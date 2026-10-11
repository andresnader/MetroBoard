package com.metroboard.ime.gestures

import kotlin.math.hypot

enum class TouchGestureMode {
    IDLE,
    TAP_CANDIDATE,
    SWIPE_ACTIVE,
    MULTITOUCH_ACTIVE,
}

/**
 * Pure state machine that arbitrates tap typing vs. shape writing.
 *
 * Once a second pointer participates in a gesture, shape writing stays disabled
 * until every pointer is lifted. This deliberately favors accurate two-thumb
 * typing over trying to salvage an ambiguous swipe.
 */
class TouchGestureArbiter(
    private val swipeStartDistancePx: Float,
    private val minSwipeDurationMs: Long = 28L,
) {
    var mode: TouchGestureMode = TouchGestureMode.IDLE
        private set

    var primaryPointerId: Int = INVALID_POINTER_ID
        private set

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    fun onDown(pointerId: Int, x: Float, y: Float, eventTime: Long) {
        primaryPointerId = pointerId
        downX = x
        downY = y
        downTime = eventTime
        mode = TouchGestureMode.TAP_CANDIDATE
    }

    fun onAdditionalPointerDown(pointerCount: Int) {
        if (pointerCount >= 2 && mode != TouchGestureMode.IDLE) {
            mode = TouchGestureMode.MULTITOUCH_ACTIVE
        }
    }

    fun shouldStartSwipe(
        pointerId: Int,
        x: Float,
        y: Float,
        eventTime: Long,
        pointerCount: Int,
    ): Boolean {
        if (pointerCount != 1) {
            onAdditionalPointerDown(pointerCount)
            return false
        }
        if (mode == TouchGestureMode.MULTITOUCH_ACTIVE || mode == TouchGestureMode.IDLE) return false
        if (pointerId != primaryPointerId) return false
        if (mode == TouchGestureMode.SWIPE_ACTIVE) return true

        val elapsed = eventTime - downTime
        val distance = hypot(x - downX, y - downY)
        if (elapsed >= minSwipeDurationMs && distance >= swipeStartDistancePx) {
            mode = TouchGestureMode.SWIPE_ACTIVE
            return true
        }
        return false
    }

    fun endGesture() {
        mode = TouchGestureMode.IDLE
        primaryPointerId = INVALID_POINTER_ID
    }

    val isSwipeActive: Boolean get() = mode == TouchGestureMode.SWIPE_ACTIVE
    val isMultitouch: Boolean get() = mode == TouchGestureMode.MULTITOUCH_ACTIVE

    companion object {
        const val INVALID_POINTER_ID = -1
    }
}
