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
    val path: MutableList<GesturePoint> = mutableListOf(),
    /** Monotonic press order across the whole gesture; used to emit taps in press order. */
    val downSeq: Long = 0L,
    /** Distinct keys this pointer has been over; evidence of shape-writing intent. */
    val visitedKeys: MutableSet<KeySpec> = LinkedHashSet()
)

class TouchInputStateMachine(
    private val listener: TouchInputListener,
    var swipeStartDistance: Float = 48f,       // in pixels
    var swipeStartTimeMinMs: Long = 40L,
    var longPressTimeoutMs: Long = 350L,
    var spaceCursorThreshold: Float = 32f,     // in pixels per cursor move step
    var touchSlop: Float = 24f,                // in pixels
    var wordFlowEnabled: Boolean = true,
    /** Settings > Gestures: slide on the spacebar moves the cursor. */
    var spaceCursorEnabled: Boolean = true,
    /** Settings > Gestures: holding the spacebar opens the language / keyboard picker. */
    var spaceLongPressEnabled: Boolean = true,
    /** Settings > Layout: holding a letter shows its alternatives. */
    var keyPopupsEnabled: Boolean = true,
    /** Distinct letter keys a pointer must cross to be treated as shape writing... */
    var swipeMinDistinctKeys: Int = 3,
    /** ...unless it crossed at least 2 and travelled this many swipe-start distances. */
    var swipeLongPathFactor: Float = 2f,
    /** Max extra drift (in swipe-start distances) for a non-swipe press to still count as a tap. */
    var tapMaxDriftFactor: Float = 2f,
    /** A released tap waits at most this long for a key pressed earlier and still held. */
    var tapOrderWindowMs: Long = 250L
) {
    var state: TouchInteractionState = TouchInteractionState.IDLE
        private set

    private val activePointers = mutableMapOf<Int, PointerRecord>()
    private var primaryPointerId: Int = -1
    private var lastCursorStepX: Float = 0f
    private var nextDownSeq = 0L

    /** Taps already released but waiting for an earlier-pressed key, to keep press order. */
    private val pendingTaps = ArrayList<PointerRecord>()

    val isSwiping: Boolean
        get() = state == TouchInteractionState.SWIPE_ACTIVE

    val activePath: List<GesturePoint>
        get() = activePointers[primaryPointerId]?.path ?: emptyList()

    fun onDown(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        // A fresh gesture means the previous one ended (possibly with a lost UP):
        // never drop taps that were already released.
        activePointers.clear()
        flushPending(time, force = true)
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
            currentKey = key,
            downSeq = nextDownSeq++
        ).apply {
            path.add(GesturePoint(x, y, time))
            key?.let { visitedKeys.add(it) }
        }
        activePointers[pointerId] = record

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
            currentKey = key,
            downSeq = nextDownSeq++
        ).apply {
            path.add(GesturePoint(x, y, time))
            key?.let { visitedKeys.add(it) }
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
        currentKey?.let { record.visitedKeys.add(it) }
        val stepDist = hypot((x - prevX).toDouble(), (y - prevY).toDouble()).toFloat()
        record.distanceTraveled += stepDist
        record.path.add(GesturePoint(x, y, time))

        val totalDistFromStart = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()

        when (state) {
            TouchInteractionState.TAP_CANDIDATE, TouchInteractionState.LONG_PRESS_CANDIDATE -> {
                // Check if starting spacebar cursor movement
                if (record.initialKey?.action == KeyAction.SPACE) {
                    val dx = x - record.startX
                    if (spaceCursorEnabled && abs(dx) >= spaceCursorThreshold) {
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
                    (time - record.startTime) >= swipeStartTimeMinMs &&
                    hasShapeWritingEvidence(record)) {
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
            val key = record.initialKey
            val isSpace = key?.action == KeyAction.SPACE
            // A disabled long press must leave the press intact so releasing still types the key.
            val handled = when {
                isSpace -> spaceLongPressEnabled
                key != null && key.alternatives.isNotEmpty() -> keyPopupsEnabled
                else -> false
            }
            if (!handled) return
            record.hasTriggeredLongPress = true
            flushPending(record.currentTime)
            state = TouchInteractionState.LONG_PRESS_ACTIVE
            if (isSpace) listener.onSpacebarLongPressed()
            else listener.onKeyLongPressed(key!!, record.currentX, record.currentY)
        }
    }

    fun onPointerUp(pointerId: Int, x: Float, y: Float, time: Long, key: KeySpec?) {
        val record = activePointers.remove(pointerId)
        if (record != null) {
            record.currentX = x
            record.currentY = y
            record.currentTime = time
            if (isTap(record, x, y)) completeTap(record, time)
        }

        if (activePointers.size <= 1) {
            primaryPointerId = activePointers.keys.firstOrNull() ?: -1
            if (activePointers.isEmpty()) {
                state = TouchInteractionState.IDLE
            }
        }
        flushPending(time)
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

            TouchInteractionState.MULTITOUCH_ACTIVE,
            TouchInteractionState.TAP_CANDIDATE,
            TouchInteractionState.SWIPE_CANDIDATE,
            TouchInteractionState.LONG_PRESS_CANDIDATE -> {
                if (record != null) {
                    record.currentX = x
                    record.currentY = y
                    record.currentTime = time
                    if (isTap(record, x, y)) completeTap(record, time)
                }
            }

            TouchInteractionState.IDLE -> Unit
        }

        activePointers.clear()
        primaryPointerId = -1
        state = TouchInteractionState.IDLE
        flushPending(time, force = true)
    }

    fun onCancel() {
        if (state == TouchInteractionState.SWIPE_ACTIVE) {
            listener.onSwipeCancelled()
        }
        activePointers.clear()
        primaryPointerId = -1
        state = TouchInteractionState.IDLE
        // Taps released before the interruption are valid keystrokes.
        flushPending(lastEventTime(), force = true)
    }

    /**
     * Emits deferred taps whose ordering constraint has expired. The view calls this
     * from a short timer so a long-held key can never hold back later keystrokes.
     */
    fun flushPending(time: Long, force: Boolean = false) {
        if (pendingTaps.isEmpty()) return
        pendingTaps.sortBy { it.downSeq }
        while (pendingTaps.isNotEmpty()) {
            val head = pendingTaps.first()
            if (!force && isBlockedByEarlierKey(head, time)) break
            pendingTaps.removeAt(0)
            emitTap(head, time)
        }
    }

    private fun lastEventTime(): Long =
        pendingTaps.maxOfOrNull { it.currentTime } ?: 0L

    /**
     * A pointer that never became a swipe, cursor move or long press is a keystroke
     * for the key it was pressed on, even if the thumb rolled a little. Only a drag
     * longer than [tapMaxDriftFactor] swipe-start distances is considered abandoned.
     */
    private fun isTap(record: PointerRecord, x: Float, y: Float): Boolean {
        if (record.hasTriggeredLongPress || record.initialKey == null) return false
        val dist = hypot((x - record.startX).toDouble(), (y - record.startY).toDouble()).toFloat()
        return dist <= swipeStartDistance * tapMaxDriftFactor
    }

    /** Shape writing needs real evidence, not just a movement past the touch slop. */
    private fun hasShapeWritingEvidence(record: PointerRecord): Boolean {
        val letterKeys = record.visitedKeys.count { it.action == KeyAction.TEXT && it.label.firstOrNull()?.isLetter() == true }
        if (letterKeys >= swipeMinDistinctKeys) return true
        return letterKeys >= 2 && record.distanceTraveled >= swipeStartDistance * swipeLongPathFactor
    }

    private fun completeTap(record: PointerRecord, time: Long) {
        if (isBlockedByEarlierKey(record, time)) {
            pendingTaps.add(record)
        } else {
            emitTap(record, time)
            flushPending(time)
        }
    }

    /** True while a key pressed earlier is still held and recent enough to define order. */
    private fun isBlockedByEarlierKey(record: PointerRecord, time: Long): Boolean =
        activePointers.values.any {
            it.downSeq < record.downSeq && !it.hasTriggeredLongPress &&
                time - it.startTime <= tapOrderWindowMs
        }

    private fun emitTap(record: PointerRecord, time: Long) {
        val key = record.initialKey ?: return
        listener.onKeyTapped(key)
    }
}
