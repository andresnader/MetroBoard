/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

/** The timings behind Settings > Gestures. The settings are named choices; this turns them into milliseconds. */
object GestureTimings {
    /** How long a key is held before its long-press action (accents, language picker) starts. */
    fun longPressMs(setting: String): Long = when (setting.uppercase()) {
        "SHORT" -> 250L
        "LONG" -> 500L
        "VERY_LONG" -> 700L
        else -> 350L
    }

    /** How long Backspace is held before it starts repeating. */
    fun backspaceInitialDelayMs(setting: String): Long = when (setting.uppercase()) {
        "SHORT" -> 250L
        "LONG" -> 600L
        else -> 400L
    }

    /**
     * Time before the next repeat of a held Backspace. [repeatIndex] counts the repeats already done. It starts gently
     * (a few characters one at a time) and speeds up, instead of repeating at full speed from the first moment.
     */
    fun backspaceIntervalMs(repeatIndex: Int, speed: String): Long {
        val gentle = repeatIndex < GENTLE_REPEATS
        return when (speed.uppercase()) {
            "SLOW" -> if (gentle) 160L else 110L
            "FAST" -> if (gentle) 90L else 65L
            else -> if (gentle) 120L else 80L
        }
    }

    private const val GENTLE_REPEATS = 8
}
