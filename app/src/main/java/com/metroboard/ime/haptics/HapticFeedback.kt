/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

class HapticFeedback {
    fun play(view: View, enabled: Boolean, strengthLevel: String = "MEDIUM") {
        if (!enabled) return

        val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val constant = when (strengthLevel.uppercase()) {
                "LIGHT" -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
                "STRONG" -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            val performed = view.performHapticFeedback(constant, flags)
            if (performed) return
        } else {
            val performed = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
            if (performed) return
        }

        // Direct vibrator fallback if view haptics are not delivered
        try {
            val vibrator = view.context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = when (strengthLevel.uppercase()) {
                        "LIGHT" -> 7L
                        "STRONG" -> 25L
                        else -> 14L
                    }
                    val amplitude = when (strengthLevel.uppercase()) {
                        "LIGHT" -> 70
                        "STRONG" -> 255
                        else -> 150
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(12L)
                }
            }
        } catch (_: Exception) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
        }
    }
}
