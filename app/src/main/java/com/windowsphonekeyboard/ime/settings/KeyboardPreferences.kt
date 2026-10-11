package com.windowsphonekeyboard.ime.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
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

    /**
     * Unique typing enhancement:
     * When cursor is at the end of a word, pressing Shift capitalizes the first letter
     * and cycles word casing: lowercase -> TitleCase -> UPPERCASE -> lowercase.
     */
    var shiftWordCasing: Boolean
        get() = preferences.getBoolean(KEY_SHIFT_WORD_CASING, true)
        set(value) { preferences.edit().putBoolean(KEY_SHIFT_WORD_CASING, value).apply() }

    var numberRow: Boolean
        get() = preferences.getBoolean(KEY_SHOW_NUMBER_ROW, false)
        set(value) { preferences.edit().putBoolean(KEY_SHOW_NUMBER_ROW, value).apply() }

    var accentColor: Int
        get() = preferences.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR)
        set(value) { preferences.edit().putInt(KEY_ACCENT_COLOR, value).apply() }

    var heightScale: Float
        get() = preferences.getFloat(KEY_HEIGHT_SCALE, 1.0f)
        set(value) { preferences.edit().putFloat(KEY_HEIGHT_SCALE, value).apply() }

    var wordFlow: Boolean
        get() = preferences.getBoolean(KEY_WORDFLOW, true)
        set(value) { preferences.edit().putBoolean(KEY_WORDFLOW, value).apply() }

    var keyPopup: Boolean
        get() = preferences.getBoolean(KEY_SHOW_KEY_POPUP, true)
        set(value) { preferences.edit().putBoolean(KEY_SHOW_KEY_POPUP, value).apply() }

    var language: String
        get() = preferences.getString(KEY_LANGUAGE, "es") ?: "es"
        set(value) { preferences.edit().putString(KEY_LANGUAGE, value).apply() }

    fun exportJson(customWords: List<String> = emptyList()): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "MetroBoard")
        root.put("timestamp", System.currentTimeMillis())

        val wordsArray = JSONArray()
        for (w in customWords) {
            wordsArray.put(w)
        }
        root.put("custom_words", wordsArray)

        val settings = JSONObject()
        settings.put(KEY_SOUND, sound)
        settings.put(KEY_SOUND_VOLUME, soundVolume.toDouble())
        settings.put(KEY_HAPTIC, vibration)
        settings.put(KEY_HAPTIC_STRENGTH, vibrationStrength)
        settings.put(KEY_SUGGESTIONS, suggestions)
        settings.put(KEY_AUTOCORRECT, autoCorrect)
        settings.put(KEY_AUTOCAP, autoCapitalization)
        settings.put(KEY_DOUBLE_SPACE_PERIOD, doubleSpacePeriod)
        settings.put(KEY_SHIFT_WORD_CASING, shiftWordCasing)
        settings.put(KEY_SHOW_NUMBER_ROW, numberRow)
        settings.put(KEY_ACCENT_COLOR, accentColor)
        settings.put(KEY_HEIGHT_SCALE, heightScale.toDouble())
        settings.put(KEY_WORDFLOW, wordFlow)
        settings.put(KEY_SHOW_KEY_POPUP, keyPopup)
        settings.put(KEY_LANGUAGE, language)

        root.put("settings", settings)
        return root.toString(2)
    }

    fun importJson(jsonStr: String): Pair<Int, Int> {
        val root = JSONObject(jsonStr)
        var wordsImported = 0
        var settingsImported = 0

        if (root.has("custom_words")) {
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
            if (settings.has(KEY_SOUND_VOLUME)) {
                editor.putFloat(KEY_SOUND_VOLUME, settings.getDouble(KEY_SOUND_VOLUME).toFloat())
                settingsImported++
            }
            if (settings.has(KEY_HAPTIC_STRENGTH)) {
                editor.putInt(KEY_HAPTIC_STRENGTH, settings.getInt(KEY_HAPTIC_STRENGTH))
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
            if (settings.has(KEY_HEIGHT_SCALE)) {
                editor.putFloat(KEY_HEIGHT_SCALE, settings.getDouble(KEY_HEIGHT_SCALE).toFloat())
                settingsImported++
            }
            if (settings.has(KEY_WORDFLOW)) {
                editor.putBoolean(KEY_WORDFLOW, settings.getBoolean(KEY_WORDFLOW))
                settingsImported++
            }
            if (settings.has(KEY_SHOW_KEY_POPUP)) {
                editor.putBoolean(KEY_SHOW_KEY_POPUP, settings.getBoolean(KEY_SHOW_KEY_POPUP))
                settingsImported++
            }
            if (settings.has(KEY_LANGUAGE)) {
                editor.putString(KEY_LANGUAGE, settings.getString(KEY_LANGUAGE))
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
            .remove(KEY_HAPTIC_STRENGTH)
            .remove(KEY_SUGGESTIONS).remove(KEY_LEGACY_SUGGESTIONS)
            .remove(KEY_AUTOCORRECT)
            .remove(KEY_AUTOCAP)
            .remove(KEY_DOUBLE_SPACE_PERIOD)
            .remove(KEY_SHIFT_WORD_CASING)
            .remove(KEY_SHOW_NUMBER_ROW)
            .remove(KEY_ACCENT_COLOR)
            .remove(KEY_HEIGHT_SCALE)
            .remove(KEY_WORDFLOW)
            .remove(KEY_SHOW_KEY_POPUP)
            .remove(KEY_LANGUAGE)
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
        const val KEY_HAPTIC_STRENGTH = "pref_haptic_strength"
        const val KEY_SUGGESTIONS = "pref_suggestions"
        const val KEY_AUTOCORRECT = "pref_autocorrect"
        const val KEY_AUTOCAP = "pref_autocap"
        const val KEY_DOUBLE_SPACE_PERIOD = "pref_double_space_period"
        const val KEY_SHIFT_WORD_CASING = "pref_shift_word_casing"
        const val KEY_SHOW_NUMBER_ROW = "pref_show_number_row"
        const val KEY_ACCENT_COLOR = "pref_accent_color"
        const val KEY_HEIGHT_SCALE = "pref_height_scale"
        const val KEY_WORDFLOW = "pref_wordflow"
        const val KEY_SHOW_KEY_POPUP = "pref_show_key_popup"
        const val KEY_LANGUAGE = "pref_language"
    }
}
