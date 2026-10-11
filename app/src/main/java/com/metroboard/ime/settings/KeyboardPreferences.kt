/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.metroboard.ime.dictionary.LocalPersonalDictionary
import org.json.JSONArray
import org.json.JSONObject

class KeyboardPreferences(val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("keyboard_preferences", Context.MODE_PRIVATE)
    )

    var vibration: Boolean
        get() = preferences.getBoolean(KEY_HAPTIC, preferences.getBoolean(KEY_LEGACY_VIBRATION, false))
        set(value) {
            preferences.edit()
                .putBoolean(KEY_HAPTIC, value)
                .putBoolean(KEY_LEGACY_VIBRATION, value)
                .apply()
        }

    var hapticStrength: String
        get() = preferences.getString(KEY_HAPTIC_STRENGTH_LEVEL, "MEDIUM") ?: "MEDIUM"
        set(value) { preferences.edit().putString(KEY_HAPTIC_STRENGTH_LEVEL, value).apply() }

    var vibrationStrength: Int
        get() = preferences.getInt(KEY_HAPTIC_STRENGTH, 25)
        set(value) { preferences.edit().putInt(KEY_HAPTIC_STRENGTH, value).apply() }

    var sound: Boolean
        get() = preferences.getBoolean(KEY_SOUND, preferences.getBoolean(KEY_LEGACY_SOUND, false))
        set(value) {
            preferences.edit()
                .putBoolean(KEY_SOUND, value)
                .putBoolean(KEY_LEGACY_SOUND, value)
                .apply()
        }

    var soundVolume: Float
        get() = preferences.getFloat(KEY_SOUND_VOLUME, 0.7f)
        set(value) { preferences.edit().putFloat(KEY_SOUND_VOLUME, value).apply() }

    var suggestions: Boolean
        get() = preferences.getBoolean(KEY_SUGGESTIONS, preferences.getBoolean(KEY_LEGACY_SUGGESTIONS, true))
        set(value) {
            preferences.edit()
                .putBoolean(KEY_SUGGESTIONS, value)
                .putBoolean(KEY_LEGACY_SUGGESTIONS, value)
                .apply()
        }

    var autoCorrect: Boolean
        get() = preferences.getBoolean(KEY_AUTOCORRECT, true)
        set(value) { preferences.edit().putBoolean(KEY_AUTOCORRECT, value).apply() }

    var autoCapitalization: Boolean
        get() = preferences.getBoolean(KEY_AUTOCAP, true)
        set(value) { preferences.edit().putBoolean(KEY_AUTOCAP, value).apply() }

    var doubleSpacePeriod: Boolean
        get() = preferences.getBoolean(KEY_DOUBLE_SPACE_PERIOD, true)
        set(value) { preferences.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, value).apply() }

    var shiftWordCasing: Boolean
        get() = preferences.getBoolean(KEY_SHIFT_WORD_CASING, true)
        set(value) { preferences.edit().putBoolean(KEY_SHIFT_WORD_CASING, value).apply() }

    var numberRow: Boolean
        get() = preferences.getBoolean(KEY_SHOW_NUMBER_ROW, false)
        set(value) { preferences.edit().putBoolean(KEY_SHOW_NUMBER_ROW, value).apply() }

    var accentColor: Int
        get() = preferences.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR)
        set(value) { preferences.edit().putInt(KEY_ACCENT_COLOR, value).apply() }

    var tintKeys: Boolean
        get() = preferences.getBoolean(KEY_TINT_KEYS, false)
        set(value) { preferences.edit().putBoolean(KEY_TINT_KEYS, value).apply() }

    var heightScale: Float
        get() = preferences.getFloat(KEY_HEIGHT_SCALE, 1.0f)
        set(value) { preferences.edit().putFloat(KEY_HEIGHT_SCALE, value).apply() }

    var heightMode: String
        get() = preferences.getString(KEY_HEIGHT_MODE, "NORMAL") ?: "NORMAL"
        set(value) {
            val scale = when (value.uppercase()) {
                "COMPACT" -> 0.85f
                "TALL" -> 1.15f
                else -> 1.0f
            }
            preferences.edit()
                .putString(KEY_HEIGHT_MODE, value)
                .putFloat(KEY_HEIGHT_SCALE, scale)
                .apply()
        }

    var wordFlow: Boolean
        get() = preferences.getBoolean(KEY_WORDFLOW, true)
        set(value) { preferences.edit().putBoolean(KEY_WORDFLOW, value).apply() }

    var spacebarCursor: Boolean
        get() = preferences.getBoolean(KEY_SPACEBAR_CURSOR, true)
        set(value) { preferences.edit().putBoolean(KEY_SPACEBAR_CURSOR, value).apply() }

    var longPressSpace: Boolean
        get() = preferences.getBoolean(KEY_LONG_PRESS_SPACE, true)
        set(value) { preferences.edit().putBoolean(KEY_LONG_PRESS_SPACE, value).apply() }

    var keyPopup: Boolean
        get() = preferences.getBoolean(KEY_SHOW_KEY_POPUP, true)
        set(value) { preferences.edit().putBoolean(KEY_SHOW_KEY_POPUP, value).apply() }

    var edgeToEdge: Boolean
        get() = preferences.getBoolean(KEY_EDGE_TO_EDGE, true)
        set(value) { preferences.edit().putBoolean(KEY_EDGE_TO_EDGE, value).apply() }

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, "es") ?: "es"
        set(value) { preferences.edit().putString(KEY_LANGUAGE, value).apply() }

    var layoutOverride: String
        get() = preferences.getString(KEY_LAYOUT_OVERRIDE, "AUTO") ?: "AUTO"
        set(value) { preferences.edit().putString(KEY_LAYOUT_OVERRIDE, value).apply() }

    var theme: String
        get() = preferences.getString(KEY_THEME, "DARK") ?: "DARK"
        set(value) { preferences.edit().putString(KEY_THEME, value).apply() }

    var oneHandedMode: String
        get() = preferences.getString(KEY_ONE_HANDED_MODE, "OFF") ?: "OFF"
        set(value) { preferences.edit().putString(KEY_ONE_HANDED_MODE, value).apply() }

    fun exportJson(customWords: List<String> = emptyList()): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "MetroBoard")
        root.put("language", language)
        root.put("timestamp", System.currentTimeMillis())

        val wordsArray = JSONArray()
        for (w in customWords) {
            val obj = JSONObject()
            obj.put("word", w)
            obj.put("frequency", 10)
            wordsArray.put(obj)
        }
        root.put("words", wordsArray)

        val settings = JSONObject()
        settings.put(KEY_SOUND, sound)
        settings.put(KEY_SOUND_VOLUME, soundVolume.toDouble())
        settings.put(KEY_HAPTIC, vibration)
        settings.put(KEY_HAPTIC_STRENGTH_LEVEL, hapticStrength)
        settings.put(KEY_SUGGESTIONS, suggestions)
        settings.put(KEY_AUTOCORRECT, autoCorrect)
        settings.put(KEY_AUTOCAP, autoCapitalization)
        settings.put(KEY_DOUBLE_SPACE_PERIOD, doubleSpacePeriod)
        settings.put(KEY_SHIFT_WORD_CASING, shiftWordCasing)
        settings.put(KEY_SHOW_NUMBER_ROW, numberRow)
        settings.put(KEY_ACCENT_COLOR, accentColor)
        settings.put(KEY_TINT_KEYS, tintKeys)
        settings.put(KEY_HEIGHT_SCALE, heightScale.toDouble())
        settings.put(KEY_HEIGHT_MODE, heightMode)
        settings.put(KEY_WORDFLOW, wordFlow)
        settings.put(KEY_SPACEBAR_CURSOR, spacebarCursor)
        settings.put(KEY_LONG_PRESS_SPACE, longPressSpace)
        settings.put(KEY_SHOW_KEY_POPUP, keyPopup)
        settings.put(KEY_EDGE_TO_EDGE, edgeToEdge)
        settings.put(KEY_LANGUAGE, language)
        settings.put(KEY_LAYOUT_OVERRIDE, layoutOverride)
        settings.put(KEY_THEME, theme)
        settings.put(KEY_ONE_HANDED_MODE, oneHandedMode)

        root.put("settings", settings)
        return root.toString(2)
    }

    fun importJson(jsonStr: String): Pair<Int, Int> {
        val root = JSONObject(jsonStr)
        var wordsImported = 0
        var settingsImported = 0

        if (root.has("words")) {
            val wordsArray = root.getJSONArray("words")
            wordsImported = wordsArray.length()
        } else if (root.has("custom_words")) {
            val wordsArray = root.getJSONArray("custom_words")
            wordsImported = wordsArray.length()
        }

        if (root.has("settings")) {
            val settings = root.getJSONObject("settings")
            val editor = preferences.edit()

            if (settings.has(KEY_SOUND)) {
                val value = settings.getBoolean(KEY_SOUND)
                editor.putBoolean(KEY_SOUND, value)
                editor.putBoolean(KEY_LEGACY_SOUND, value)
                settingsImported++
            }
            if (settings.has(KEY_HAPTIC)) {
                val value = settings.getBoolean(KEY_HAPTIC)
                editor.putBoolean(KEY_HAPTIC, value)
                editor.putBoolean(KEY_LEGACY_VIBRATION, value)
                settingsImported++
            }
            if (settings.has(KEY_HAPTIC_STRENGTH_LEVEL)) {
                editor.putString(KEY_HAPTIC_STRENGTH_LEVEL, settings.getString(KEY_HAPTIC_STRENGTH_LEVEL))
                settingsImported++
            }
            if (settings.has(KEY_SOUND_VOLUME)) {
                editor.putFloat(KEY_SOUND_VOLUME, settings.getDouble(KEY_SOUND_VOLUME).toFloat())
                settingsImported++
            }
            if (settings.has(KEY_SUGGESTIONS)) {
                val value = settings.getBoolean(KEY_SUGGESTIONS)
                editor.putBoolean(KEY_SUGGESTIONS, value)
                editor.putBoolean(KEY_LEGACY_SUGGESTIONS, value)
                settingsImported++
            }
            if (settings.has(KEY_AUTOCORRECT)) {
                editor.putBoolean(KEY_AUTOCORRECT, settings.getBoolean(KEY_AUTOCORRECT))
                settingsImported++
            }
            if (settings.has(KEY_AUTOCAP)) {
                editor.putBoolean(KEY_AUTOCAP, settings.getBoolean(KEY_AUTOCAP))
                settingsImported++
            }
            if (settings.has(KEY_DOUBLE_SPACE_PERIOD)) {
                editor.putBoolean(KEY_DOUBLE_SPACE_PERIOD, settings.getBoolean(KEY_DOUBLE_SPACE_PERIOD))
                settingsImported++
            }
            if (settings.has(KEY_SHIFT_WORD_CASING)) {
                editor.putBoolean(KEY_SHIFT_WORD_CASING, settings.getBoolean(KEY_SHIFT_WORD_CASING))
                settingsImported++
            }
            if (settings.has(KEY_SHOW_NUMBER_ROW)) {
                editor.putBoolean(KEY_SHOW_NUMBER_ROW, settings.getBoolean(KEY_SHOW_NUMBER_ROW))
                settingsImported++
            }
            if (settings.has(KEY_ACCENT_COLOR)) {
                val rawColor = settings.get(KEY_ACCENT_COLOR)
                val colorInt = when (rawColor) {
                    is Number -> rawColor.toInt()
                    is String -> Color.parseColor(rawColor)
                    else -> DEFAULT_ACCENT_COLOR
                }
                editor.putInt(KEY_ACCENT_COLOR, colorInt)
                settingsImported++
            }
            if (settings.has(KEY_TINT_KEYS)) {
                editor.putBoolean(KEY_TINT_KEYS, settings.getBoolean(KEY_TINT_KEYS))
                settingsImported++
            }
            if (settings.has(KEY_HEIGHT_SCALE)) {
                editor.putFloat(KEY_HEIGHT_SCALE, settings.getDouble(KEY_HEIGHT_SCALE).toFloat())
                settingsImported++
            }
            if (settings.has(KEY_HEIGHT_MODE)) {
                editor.putString(KEY_HEIGHT_MODE, settings.getString(KEY_HEIGHT_MODE))
                settingsImported++
            }
            if (settings.has(KEY_WORDFLOW)) {
                editor.putBoolean(KEY_WORDFLOW, settings.getBoolean(KEY_WORDFLOW))
                settingsImported++
            }
            if (settings.has(KEY_SPACEBAR_CURSOR)) {
                editor.putBoolean(KEY_SPACEBAR_CURSOR, settings.getBoolean(KEY_SPACEBAR_CURSOR))
                settingsImported++
            }
            if (settings.has(KEY_LONG_PRESS_SPACE)) {
                editor.putBoolean(KEY_LONG_PRESS_SPACE, settings.getBoolean(KEY_LONG_PRESS_SPACE))
                settingsImported++
            }
            if (settings.has(KEY_SHOW_KEY_POPUP)) {
                editor.putBoolean(KEY_SHOW_KEY_POPUP, settings.getBoolean(KEY_SHOW_KEY_POPUP))
                settingsImported++
            }
            if (settings.has(KEY_EDGE_TO_EDGE)) {
                editor.putBoolean(KEY_EDGE_TO_EDGE, settings.getBoolean(KEY_EDGE_TO_EDGE))
                settingsImported++
            }
            if (settings.has(KEY_LANGUAGE)) {
                editor.putString(KEY_LANGUAGE, settings.getString(KEY_LANGUAGE))
                settingsImported++
            }
            if (settings.has(KEY_LAYOUT_OVERRIDE)) {
                editor.putString(KEY_LAYOUT_OVERRIDE, settings.getString(KEY_LAYOUT_OVERRIDE))
                settingsImported++
            }
            if (settings.has(KEY_THEME)) {
                editor.putString(KEY_THEME, settings.getString(KEY_THEME))
                settingsImported++
            }
            if (settings.has(KEY_ONE_HANDED_MODE)) {
                editor.putString(KEY_ONE_HANDED_MODE, settings.getString(KEY_ONE_HANDED_MODE))
                settingsImported++
            }

            editor.apply()
        }

        return Pair(wordsImported, settingsImported)
    }

    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_SOUND).remove(KEY_LEGACY_SOUND)
            .remove(KEY_SOUND_VOLUME)
            .remove(KEY_HAPTIC).remove(KEY_LEGACY_VIBRATION)
            .remove(KEY_HAPTIC_STRENGTH_LEVEL)
            .remove(KEY_HAPTIC_STRENGTH)
            .remove(KEY_SUGGESTIONS).remove(KEY_LEGACY_SUGGESTIONS)
            .remove(KEY_AUTOCORRECT)
            .remove(KEY_AUTOCAP)
            .remove(KEY_DOUBLE_SPACE_PERIOD)
            .remove(KEY_SHIFT_WORD_CASING)
            .remove(KEY_SHOW_NUMBER_ROW)
            .remove(KEY_ACCENT_COLOR)
            .remove(KEY_TINT_KEYS)
            .remove(KEY_HEIGHT_SCALE)
            .remove(KEY_HEIGHT_MODE)
            .remove(KEY_WORDFLOW)
            .remove(KEY_SPACEBAR_CURSOR)
            .remove(KEY_LONG_PRESS_SPACE)
            .remove(KEY_SHOW_KEY_POPUP)
            .remove(KEY_EDGE_TO_EDGE)
            .remove(KEY_LANGUAGE)
            .remove(KEY_LAYOUT_OVERRIDE)
            .remove(KEY_THEME)
            .remove(KEY_ONE_HANDED_MODE)
            .apply()
    }

    companion object {
        const val DEFAULT_ACCENT_COLOR: Int = -16735073 // 0xFF00A4EF.toInt() Lumia Cyan
        const val KEY_LEGACY_VIBRATION = "vibration"
        const val KEY_LEGACY_SOUND = "sound"
        const val KEY_LEGACY_SUGGESTIONS = "suggestions"

        const val KEY_SOUND = "pref_sound"
        const val KEY_SOUND_VOLUME = "pref_sound_volume"
        const val KEY_HAPTIC = "pref_haptic"
        const val KEY_HAPTIC_STRENGTH_LEVEL = "pref_haptic_strength_level"
        const val KEY_HAPTIC_STRENGTH = "pref_haptic_strength"
        const val KEY_SUGGESTIONS = "pref_suggestions"
        const val KEY_AUTOCORRECT = "pref_autocorrect"
        const val KEY_AUTOCAP = "pref_autocap"
        const val KEY_DOUBLE_SPACE_PERIOD = "pref_double_space_period"
        const val KEY_SHIFT_WORD_CASING = "pref_shift_word_casing"
        const val KEY_SHOW_NUMBER_ROW = "pref_show_number_row"
        const val KEY_ACCENT_COLOR = "pref_accent_color"
        const val KEY_TINT_KEYS = "pref_tint_keys"
        const val KEY_HEIGHT_SCALE = "pref_height_scale"
        const val KEY_HEIGHT_MODE = "pref_height_mode"
        const val KEY_WORDFLOW = "pref_wordflow"
        const val KEY_SPACEBAR_CURSOR = "pref_spacebar_cursor"
        const val KEY_LONG_PRESS_SPACE = "pref_long_press_space"
        const val KEY_SHOW_KEY_POPUP = "pref_show_key_popup"
        const val KEY_EDGE_TO_EDGE = "pref_edge_to_edge"
        const val KEY_LANGUAGE = "pref_language"
        const val KEY_LAYOUT_OVERRIDE = "pref_layout_override"
        const val KEY_THEME = "pref_theme"
        const val KEY_ONE_HANDED_MODE = "pref_one_handed_mode"
    }
}
