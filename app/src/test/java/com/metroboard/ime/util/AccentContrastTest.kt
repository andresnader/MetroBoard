/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selected segments and pressed buttons are filled with the accent; their text must stay readable on all 20. */
class AccentContrastTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun lightAccentsGetBlackText() {
        for (name in listOf("Lime", "Cyan", "Amber", "Yellow", "Pink", "Orange", "Teal")) {
            assertEquals(name, black, KeyboardColors.onColor(accent(name)))
        }
    }

    @Test fun darkAccentsGetWhiteText() {
        for (name in listOf("Cobalt", "Indigo", "Crimson", "Brown", "Sienna", "Mauve")) {
            assertEquals(name, white, KeyboardColors.onColor(accent(name)))
        }
    }

    @Test fun blackAndWhiteBackgroundsAreObvious() {
        assertEquals(white, KeyboardColors.onColor(black))
        assertEquals(black, KeyboardColors.onColor(white))
    }

    @Test fun theChosenTextAlwaysReachesAReadableContrastOnEveryAccent() {
        for (a in KeyboardColors.LUMIA_PALETTE) {
            val ratio = contrast(a.color, KeyboardColors.onColor(a.color))
            assertTrue("${a.name} contrast $ratio", ratio >= 3.0)
        }
    }

    private fun accent(name: String) = KeyboardColors.LUMIA_PALETTE.first { it.name == name }.color

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val hi = maxOf(lum(a), lum(b)); val lo = minOf(lum(a), lum(b))
        return (hi + 0.05) / (lo + 0.05)
    }
}
