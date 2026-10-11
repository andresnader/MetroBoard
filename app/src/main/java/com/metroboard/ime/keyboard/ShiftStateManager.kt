/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

enum class ShiftState { AUTO_SHIFT, ONE_SHOT_SHIFT, CAPS_LOCK, LOWERCASE }

/** No Android dependencies or wall clock; callers supply monotonic milliseconds. */
class ShiftStateManager(private val doubleTapTimeoutMs: Long = DOUBLE_TAP_TIMEOUT_MS) {
    var state: ShiftState = ShiftState.LOWERCASE
        private set
    private var lastTap: Long? = null
    private var suppressAuto = false
    val isUppercase: Boolean get() = state != ShiftState.LOWERCASE

    fun reset(shouldCapitalize: Boolean) {
        lastTap = null
        suppressAuto = false
        state = if (shouldCapitalize) ShiftState.AUTO_SHIFT else ShiftState.LOWERCASE
    }

    fun tap(now: Long) {
        val previous = lastTap
        when {
            state == ShiftState.CAPS_LOCK -> {
                state = ShiftState.LOWERCASE
                suppressAuto = true
                lastTap = null
            }
            previous != null && now - previous in 0..doubleTapTimeoutMs -> {
                state = ShiftState.CAPS_LOCK
                lastTap = null
            }
            else -> {
                state = if (state == ShiftState.ONE_SHOT_SHIFT) ShiftState.LOWERCASE
                    else ShiftState.ONE_SHOT_SHIFT
                suppressAuto = state == ShiftState.LOWERCASE
                lastTap = now
            }
        }
    }

    fun onLetterCommitted() {
        lastTap = null
        suppressAuto = false
        if (state != ShiftState.CAPS_LOCK) state = ShiftState.LOWERCASE
    }

    fun breakTapSequence() { lastTap = null }

    fun updateAutomatic(shouldCapitalize: Boolean) {
        if (!shouldCapitalize) suppressAuto = false
        if (state == ShiftState.CAPS_LOCK || state == ShiftState.ONE_SHOT_SHIFT) return
        state = if (shouldCapitalize && !suppressAuto) ShiftState.AUTO_SHIFT else ShiftState.LOWERCASE
    }

    fun onSentenceBoundary() {
        suppressAuto = false
        lastTap = null
        if (state != ShiftState.CAPS_LOCK) state = ShiftState.AUTO_SHIFT
    }

    fun onCursorMoved(shouldCapitalize: Boolean) {
        lastTap = null
        suppressAuto = false
        if (state != ShiftState.CAPS_LOCK) reset(shouldCapitalize)
    }

    companion object { const val DOUBLE_TAP_TIMEOUT_MS = 300L }
}
