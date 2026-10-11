/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

data class LumiaAccent(val name: String, val color: Int)

object KeyboardColors {
    var isDarkTheme: Boolean = true

    fun setTheme(theme: String, isSystemDark: Boolean = true) {
        isDarkTheme = when (theme.uppercase()) {
            "LIGHT" -> false
            "SYSTEM" -> isSystemDark
            else -> true
        }
    }

    val background: Int
        get() = if (isDarkTheme) Color.rgb(0, 0, 0) else Color.rgb(255, 255, 255)

    val key: Int
        get() = if (isDarkTheme) Color.rgb(38, 38, 38) else Color.rgb(228, 228, 228)

    val foreground: Int
        get() = if (isDarkTheme) Color.WHITE else Color.BLACK

    val muted: Int
        get() = if (isDarkTheme) Color.rgb(180, 180, 180) else Color.rgb(100, 100, 100)

    val divider: Int
        get() = if (isDarkTheme) Color.rgb(44, 44, 44) else Color.rgb(220, 220, 220)

    var accent: Int = Color.rgb(0, 164, 239)

    val LUMIA_PALETTE = listOf(
        LumiaAccent("Blue", Color.rgb(0, 120, 215)),         // #0078D7
        LumiaAccent("Cyan", Color.rgb(0, 164, 239)),         // #00A4EF
        LumiaAccent("Teal", Color.rgb(0, 130, 114)),         // #008272
        LumiaAccent("Green", Color.rgb(16, 124, 17)),        // #107C11
        LumiaAccent("Lime", Color.rgb(186, 216, 10)),        // #BAD80A
        LumiaAccent("Yellow", Color.rgb(255, 185, 0)),       // #FFB900
        LumiaAccent("Orange", Color.rgb(247, 99, 12)),       // #F7630C
        LumiaAccent("Red", Color.rgb(232, 17, 35)),          // #E81123
        LumiaAccent("Magenta", Color.rgb(180, 0, 158)),      // #B4009E
        LumiaAccent("Purple", Color.rgb(116, 77, 169)),      // #744DA9
    )

    fun keyBackground(
        active: Boolean = false,
        tintKeys: Boolean = false,
        accentColor: Int = accent
    ): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(accentColor))
        val defaultBg = if (active) {
            if (isDarkTheme) Color.WHITE else Color.BLACK
        } else if (tintKeys) {
            val baseKey = key
            val r = (Color.red(baseKey) * 0.82f + Color.red(accentColor) * 0.18f).toInt()
            val g = (Color.green(baseKey) * 0.82f + Color.green(accentColor) * 0.18f).toInt()
            val b = (Color.blue(baseKey) * 0.82f + Color.blue(accentColor) * 0.18f).toInt()
            Color.rgb(r, g, b)
        } else {
            key
        }
        addState(intArrayOf(), ColorDrawable(defaultBg))
    }
}
