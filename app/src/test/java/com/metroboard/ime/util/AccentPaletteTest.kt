/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import com.metroboard.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The accent table must stay identical to the "Windows Phone Design" deck (slide "Colors", hex column). */
class AccentPaletteTest {
    private val expected = listOf(
        "Lime" to 0xA4C400, "Green" to 0x60A917, "Emerald" to 0x008A00, "Teal" to 0x00ABA9, "Cyan" to 0x1BA1E2,
        "Cobalt" to 0x0050EF, "Indigo" to 0x6A00FF, "Violet" to 0xAA00FF, "Pink" to 0xF472D0, "Magenta" to 0xD80073,
        "Crimson" to 0xA20025, "Red" to 0xE51400, "Orange" to 0xFA6800, "Amber" to 0xF0A30A, "Yellow" to 0xD8C100,
        "Brown" to 0x825A2C, "Olive" to 0x6D8764, "Steel" to 0x647687, "Mauve" to 0x76608A, "Sienna" to 0x7A3B3F,
    )

    @Test fun thePaletteIsTheTwentyWindowsPhone8Accents() {
        val actual = KeyboardColors.LUMIA_PALETTE.map { it.name to (it.color and 0xFFFFFF) }
        assertEquals(expected, actual)
    }

    @Test fun everyAccentIsOpaqueAndUnique() {
        val colors = KeyboardColors.LUMIA_PALETTE.map { it.color }
        assertTrue(colors.all { (it ushr 24) == 0xFF })
        assertEquals(20, colors.toSet().size)
        assertEquals(20, KeyboardColors.LUMIA_PALETTE.map { it.name }.toSet().size)
    }

    @Test fun theDefaultAccentIsOneOfThem() {
        assertTrue(KeyboardColors.LUMIA_PALETTE.any { it.color == KeyboardPreferences.DEFAULT_ACCENT_COLOR })
        assertEquals("Cyan", KeyboardColors.LUMIA_PALETTE.first { it.color == KeyboardPreferences.DEFAULT_ACCENT_COLOR }.name)
    }
}
