/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardPreferencesTest {
    private lateinit var preferences: KeyboardPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        preferences = KeyboardPreferences(prefs)
    }

    @Test
    fun defaultValuesAreCorrect() {
        assertFalse(preferences.vibration)
        assertFalse(preferences.sound)
        assertTrue(preferences.suggestions)
        assertTrue(preferences.autoCorrect)
        assertTrue(preferences.autoCapitalization)
        assertTrue(preferences.doubleSpacePeriod)
        assertTrue(preferences.shiftWordCasing)
        assertFalse(preferences.numberRow)
        assertEquals(KeyboardPreferences.DEFAULT_ACCENT_COLOR, preferences.accentColor)
        assertEquals(1.0f, preferences.heightScale, 0.001f)
        assertEquals("es", preferences.language)
    }

    @Test
    fun settersAndGettersWork() {
        preferences.vibration = true
        preferences.sound = true
        preferences.suggestions = false
        preferences.autoCorrect = false
        preferences.autoCapitalization = false
        preferences.doubleSpacePeriod = false
        preferences.shiftWordCasing = false
        preferences.numberRow = true
        preferences.accentColor = 0xFFE51400.toInt()
        preferences.heightScale = 1.15f
        preferences.language = "en"

        assertTrue(preferences.vibration)
        assertTrue(preferences.sound)
        assertFalse(preferences.suggestions)
        assertFalse(preferences.autoCorrect)
        assertFalse(preferences.autoCapitalization)
        assertFalse(preferences.doubleSpacePeriod)
        assertFalse(preferences.shiftWordCasing)
        assertTrue(preferences.numberRow)
        assertEquals(0xFFE51400.toInt(), preferences.accentColor)
        assertEquals(1.15f, preferences.heightScale, 0.001f)
        assertEquals("en", preferences.language)
    }

    @Test
    fun exportAndImportJsonRoundTrip() {
        preferences.sound = true
        preferences.vibration = true
        preferences.numberRow = true
        preferences.accentColor = 0xFF008A00.toInt()
        preferences.heightScale = 0.85f
        preferences.shiftWordCasing = true

        val json = preferences.exportJson(listOf("custom1", "custom2"))
        assertTrue(json.contains("\"app\": \"MetroBoard\""))
        assertTrue(json.contains("\"pref_sound\": true"))
        assertTrue(json.contains("\"pref_show_number_row\": true"))
        assertTrue(json.contains("\"pref_shift_word_casing\": true"))

        // Reset and re-import
        preferences.resetToDefaults()
        assertFalse(preferences.sound)
        assertFalse(preferences.numberRow)

        val (wordsCount, settingsCount) = preferences.importJson(json)
        assertEquals(2, wordsCount)
        assertTrue(settingsCount > 0)
        assertTrue(preferences.sound)
        assertTrue(preferences.vibration)
        assertTrue(preferences.numberRow)
        assertEquals(0xFF008A00.toInt(), preferences.accentColor)
        assertEquals(0.85f, preferences.heightScale, 0.01f)
    }

    @Test
    fun importChiFlowCompatibility() {
        val chiFlowJson = """
            {
                "version": 1,
                "app": "ChiFlow",
                "custom_words": ["hola", "mundo"],
                "settings": {
                    "pref_sound": true,
                    "pref_haptic": true,
                    "pref_suggestions": false,
                    "pref_autocorrect": false,
                    "pref_autocap": false,
                    "pref_double_space_period": false,
                    "pref_show_number_row": true,
                    "pref_accent_color": -16743185,
                    "pref_height_scale": 1.15
                }
            }
        """.trimIndent()

        val (words, settings) = preferences.importJson(chiFlowJson)
        assertEquals(2, words)
        assertTrue(settings >= 8)
        assertTrue(preferences.sound)
        assertTrue(preferences.vibration)
        assertFalse(preferences.suggestions)
        assertFalse(preferences.autoCorrect)
        assertFalse(preferences.autoCapitalization)
        assertFalse(preferences.doubleSpacePeriod)
        assertTrue(preferences.numberRow)
        assertEquals(-16743185, preferences.accentColor)
        assertEquals(1.15f, preferences.heightScale, 0.01f)
    }

    @Test
    fun resetToDefaultsRestoresInitialState() {
        preferences.sound = true
        preferences.numberRow = true
        preferences.accentColor = 0xFF7137C8.toInt()
        preferences.heightScale = 1.2f

        preferences.resetToDefaults()

        assertFalse(preferences.sound)
        assertFalse(preferences.numberRow)
        assertEquals(KeyboardPreferences.DEFAULT_ACCENT_COLOR, preferences.accentColor)
        assertEquals(1.0f, preferences.heightScale, 0.001f)
    }
}
