/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroboard.ime.ime.EditorPolicy
import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.KeySpec
import com.metroboard.ime.keyboard.KeyboardPage
import com.metroboard.ime.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real [KeyboardView] (hit testing, per-pointer dispatch, state machine) with
 * synthetic multi-pointer MotionEvents, the same way Android would deliver two thumbs.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardViewTouchTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val typed = mutableListOf<String>()
    private var wordFlowCommits = 0
    private lateinit var view: KeyboardView
    private var downTime = 0L
    private var clock = 0L

    private class Pointer(val id: Int, var x: Float, var y: Float)
    private val pointers = mutableListOf<Pointer>()

    private val listener = object : KeyboardView.Listener {
        override fun onKey(key: KeySpec) { if (key.action == KeyAction.TEXT) typed += key.label }
        override fun onSuggestion(word: String) {}
        override fun onSwitchKeyboard() {}
        override fun onAlternativesOpened() {}
        override fun onWordFlow(word: String, candidates: List<String>) { wordFlowCommits++ }
        override fun onLanguageToggled() {}
        override fun onSpacebarCursorMoved(step: Int) {}
        override fun onSpacebarLongPressed() {}
        override fun onDoubleTapWordDetected() {}
        override fun onClipboardPasteRequested() {}
    }

    private lateinit var host: Activity

    @Before
    fun setUp() {
        typed.clear(); wordFlowCommits = 0; pointers.clear()
        // The view must be attached to a real window: KeyboardView ignores buttons that are not shown.
        host = inst.startActivitySync(
            Intent(inst.targetContext, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        inst.runOnMainSync {
            view = KeyboardView(host, listener)
            view.setLayout(
                page = KeyboardPage.LETTERS,
                policy = EditorPolicy.from(EditorInfo(), false),
                language = "es",
                wordFlow = true,
            )
            host.addContentView(
                view,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        inst.waitForIdleSync()
        downTime = SystemClock.uptimeMillis()
        clock = downTime
    }

    @After
    fun tearDown() {
        inst.runOnMainSync { host.finish() }
    }

    // ---- helpers -------------------------------------------------------

    private fun keyCenter(label: String): Pair<Float, Float> {
        fun find(v: View): KeyButton? {
            if (v is KeyButton && v.text?.toString().equals(label, ignoreCase = true)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        val key = requireNotNull(find(view)) { "key '$label' not found in layout" }
        var x = key.width / 2f
        var y = key.height / 2f
        var cur: View = key
        while (cur !== view) {
            x += cur.left; y += cur.top
            cur = cur.parent as View
        }
        return x to y
    }

    private fun send(action: Int, actionIndex: Int = 0) {
        val props = pointers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = pointers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 1f } }
        val masked = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val ev = MotionEvent.obtain(
            downTime, clock, masked, pointers.size,
            props.toTypedArray(), coords.toTypedArray(),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        inst.runOnMainSync { view.dispatchTouchEvent(ev) }
        ev.recycle()
    }

    private fun down(id: Int, key: String, dt: Long = 0) {
        clock += dt
        val (x, y) = keyCenter(key)
        pointers += Pointer(id, x, y)
        if (pointers.size == 1) send(MotionEvent.ACTION_DOWN)
        else send(MotionEvent.ACTION_POINTER_DOWN, pointers.indexOfFirst { it.id == id })
    }

    private fun moveTo(id: Int, key: String, dt: Long = 0) {
        clock += dt
        val (x, y) = keyCenter(key)
        pointers.first { it.id == id }.apply { this.x = x; this.y = y }
        send(MotionEvent.ACTION_MOVE)
    }

    private fun up(id: Int, dt: Long = 0) {
        clock += dt
        val idx = pointers.indexOfFirst { it.id == id }
        if (pointers.size == 1) send(MotionEvent.ACTION_UP) else send(MotionEvent.ACTION_POINTER_UP, idx)
        pointers.removeAt(idx)
    }

    // ---- scenarios -----------------------------------------------------

    @Test
    fun rapidSingleFingerTapsAreTypedInOrder() {
        for (k in listOf("h", "o", "l", "a")) { down(0, k); up(0, 40); clock += 60 }
        assertEquals(listOf("h", "o", "l", "a"), typed)
        assertEquals(0, wordFlowCommits)
    }

    @Test
    fun alternatingTwoThumbsWithOverlapNeverTriggersWordFlow() {
        // left thumb: a s d f, right thumb: l k j h, overlapping presses
        val left = listOf("a", "s", "d", "f")
        val right = listOf("l", "k", "j", "h")
        for (i in left.indices) {
            down(0, left[i])
            down(1, right[i], 30)
            up(0, 40)
            up(1, 30)
            clock += 60
        }
        assertEquals(left.indices.flatMap { listOf(left[it], right[it]) }, typed)
        assertEquals("two-thumb typing must never commit a shape-written word", 0, wordFlowCommits)
    }

    @Test
    fun nearSimultaneousPressesKeepPressOrderEvenWhenReleasedInReverse() {
        down(0, "a")
        down(1, "l", 25)
        up(1, 35) // l released first
        up(0, 15)
        assertEquals(listOf("a", "l"), typed)
    }

    @Test
    fun secondThumbWhileTheFirstDriftsDoesNotStartWordFlow() {
        down(0, "a")
        down(1, "l", 20)
        moveTo(0, "s", 15) // involuntary drift of the first thumb
        moveTo(0, "d", 15)
        up(1, 20)
        up(0, 20)
        assertEquals(0, wordFlowCommits)
        assertTrue("second thumb's key must be typed", typed.contains("l"))
    }

    @Test
    fun cancelDuringATwoFingerGestureLeavesTheViewUsable() {
        down(0, "a")
        down(1, "l", 20)
        send(MotionEvent.ACTION_CANCEL)
        pointers.clear()
        clock += 100
        down(0, "h"); up(0, 40)
        assertTrue(typed.contains("h"))
        assertEquals(0, wordFlowCommits)
    }

    @Test
    fun aClearSingleFingerGestureStillShapeWrites() {
        down(0, "h")
        for (k in listOf("j", "k", "l", "o", "i", "u")) moveTo(0, k, 25)
        up(0, 25)
        assertEquals("a deliberate swipe over several keys must commit once", 1, wordFlowCommits)
        assertTrue("a swipe must not also type letters", typed.isEmpty())
    }
}
