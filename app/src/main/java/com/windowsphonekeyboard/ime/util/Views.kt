package com.windowsphonekeyboard.ime.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

data class LumiaAccent(val name: String, val color: Int)

object KeyboardColors {
    val background = Color.rgb(16, 16, 16)
    val key = Color.rgb(48, 48, 48)
    var accent: Int = Color.rgb(0, 164, 239)
    val muted = Color.rgb(184, 184, 184)

    val LUMIA_PALETTE = listOf(
        LumiaAccent("Cyan", Color.rgb(0, 164, 239)),        // #00A4EF
        LumiaAccent("Cobalt", Color.rgb(0, 80, 239)),       // #0050EF
        LumiaAccent("Teal", Color.rgb(0, 171, 169)),        // #00ABA9
        LumiaAccent("Lime", Color.rgb(164, 196, 0)),        // #A4C400
        LumiaAccent("Green", Color.rgb(96, 169, 23)),       // #60A917
        LumiaAccent("Emerald", Color.rgb(0, 138, 0)),       // #008A00
        LumiaAccent("Indigo", Color.rgb(106, 0, 255)),      // #6A00FF
        LumiaAccent("Violet", Color.rgb(170, 0, 255)),      // #AA00FF
        LumiaAccent("Pink", Color.rgb(244, 114, 208)),      // #F472D0
        LumiaAccent("Magenta", Color.rgb(216, 0, 115)),     // #D80073
        LumiaAccent("Crimson", Color.rgb(162, 0, 37)),      // #A20025
        LumiaAccent("Red", Color.rgb(229, 20, 0)),          // #E51400
        LumiaAccent("Orange", Color.rgb(250, 104, 0)),      // #FA6800
        LumiaAccent("Amber", Color.rgb(240, 163, 10)),      // #F0A30A
        LumiaAccent("Yellow", Color.rgb(227, 200, 0)),      // #E3C800
        LumiaAccent("Brown", Color.rgb(130, 90, 44)),       // #825A2C
        LumiaAccent("Olive", Color.rgb(109, 135, 100)),     // #6D8764
        LumiaAccent("Steel", Color.rgb(100, 118, 135)),     // #647687
        LumiaAccent("Mauve", Color.rgb(118, 96, 138)),      // #76608A
        LumiaAccent("Taupe", Color.rgb(135, 121, 78)),      // #87794E
    )

    fun keyBackground(active: Boolean = false, accentColor: Int = accent): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(accentColor))
        addState(intArrayOf(), ColorDrawable(if (active) Color.WHITE else key))
    }
}
