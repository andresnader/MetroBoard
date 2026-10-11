package com.metroboard.ime.ime

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.metroboard.ime.keyboard.EditorPort
import java.util.ArrayDeque

class InputConnectionEditor(
    private val connection: () -> InputConnection?,
) : EditorPort {
    private var info = EditorInfo()
    private var policy = EditorPolicy.from(info, false)
    private var start = -1
    private var end = -1
    private val expectedSelections = ArrayDeque<Pair<Int, Int>>()

    fun start(info: EditorInfo, policy: EditorPolicy) {
        this.info = info
        this.policy = policy
        start = info.initialSelStart
        end = info.initialSelEnd
        expectedSelections.clear()
    }

    fun finish() {
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
        return true
    }

    private fun expect(cursor: Int) {
        if (cursor < 0) return
        start = cursor
        end = cursor
        expectedSelections.addLast(cursor to cursor)
        if (expectedSelections.size > 32) expectedSelections.removeFirst()
    }

    override fun beforeCursor(maxChars: Int): String =
        connection()?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()

    override fun afterCursor(maxChars: Int): String =
        connection()?.getTextAfterCursor(maxChars, 0)?.toString().orEmpty()

    override fun hasSelection(): Boolean = start >= 0 && end >= 0 && start != end

    override fun shouldCapitalize(): Boolean {
        if (!policy.editing.automaticCapitalization) return false
        val ic = connection() ?: return false
        if (ic.getCursorCapsMode(policy.capsModes) != 0) return true
        // Use the platform's contextual algorithm, not a last-character-only rule.
        val context = ic.getTextBeforeCursor(256, 0) ?: return start == 0
        if (context.isEmpty()) return start == 0
        return TextUtils.getCapsMode(context, context.length, policy.capsModes) != 0
    }

    override fun commit(text: String): Boolean {
        val committed = connection()?.commitText(text, 1) == true
        if (committed && start >= 0 && end >= 0) expect(minOf(start, end) + text.length)
        return committed
    }

    override fun deleteBeforeCursor(codePoints: Int): Boolean {
        val ic = connection() ?: return false
        // Bounded temporary context preserves surrogate pairs in legacy fallback.
        val previous = ic.getTextBeforeCursor(codePoints * 2, 0)?.toString().orEmpty()
        val count = minOf(codePoints, previous.codePointCount(0, previous.length))
        val units = previous.length - previous.offsetByCodePoints(previous.length, -count)
        val deleted = if (ic.deleteSurroundingTextInCodePoints(codePoints, 0)) true
            else if (units > 0) ic.deleteSurroundingText(units, 0)
            else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)).also {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        if (deleted && start >= 0) expect((start - units).coerceAtLeast(0))
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
}
