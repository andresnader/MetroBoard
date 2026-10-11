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
import kotlin.math.abs
import kotlin.math.hypot

enum class TouchInteractionState {
    IDLE,
    TAP_CANDIDATE,
    LONG_PRESS_CANDIDATE,
    LONG_PRESS_ACTIVE,
    SWIPE_CANDIDATE,
    SWIPE_ACTIVE,
    MULTITOUCH_ACTIVE,
    SPACEBAR_CURSOR_ACTIVE,
    BACKSPACE_SWIPE_DELETE_ACTIVE
}

interface TouchInputListener {
    fun onKeyTapped(key: KeySpec)
    fun onKeyLongPressed(key: KeySpec, anchorX: Float, anchorY: Float)
    fun onSwipeStarted(startPoint: GesturePoint)
    fun onSwipeUpdated(point: GesturePoint)
    fun onSwipeEnded(path: List<GesturePoint>)
    fun onSwipeCancelled()
    fun onSpacebarCursorMoved(step: Int)
    fun onSpacebarLongPressed()
    fun onDoubleTapWordDetected()
    fun onSwipeToDelete(wordsCount: Int) = Unit
}

data class PointerRecord(
    val pointerId: Int,
    val startX: Float,
    val startY: Float,
    val startTime: Long,
    var currentX: Float,
    var currentY: Float,
    var currentTime: Long,
    val initialKey: KeySpec?,
    var currentKey: KeySpec? = initialKey,
    var distanceTraveled: Float = 0f,
    var hasTriggeredTap: Boolean = false,
    var hasTriggeredLongPress: Boolean = false,
    val path: MutableList<GesturePoint> = mutableListOf()
)

class TouchInputStateMachine(
    private val listener: TouchInputListener,
    var swipeStartDistance: Float = 48f,       // in pixels
    var swipeStartTimeMinMs: Long = 40L,
    var longPressTimeoutMs: Long = 350L,
    var doubleTapTimeoutMs: Long = 300L,
    var spaceCursorThreshold: Float = 32f,     // in pixels per cursor move step
    var touchSlop: Float = 24f,                // in pixels
    var wordFlowEnabled: Boolean = true
) {
    var state: TouchInteractionState = TouchInteractionState.IDLE
        private set

    private val activePointers = mutableMapOf<Int, PointerRecord>()
    private var primaryPointerId: Int = -1
    private var lastCursorStepX: Float = 0f
    private var lastTapTime: Long = 0L
    private var lastTapKey: KeySpec? = null

    val isSwiping: Boolean
        get() = state == TouchInteractionState.SWIPE_ACTIVE

    val activePath: List<GesturePoint>
        get() = activePointers[primaryPointerId]?.path ?: emptyList()

    fun onDown(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        activePointers.clear()
        primaryPointerId = pointerId
        val record = PointerRecord(
            pointerId = pointerId,
            startX = x,
            startY = y,
            startTime = time,
            currentX = x,
            currentY = y,
            currentTime = time,
            initialKey = key,
            currentKey = key
        ).apply {
            path.add(GesturePoint(x, y, time))
        }
        activePointers[pointerId] = record

        // Check if double tap on same key or area
        if (lastTapKey != null && lastTapKey == key && (time - lastTapTime) <= doubleTapTimeoutMs) {
            // Potential double tap
            if (key?.action == KeyAction.TEXT) {
                listener.onDoubleTapWordDetected()
            }
        }

        if (key?.action == KeyAction.SPACE) {
            state = TouchInteractionState.TAP_CANDIDATE
            lastCursorStepX = x
        } else {
            state = TouchInteractionState.TAP_CANDIDATE
        }
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        val record = PointerRecord(
            pointerId = pointerId,
            startX = x,
            startY = y,
            startTime = time,
            currentX = x,
            currentY = y,
            currentTime = time,
            initialKey = key,
            currentKey = key
        ).apply {
            path.add(GesturePoint(x, y, time))
        }
        activePointers[pointerId] = record

        // CRITICAL MULTI-TOUCH RULE:
        // As soon as two or more pointers are active simultaneously, CANCEL any swipe candidate or active swipe!
        // Prioritize TAP TYPING for both thumbs.
        if (state == TouchInteractionState.SWIPE_ACTIVE) {
            listener.onSwipeCancelled()
        }
        state = TouchInteractionState.MULTITOUCH_ACTIVE
    }

    fun onMove(pointerId: Int, x: Float, y: Float, time: Long, currentKey: KeySpec?) {
        val record = activePointers[pointerId] ?: return
        val prevX = record.currentX
        val prevY = record.currentY
        record.currentX = x
        record.currentY = y
        record.currentTime = time
        record.currentKey = currentKey
        val stepDist = hypot((x - prevX).toDouble(), (y - prevY).toDouble()).toFloat()
        record.distanceTraveled += stepDist
        record.path.add(GesturePoint(x, y, time))

        val totalDistFromStart = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()

        when (state) {
            TouchInteractionState.TAP_CANDIDATE, TouchInteractionState.LONG_PRESS_CANDIDATE -> {
                // Check if starting spacebar cursor movement
                if (record.initialKey?.action == KeyAction.SPACE) {
                    val dx = x - record.startX
                    if (abs(dx) >= spaceCursorThreshold) {
                        state = TouchInteractionState.SPACEBAR_CURSOR_ACTIVE
                        lastCursorStepX = x
                        val step = if (dx > 0) 1 else -1
                        listener.onSpacebarCursorMoved(step)
                    }
                    return
                }

                // Check if starting backspace swipe delete
                if (record.initialKey?.action == KeyAction.DELETE) {
                    val dx = record.startX - x
                    if (dx >= swipeStartDistance) {
                        state = TouchInteractionState.BACKSPACE_SWIPE_DELETE_ACTIVE
                    }
                    return
                }

                // Check if eligible for swipe typing:
                // 1. Only 1 pointer active
                // 2. Word Flow enabled
                // 3. Not started on non-alpha (space, delete, enter, etc.)
                // 4. Dist exceeds swipe start distance
                val isAlphaKey = record.initialKey?.action == KeyAction.TEXT &&
                    record.initialKey.label.isNotEmpty() &&
                    record.initialKey.label.first().isLetter()

                if (activePointers.size == 1 && wordFlowEnabled && isAlphaKey) {
                    if (totalDistFromStart > touchSlop) {
                        state = TouchInteractionState.SWIPE_CANDIDATE
                    }
                }
            }

            TouchInteractionState.SWIPE_CANDIDATE -> {
                if (activePointers.size == 1 && totalDistFromStart >= swipeStartDistance &&
                    (time - record.startTime) >= swipeStartTimeMinMs) {
                    state = TouchInteractionState.SWIPE_ACTIVE
                    listener.onSwipeStarted(record.path.first())
                    for (pt in record.path) {
                        listener.onSwipeUpdated(pt)
                    }
                }
            }

            TouchInteractionState.SWIPE_ACTIVE -> {
                if (pointerId == primaryPointerId) {
                    listener.onSwipeUpdated(GesturePoint(x, y, time))
                }
            }

            TouchInteractionState.SPACEBAR_CURSOR_ACTIVE -> {
                if (record.initialKey?.action == KeyAction.SPACE) {
                    val deltaX = x - lastCursorStepX
                    if (abs(deltaX) >= spaceCursorThreshold) {
                        val steps = (deltaX / spaceCursorThreshold).toInt()
                        if (steps != 0) {
                            listener.onSpacebarCursorMoved(steps)
                            lastCursorStepX += steps * spaceCursorThreshold
                        }
                    }
                }
            }

            TouchInteractionState.MULTITOUCH_ACTIVE -> {
                // In multitouch, each finger does tap typing; movements within touchSlop are tolerated.
            }

            TouchInteractionState.LONG_PRESS_ACTIVE -> {
                // Already in long press popup; moving finger can select alternative.
            }

            TouchInteractionState.BACKSPACE_SWIPE_DELETE_ACTIVE -> Unit

            TouchInteractionState.IDLE -> Unit
        }
    }

    fun onLongPressTimerExpired(pointerId: Int = primaryPointerId) {
        val pid = if (activePointers.containsKey(pointerId)) pointerId else primaryPointerId
        val record = activePointers[pid] ?: return
        if (state == TouchInteractionState.SWIPE_ACTIVE ||
            state == TouchInteractionState.SPACEBAR_CURSOR_ACTIVE ||
            state == TouchInteractionState.BACKSPACE_SWIPE_DELETE_ACTIVE ||
            state == TouchInteractionState.MULTITOUCH_ACTIVE) {
            return
        }

        val dist = hypot((record.currentX - record.startX).toDouble(), (record.currentY - record.startY).toDouble()).toFloat()
        if (dist <= touchSlop) {
            record.hasTriggeredLongPress = true
            if (record.initialKey?.action == KeyAction.SPACE) {
                state = TouchInteractionState.LONG_PRESS_ACTIVE
                listener.onSpacebarLongPressed()
            } else if (record.initialKey != null && record.initialKey.alternatives.isNotEmpty()) {
                state = TouchInteractionState.LONG_PRESS_ACTIVE
                listener.onKeyLongPressed(record.initialKey, record.currentX, record.currentY)
            }
        }
    }

    fun onPointerUp(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        val record = activePointers.remove(pointerId)
        if (record != null && !record.hasTriggeredLongPress) {
            val dist = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()
            if (dist <= touchSlop * 1.5f && record.initialKey != null) {
                listener.onKeyTapped(record.initialKey)
                lastTapTime = time
                lastTapKey = record.initialKey
            }
        }

        if (activePointers.size <= 1) {
            primaryPointerId = activePointers.keys.firstOrNull() ?: -1
            if (activePointers.isEmpty()) {
                state = TouchInteractionState.IDLE
            }
        }
    }

    fun onUp(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        val record = activePointers.remove(pointerId)

        when (state) {
            TouchInteractionState.SWIPE_ACTIVE -> {
                if (record != null && record.path.size >= 3) {
                    listener.onSwipeEnded(record.path)
                } else {
                    listener.onSwipeCancelled()
                }
            }

            TouchInteractionState.SPACEBAR_CURSOR_ACTIVE -> {
                // Ended space cursor gesture; DO NOT emit space tap.
            }

            TouchInteractionState.BACKSPACE_SWIPE_DELETE_ACTIVE -> {
                if (record != null) {
                    val deltaX = record.startX - x
                    if (deltaX >= swipeStartDistance) {
                        val words = (deltaX / (swipeStartDistance * 1.5f)).toInt().coerceIn(1, 10)
                        listener.onSwipeToDelete(words)
                    }
                }
            }

            TouchInteractionState.LONG_PRESS_ACTIVE -> {
                // Handled by popup dismissal/selection
            }

            TouchInteractionState.MULTITOUCH_ACTIVE -> {
                if (record != null && !record.hasTriggeredLongPress) {
                    val dist = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()
                    if (dist <= touchSlop * 1.5f && record.initialKey != null) {
                        listener.onKeyTapped(record.initialKey)
                        lastTapTime = time
                        lastTapKey = record.initialKey
                    }
                }
            }

            TouchInteractionState.TAP_CANDIDATE, TouchInteractionState.SWIPE_CANDIDATE, TouchInteractionState.LONG_PRESS_CANDIDATE -> {
                if (record != null && !record.hasTriggeredLongPress) {
                    val dist = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()
                    if (dist <= touchSlop * 1.5f && record.initialKey != null) {
                        listener.onKeyTapped(record.initialKey)
                        lastTapTime = time
                        lastTapKey = record.initialKey
                    }
                }
            }

            TouchInteractionState.IDLE -> Unit
        }

        activePointers.clear()
        primaryPointerId = -1
        state = TouchInteractionState.IDLE
    }

    fun onCancel() {
        if (state == TouchInteractionState.SWIPE_ACTIVE) {
            listener.onSwipeCancelled()
        }
        activePointers.clear()
        primaryPointerId = -1
        state = TouchInteractionState.IDLE
    }
}
