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
import com.metroboard.ime.dictionary.BackupFormat
import com.metroboard.ime.dictionary.LearnedWord
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

    /** Remember new words the user types (never in sensitive fields). Opt-out; "Ver palabras aprendidas" shows them. */
    var learnWords: Boolean
        get() = preferences.getBoolean(KEY_LEARN_WORDS, true)
        set(value) { preferences.edit().putBoolean(KEY_LEARN_WORDS, value).apply() }

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


    var language: String
        get() = preferences.getString(KEY_LANGUAGE, null) ?: languageFor(java.util.Locale.getDefault().language)
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

    /** Current value of every setting that a backup carries (see BackupFormat's whitelist). */
    fun exportSettingsMap(): Map<String, Any> = linkedMapOf(
        KEY_SOUND to sound, KEY_SOUND_VOLUME to soundVolume.toDouble(), KEY_HAPTIC to vibration,
        KEY_HAPTIC_STRENGTH_LEVEL to hapticStrength, KEY_SUGGESTIONS to suggestions, KEY_AUTOCORRECT to autoCorrect,
        KEY_AUTOCAP to autoCapitalization, KEY_LEARN_WORDS to learnWords, KEY_DOUBLE_SPACE_PERIOD to doubleSpacePeriod,
        KEY_SHIFT_WORD_CASING to shiftWordCasing, KEY_SHOW_NUMBER_ROW to numberRow, KEY_ACCENT_COLOR to accentColor,
        KEY_TINT_KEYS to tintKeys, KEY_HEIGHT_MODE to heightMode, KEY_HEIGHT_SCALE to heightScale.toDouble(),
        KEY_WORDFLOW to wordFlow, KEY_SPACEBAR_CURSOR to spacebarCursor, KEY_LONG_PRESS_SPACE to longPressSpace,
        KEY_SHOW_KEY_POPUP to keyPopup, KEY_LANGUAGE to language, KEY_LAYOUT_OVERRIDE to layoutOverride,
        KEY_THEME to theme, KEY_ONE_HANDED_MODE to oneHandedMode,
    )

    /**
     * Applies settings that BackupFormat has already validated (type, range, enumeration).
     * Height mode is applied before height scale because choosing a mode also sets the scale.
     * Returns how many settings were applied.
     */
    fun applySettings(settings: Map<String, Any>): Int {
        var applied = 0
        fun bool(key: String, set: (Boolean) -> Unit) { (settings[key] as? Boolean)?.let { set(it); applied++ } }
        fun text(key: String, set: (String) -> Unit) { (settings[key] as? String)?.let { set(it); applied++ } }
        bool(KEY_SOUND) { sound = it }
        bool(KEY_HAPTIC) { vibration = it }
        bool(KEY_SUGGESTIONS) { suggestions = it }
        bool(KEY_AUTOCORRECT) { autoCorrect = it }
        bool(KEY_AUTOCAP) { autoCapitalization = it }
        bool(KEY_LEARN_WORDS) { learnWords = it }
        bool(KEY_DOUBLE_SPACE_PERIOD) { doubleSpacePeriod = it }
        bool(KEY_SHIFT_WORD_CASING) { shiftWordCasing = it }
        bool(KEY_SHOW_NUMBER_ROW) { numberRow = it }
        bool(KEY_TINT_KEYS) { tintKeys = it }
        bool(KEY_WORDFLOW) { wordFlow = it }
        bool(KEY_SPACEBAR_CURSOR) { spacebarCursor = it }
        bool(KEY_LONG_PRESS_SPACE) { longPressSpace = it }
        bool(KEY_SHOW_KEY_POPUP) { keyPopup = it }
        text(KEY_HAPTIC_STRENGTH_LEVEL) { hapticStrength = it }
        text(KEY_HEIGHT_MODE) { heightMode = it }
        text(KEY_LANGUAGE) { language = it }
        text(KEY_LAYOUT_OVERRIDE) { layoutOverride = it }
        text(KEY_THEME) { theme = it }
        text(KEY_ONE_HANDED_MODE) { oneHandedMode = it }
        (settings[KEY_SOUND_VOLUME] as? Number)?.let { soundVolume = it.toFloat(); applied++ }
        (settings[KEY_ACCENT_COLOR] as? Number)?.let { accentColor = it.toInt(); applied++ }
        (settings[KEY_HEIGHT_SCALE] as? Number)?.let { heightScale = it.toFloat(); applied++ }
        return applied
    }

    fun exportJson(customWords: List<String> = emptyList()): String =
        BackupFormat.write(exportSettingsMap(), mapOf(language to customWords.map { LearnedWord(it, 10, language) }))

    /** Imports the settings of a backup; returns (words found, settings applied). Throws [BackupFormat.InvalidBackup]. */
    fun importJson(jsonStr: String): Pair<Int, Int> {
        val backup = BackupFormat.parse(jsonStr)
        return backup.words.values.sumOf { it.size } to applySettings(backup.settings)
    }

    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_SOUND).remove(KEY_LEGACY_SOUND)
            .remove(KEY_LEARN_WORDS)
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
            .remove(KEY_EDGE_TO_EDGE) // legacy key from 0.2.0, kept only so a reset clears it
            .remove(KEY_LANGUAGE)
            .remove(KEY_LAYOUT_OVERRIDE)
            .remove(KEY_THEME)
            .remove(KEY_ONE_HANDED_MODE)
            .apply()
    }

    companion object {
        /** Typing languages that have a dictionary and a layout. */
        val SUPPORTED_LANGUAGES = listOf("es", "en", "fr", "de")

        /**
         * The typing language to start with, taken from the device language so a fresh install already
         * types in the user's own language. A device language with no dictionary falls back to English.
         */
        fun languageFor(deviceLanguage: String): String =
            deviceLanguage.lowercase(java.util.Locale.ROOT).takeIf { it in SUPPORTED_LANGUAGES } ?: "en"

        const val DEFAULT_ACCENT_COLOR: Int = 0xFF1BA1E2.toInt() // Windows Phone 8 "Cyan", the Lumia default (was a stray 0xFF00A49F mislabelled as 0xFF00A4EF)
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
        const val KEY_LEARN_WORDS = "pref_learn_words"
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
