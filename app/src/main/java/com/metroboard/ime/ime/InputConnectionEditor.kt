/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ime

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.metroboard.ime.keyboard.EditorPort
import com.metroboard.ime.util.LatencyProbe
import java.util.ArrayDeque

class InputConnectionEditor(
    private val connection: () -> InputConnection?,
) : EditorPort {
    private var info = EditorInfo()
    private var policy = EditorPolicy.from(info, false)
    private var start = -1
    private var end = -1
    private val expectedSelections = ArrayDeque<Pair<Int, Int>>()

    // A local copy of the text around the cursor. Every read through the InputConnection is a synchronous call
    // into the app (it waits for the app's UI thread: measured at 1 to 31 ms each, two per letter typed), so the
    // copy is kept while everything that happens to the text is our own doing and dropped at the first external
    // change (a moved cursor, another field, the app rewriting text). Never used in password fields.
    private var cachedBefore: String? = null
    private var cachedBeforeIsAll = false // the copy reaches the start of the field
    private var cachedAfter: String? = null
    private var cachedAfterIsAll = false // the copy reaches the end of the field

    private fun forget() {
        cachedBefore = null
        cachedBeforeIsAll = false
        cachedAfter = null
        cachedAfterIsAll = false
    }

    private fun appendToCachedBefore(text: String) {
        val known = cachedBefore ?: return
        val combined = known + text
        if (combined.length > BEFORE_KEPT) {
            cachedBefore = combined.takeLast(BEFORE_KEPT)
            cachedBeforeIsAll = false
        } else {
            cachedBefore = combined
        }
    }

    fun start(info: EditorInfo, policy: EditorPolicy) {
        this.info = info
        this.policy = policy
        start = info.initialSelStart
        end = info.initialSelEnd
        expectedSelections.clear()
        forget()
    }

    fun finish() {
        forget()
        expectedSelections.clear()
        start = -1
        end = -1
        info = EditorInfo()
        policy = EditorPolicy.from(info, false)
    }

    /** Returns true only for an external cursor/selection move, not our commit ack. */
    fun selectionChanged(newStart: Int, newEnd: Int): Boolean {
        val selection = newStart to newEnd
        if (expectedSelections.contains(selection)) {
            while (expectedSelections.isNotEmpty()) {
                if (expectedSelections.removeFirst() == selection) break
            }
            return false
        }
        if (newStart == start && newEnd == end) return false
        expectedSelections.clear()
        start = newStart
        end = newEnd
        forget() // something other than us moved the cursor: whatever we remembered may be stale
        return true
    }

    private fun expect(cursor: Int) {
        if (cursor < 0) return
        start = cursor
        end = cursor
        expectedSelections.addLast(cursor to cursor)
        if (expectedSelections.size > 32) expectedSelections.removeFirst()
    }

    override fun beforeCursor(maxChars: Int): String {
        val ic = connection() ?: return ""
        if (policy.sensitive) {
            return LatencyProbe.time("ipc.before") { ic.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty() }
        }
        cachedBefore?.let { if (it.length >= maxChars || cachedBeforeIsAll) return it.takeLast(maxChars) }
        val wanted = maxOf(maxChars, BEFORE_WINDOW)
        val fetched = LatencyProbe.time("ipc.before") { ic.getTextBeforeCursor(wanted, 0)?.toString().orEmpty() }
        cachedBefore = fetched
        cachedBeforeIsAll = fetched.length < wanted
        return fetched.takeLast(maxChars)
    }

    override fun afterCursor(maxChars: Int): String {
        val ic = connection() ?: return ""
        if (policy.sensitive) {
            return LatencyProbe.time("ipc.after") { ic.getTextAfterCursor(maxChars, 0)?.toString().orEmpty() }
        }
        cachedAfter?.let { if (it.length >= maxChars || cachedAfterIsAll) return it.take(maxChars) }
        val wanted = maxOf(maxChars, AFTER_WINDOW)
        val fetched = LatencyProbe.time("ipc.after") { ic.getTextAfterCursor(wanted, 0)?.toString().orEmpty() }
        cachedAfter = fetched
        cachedAfterIsAll = fetched.length < wanted
        return fetched.take(maxChars)
    }

    override fun hasSelection(): Boolean = start >= 0 && end >= 0 && start != end

    override fun selectedText(): String? =
        if (hasSelection()) connection()?.getSelectedText(0)?.toString() else null

    override fun replaceSelectionKeepingIt(text: String): Boolean {
        val ic = connection() ?: return false
        if (start < 0 || end < 0) return commit(text)
        forget()
        val from = minOf(start, end)
        ic.beginBatchEdit()
        val ok = try { ic.commitText(text, 1) && ic.setSelection(from, from + text.length) } finally { ic.endBatchEdit() }
        if (ok) {
            // The platform will report the collapsed cursor and then the restored range: both are our own.
            expectedSelections.addLast((from + text.length) to (from + text.length))
            expectedSelections.addLast(from to (from + text.length))
            start = from
            end = from + text.length
        }
        return ok
    }

    override fun shouldCapitalize(): Boolean {
        if (!policy.editing.automaticCapitalization) return false
        val ic = connection() ?: return false
        if (LatencyProbe.time("ipc.capsMode") { ic.getCursorCapsMode(policy.capsModes) } != 0) return true
        // Use the platform's contextual algorithm, not a last-character-only rule.
        val context = beforeCursor(BEFORE_WINDOW)
        if (context.isEmpty()) return start == 0
        return TextUtils.getCapsMode(context, context.length, policy.capsModes) != 0
    }

    override fun commit(text: String): Boolean {
        val replacesSelection = hasSelection()
        val committed = LatencyProbe.time("ipc.commit") { connection()?.commitText(text, 1) } == true
        if (committed && start >= 0 && end >= 0) expect(minOf(start, end) + text.length)
        if (committed) {
            // The text after the cursor is untouched by typing at it; the text before simply grows.
            if (replacesSelection) forget() else appendToCachedBefore(text)
        }
        return committed
    }

    override fun deleteBeforeCursor(codePoints: Int): Boolean {
        val ic = connection() ?: return false
        val removesSelection = hasSelection()
        // Bounded temporary context preserves surrogate pairs in legacy fallback.
        val previous = beforeCursor(codePoints * 2)
        val count = minOf(codePoints, previous.codePointCount(0, previous.length))
        val units = previous.length - previous.offsetByCodePoints(previous.length, -count)
        var byKeyEvent = false
        val deleted = if (ic.deleteSurroundingTextInCodePoints(codePoints, 0)) true
            else if (units > 0) ic.deleteSurroundingText(units, 0)
            else {
                byKeyEvent = true
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)).also {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }
            }
        if (deleted && start >= 0) expect((start - units).coerceAtLeast(0))
        if (deleted) {
            // What a key event deletes (or a deleted selection) is not known here, so the copy is dropped.
            if (removesSelection || byKeyEvent) forget() else cachedBefore = cachedBefore?.dropLast(units)
        }
        return deleted
    }

    override fun enter(): Boolean {
        val ic = connection() ?: return false
        val allowAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (allowAction) {
            if (info.actionId != 0 && info.actionLabel != null && ic.performEditorAction(info.actionId)) return true
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                ic.performEditorAction(action)) return true
        }
        return commit("\n")
    }

    override fun beginBatch() { connection()?.beginBatchEdit() }
    override fun endBatch() { connection()?.endBatchEdit() }

    private companion object {
        /** How much text before the cursor is fetched on a miss: enough for every caller (the largest asks for 256). */
        const val BEFORE_WINDOW = 256
        /** The local copy of the text before the cursor never grows past this while the user types. */
        const val BEFORE_KEPT = 512
        const val AFTER_WINDOW = 16
    }
}
