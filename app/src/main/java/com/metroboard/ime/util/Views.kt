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

    /**
     * The 20 Windows Phone 8 accent colours, names and hex taken from the "Windows Phone Design"
     * deck (BUILD 2013, slide "Colors"). The deck's RGB column has two typos (Pink G, Yellow G);
     * the hex column agrees with the rendered swatches and is the one used here.
     * Plain ARGB literals (not Color.rgb) so the table can be unit-tested on the JVM.
     */
    val LUMIA_PALETTE = listOf(
        LumiaAccent("Lime", 0xFFA4C400.toInt()),
        LumiaAccent("Green", 0xFF60A917.toInt()),
        LumiaAccent("Emerald", 0xFF008A00.toInt()),
        LumiaAccent("Teal", 0xFF00ABA9.toInt()),
        LumiaAccent("Cyan", 0xFF1BA1E2.toInt()),
        LumiaAccent("Cobalt", 0xFF0050EF.toInt()),
        LumiaAccent("Indigo", 0xFF6A00FF.toInt()),
        LumiaAccent("Violet", 0xFFAA00FF.toInt()),
        LumiaAccent("Pink", 0xFFF472D0.toInt()),
        LumiaAccent("Magenta", 0xFFD80073.toInt()),
        LumiaAccent("Crimson", 0xFFA20025.toInt()),
        LumiaAccent("Red", 0xFFE51400.toInt()),
        LumiaAccent("Orange", 0xFFFA6800.toInt()),
        LumiaAccent("Amber", 0xFFF0A30A.toInt()),
        LumiaAccent("Yellow", 0xFFD8C100.toInt()),
        LumiaAccent("Brown", 0xFF825A2C.toInt()),
        LumiaAccent("Olive", 0xFF6D8764.toInt()),
        LumiaAccent("Steel", 0xFF647687.toInt()),
        LumiaAccent("Mauve", 0xFF76608A.toInt()),
        LumiaAccent("Sienna", 0xFF7A3B3F.toInt()),
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
