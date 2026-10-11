package com.metroboard.ime.gestures

import org.junit.Assert.*
import org.junit.Test

class TouchGestureArbiterTest {
    @Test fun smallMovementStaysTapCandidate() {
        val arbiter = TouchGestureArbiter(20f, 20L)
        arbiter.onDown(4, 100f, 100f, 0L)
        assertFalse(arbiter.shouldStartSwipe(4, 108f, 108f, 40L, 1))
        assertEquals(TouchGestureMode.TAP_CANDIDATE, arbiter.mode)
    }

    @Test fun onePointerCanEnterSwipeAfterThreshold() {
        val arbiter = TouchGestureArbiter(20f, 20L)
        arbiter.onDown(7, 10f, 10f, 0L)
        assertTrue(arbiter.shouldStartSwipe(7, 40f, 10f, 30L, 1))
        assertTrue(arbiter.isSwipeActive)
    }

    @Test fun secondPointerPermanentlyCancelsSwipeForCurrentGesture() {
        val arbiter = TouchGestureArbiter(20f, 20L)
        arbiter.onDown(1, 10f, 10f, 0L)
        arbiter.onAdditionalPointerDown(2)
        assertTrue(arbiter.isMultitouch)
        assertFalse(arbiter.shouldStartSwipe(1, 80f, 10f, 100L, 1))
        assertTrue(arbiter.isMultitouch)
    }

    @Test fun wrongPointerCannotStartSwipe() {
        val arbiter = TouchGestureArbiter(20f, 20L)
        arbiter.onDown(3, 0f, 0f, 0L)
        assertFalse(arbiter.shouldStartSwipe(9, 50f, 0f, 50L, 1))
    }

    @Test fun nextGestureCanSwipeAfterMultitouchGestureEnds() {
        val arbiter = TouchGestureArbiter(20f, 20L)
        arbiter.onDown(1, 0f, 0f, 0L)
        arbiter.onAdditionalPointerDown(2)
        arbiter.endGesture()
        arbiter.onDown(5, 0f, 0f, 100L)
        assertTrue(arbiter.shouldStartSwipe(5, 30f, 0f, 140L, 1))
    }
}
