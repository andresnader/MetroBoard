/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.keyboard.KeyAction
import com.metroboard.ime.keyboard.KeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Synthetic MotionEvent-like sequences (pointerId, positions, timestamps) fed to
 * [TouchInputStateMachine]. They model the P0 input-quality scenarios: two-thumb
 * typing, near-simultaneous presses, drift while tapping, cancellation, and
 * accidental shape-writing activation.
 */
class TouchInputScenarioTest {
    private val taps = mutableListOf<String>()
    private var swipeStarts = 0
    private var swipeEnds = 0
    private var swipeCancels = 0

    private val listener = object : TouchInputListener {
        override fun onKeyTapped(key: KeySpec) { taps += key.label }
        override fun onKeyLongPressed(key: KeySpec, anchorX: Float, anchorY: Float) {}
        override fun onSwipeStarted(startPoint: GesturePoint) { swipeStarts++ }
        override fun onSwipeUpdated(point: GesturePoint) {}
        override fun onSwipeEnded(path: List<GesturePoint>) { swipeEnds++ }
        override fun onSwipeCancelled() { swipeCancels++ }
        override fun onSpacebarCursorMoved(step: Int) {}
        override fun onSpacebarLongPressed() {}
    }

    // Same units as the production view would use after dp->px conversion (~3 px/dp):
    // key ~ 100 px wide, slop 36 px, swipe start 72 px.
    private lateinit var sm: TouchInputStateMachine

    private fun key(c: String) = KeySpec(c, KeyAction.TEXT)
    private val a = key("a")
    private val s = key("s")
    private val d = key("d")
    private val f = key("f")
    private val l = key("l")
    private val k = key("k")

    @Before
    fun setup() {
        sm = TouchInputStateMachine(
            listener = listener,
            swipeStartDistance = 72f,
            swipeStartTimeMinMs = 45L,
            touchSlop = 36f,
            wordFlowEnabled = true,
        )
    }

    // ---- single finger -------------------------------------------------

    @Test
    fun rapidSingleFingerTapsAreAllEmittedInOrderAndNeverSwipe() {
        val keys = listOf(a, s, d, f, a, s, d, f, a, s)
        var t = 1000L
        keys.forEach { key ->
            sm.onDown(0, 100f, 100f, t, key)
            sm.onUp(0, 103f, 101f, t + 40, key)
            t += 90
        }
        assertEquals(keys.map { it.label }, taps)
        assertEquals(0, swipeStarts)
        assertEquals(TouchInteractionState.IDLE, sm.state)
    }

    @Test
    fun tapWithDriftBetweenSlopAndSwipeDistanceStillTypesTheKeyPressed() {
        // Thumb rolls ~55 px (between 1.5*slop=54 and swipeStartDistance=72) before lifting.
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onMove(0, 130f, 102f, 1015L, a)
        sm.onMove(0, 155f, 104f, 1030L, a)
        sm.onUp(0, 156f, 104f, 1040L, a)
        assertEquals("a tap that is not a swipe must not be silently dropped", listOf("a"), taps)
        assertEquals(0, swipeStarts)
    }

    @Test
    fun shortFastDriftAcrossTwoKeysIsATapNotShapeWriting() {
        // 80 px in 50 ms, but the finger only ever touched 'a' and its neighbour 's'.
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onMove(0, 140f, 100f, 1025L, a)
        sm.onMove(0, 180f, 100f, 1050L, s)
        sm.onUp(0, 180f, 100f, 1055L, s)
        assertEquals("one-key neighbour drift is not intent to shape-write", 0, swipeStarts)
        assertEquals(listOf("a"), taps)
    }

    @Test
    fun clearGestureAcrossSeveralKeysStillActivatesShapeWriting() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onMove(0, 160f, 100f, 1030L, s)
        sm.onMove(0, 230f, 100f, 1060L, d)
        sm.onMove(0, 300f, 100f, 1090L, f)
        sm.onMove(0, 370f, 100f, 1120L, k)
        sm.onUp(0, 380f, 100f, 1140L, k)
        assertEquals(1, swipeStarts)
        assertEquals(1, swipeEnds)
        assertTrue(taps.isEmpty())
    }

    @Test
    fun fastDoubleLettersAreTwoPlainTaps() {
        // "ww", "ll", "ss": the same key twice within the old 300 ms "double tap" window.
        for (k in listOf(a, l, s)) {
            sm.onDown(0, 100f, 100f, 1000L, k); sm.onUp(0, 100f, 100f, 1040L, k)
            sm.onDown(0, 100f, 100f, 1100L, k); sm.onUp(0, 100f, 100f, 1140L, k)
        }
        assertEquals(listOf("a", "a", "l", "l", "s", "s"), taps)
        assertEquals(0, swipeStarts)
    }

    // ---- settings that used to do nothing -----------------------------------

    @Test
    fun withTheSpacebarCursorDisabledSlidingOnSpaceDoesNotMoveTheCursor() {
        var moved = 0
        val listener2 = object : TouchInputListener by listener { override fun onSpacebarCursorMoved(step: Int) { moved += step } }
        val machine = TouchInputStateMachine(listener2, swipeStartDistance = 72f, touchSlop = 36f).apply { spaceCursorEnabled = false }
        val space = KeySpec("space", KeyAction.SPACE)
        machine.onDown(0, 300f, 100f, 1000L, space)
        machine.onMove(0, 360f, 100f, 1040L, space)
        machine.onMove(0, 420f, 100f, 1080L, space)
        machine.onUp(0, 420f, 100f, 1100L, space)
        assertEquals(0, moved)
    }

    @Test
    fun withSpaceLongPressDisabledHoldingSpaceStillTypesASpace() {
        var picker = 0
        val listener2 = object : TouchInputListener by listener { override fun onSpacebarLongPressed() { picker++ } }
        val machine = TouchInputStateMachine(listener2, swipeStartDistance = 72f, touchSlop = 36f).apply { spaceLongPressEnabled = false }
        val space = KeySpec("space", KeyAction.SPACE)
        machine.onDown(0, 300f, 100f, 1000L, space)
        machine.onLongPressTimerExpired(0)
        machine.onUp(0, 300f, 100f, 1500L, space)
        assertEquals(0, picker)
        assertEquals(listOf("space"), taps)
    }

    @Test
    fun withKeyPopupsDisabledHoldingALetterJustTypesIt() {
        var popups = 0
        val listener2 = object : TouchInputListener by listener { override fun onKeyLongPressed(key: KeySpec, anchorX: Float, anchorY: Float) { popups++ } }
        val machine = TouchInputStateMachine(listener2, swipeStartDistance = 72f, touchSlop = 36f).apply { keyPopupsEnabled = false }
        val withAlternatives = KeySpec("a", KeyAction.TEXT, alternatives = listOf("á", "à"))
        machine.onDown(0, 100f, 100f, 1000L, withAlternatives)
        machine.onLongPressTimerExpired(0)
        machine.onUp(0, 100f, 100f, 1500L, withAlternatives)
        assertEquals(0, popups)
        assertEquals(listOf("a"), taps)
    }

    @Test
    fun theDefaultsKeepAllThreeFeaturesOn() {
        val machine = TouchInputStateMachine(listener)
        assertTrue(machine.spaceCursorEnabled && machine.spaceLongPressEnabled && machine.keyPopupsEnabled)
    }

    // ---- two thumbs ----------------------------------------------------

    @Test
    fun alternatingThumbsWithOverlapNeverStartShapeWritingAndKeepPressOrder() {
        val seq = listOf(a to l, s to k, d to l, f to k)
        var t = 1000L
        val expected = mutableListOf<String>()
        seq.forEach { (left, right) ->
            sm.onDown(0, 60f, 100f, t, left)
            sm.onPointerDown(1, 700f, 100f, t + 30, right)
            sm.onMove(0, 70f, 104f, t + 50, left)
            sm.onMove(1, 690f, 96f, t + 55, right)
            sm.onPointerUp(0, 70f, 104f, t + 70, left)
            sm.onUp(1, 690f, 96f, t + 90, right)
            expected += listOf(left.label, right.label)
            t += 140
        }
        assertEquals(expected, taps)
        assertEquals(0, swipeStarts)
        assertEquals(0, swipeEnds)
        assertEquals(TouchInteractionState.IDLE, sm.state)
    }

    @Test
    fun nearSimultaneousPressesKeepPressOrderEvenWhenReleasedInReverse() {
        // A pressed first, B pressed 25 ms later, B released BEFORE A.
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onPointerDown(1, 600f, 100f, 1025L, l)
        sm.onPointerUp(1, 600f, 100f, 1060L, l)
        sm.onUp(0, 100f, 100f, 1075L, a)
        assertEquals(listOf("a", "l"), taps)
    }

    @Test
    fun secondFingerDuringShapeWritingCancelsItAndStillTypesTheSecondKey() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onMove(0, 170f, 100f, 1030L, s)
        sm.onMove(0, 240f, 100f, 1060L, d)
        sm.onMove(0, 310f, 100f, 1090L, f)
        assertTrue(sm.isSwiping)
        sm.onPointerDown(1, 700f, 100f, 1100L, l)
        assertEquals(1, swipeCancels)
        sm.onPointerUp(1, 700f, 100f, 1140L, l)
        sm.onUp(0, 320f, 100f, 1160L, f)
        assertEquals("second thumb's key must be typed", listOf("l"), taps)
        assertEquals(0, swipeEnds)
    }

    @Test
    fun shapeWritingStaysDisabledForTheRestOfAMultitouchGesture() {
        // Ported from PR #1 TouchGestureArbiter: once a 2nd pointer participated,
        // the remaining finger cannot start shape writing until ALL pointers are up.
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onPointerDown(1, 700f, 100f, 1020L, l)
        sm.onPointerUp(1, 700f, 100f, 1050L, l)
        sm.onMove(0, 170f, 100f, 1080L, s)
        sm.onMove(0, 240f, 100f, 1110L, d)
        sm.onMove(0, 310f, 100f, 1140L, f)
        sm.onMove(0, 380f, 100f, 1170L, k)
        assertFalse(sm.isSwiping)
        sm.onUp(0, 380f, 100f, 1190L, k)
        assertEquals(0, swipeStarts)

        // A brand-new single-finger gesture afterwards may swipe again.
        sm.onDown(0, 100f, 100f, 2000L, a)
        sm.onMove(0, 160f, 100f, 2030L, s)
        sm.onMove(0, 230f, 100f, 2060L, d)
        sm.onMove(0, 300f, 100f, 2090L, f)
        sm.onMove(0, 370f, 100f, 2120L, k)
        assertTrue(sm.isSwiping)
    }

    @Test
    fun threeFingersRollingOverEmitEveryKeyOnceInPressOrder() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onPointerDown(1, 300f, 100f, 1020L, d)
        sm.onPointerDown(2, 600f, 100f, 1040L, l)
        sm.onPointerUp(2, 600f, 100f, 1070L, l)
        sm.onPointerUp(1, 300f, 100f, 1080L, d)
        sm.onUp(0, 100f, 100f, 1090L, a)
        assertEquals(listOf("a", "d", "l"), taps)
    }

    // ---- cancellation / interruption ----------------------------------

    @Test
    fun cancelAfterAReleasedButDeferredTapDoesNotLoseIt() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onPointerDown(1, 600f, 100f, 1025L, l)
        sm.onPointerUp(1, 600f, 100f, 1060L, l) // 'l' released while 'a' still down
        sm.onCancel()                            // e.g. IME interrupted
        assertEquals("a valid released tap must survive a cancel", listOf("l"), taps)
        assertEquals(TouchInteractionState.IDLE, sm.state)
    }

    @Test
    fun cancelDuringShapeWritingCancelsWithoutTypingAnything() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onMove(0, 170f, 100f, 1030L, s)
        sm.onMove(0, 240f, 100f, 1060L, d)
        sm.onMove(0, 310f, 100f, 1090L, f)
        sm.onCancel()
        assertEquals(1, swipeCancels)
        assertTrue(taps.isEmpty())
        assertEquals(TouchInteractionState.IDLE, sm.state)
    }

    @Test
    fun newDownWithStalePointersFlushesPendingTapsBeforeResetting() {
        sm.onDown(0, 100f, 100f, 1000L, a)
        sm.onPointerDown(1, 600f, 100f, 1025L, l)
        sm.onPointerUp(1, 600f, 100f, 1060L, l)
        // The UP for pointer 0 was lost; framework starts a new gesture.
        sm.onDown(0, 300f, 100f, 1500L, d)
        sm.onUp(0, 300f, 100f, 1540L, d)
        assertEquals(listOf("l", "d"), taps)
    }
}
