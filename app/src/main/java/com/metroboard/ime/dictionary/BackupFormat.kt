/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Versioned, bounded JSON backup of the settings and the learned words.
 *
 * The file is user-chosen input (Storage Access Framework), so everything in it is untrusted:
 * size is capped while reading, UTF-8 is decoded strictly, counts and lengths are limited, every
 * setting is whitelisted with its type and range, and anything unexpected is dropped, never stored.
 * It contains no typing history: only settings and the words the user chose to keep.
 *
 * Schema v2: {"version":2,"app":"MetroBoard","settings":{...},"dictionaries":{"es":[{"word","frequency"}]}}
 * Schema v1 (single language: "language" + "words") is still accepted.
 */
object BackupFormat {
    const val VERSION = 2
    const val MAX_BYTES = 1_048_576
    const val MAX_WORDS_PER_LANGUAGE = 20_000
    const val MAX_FREQUENCY = 1_000
    const val MIN_WORD_LENGTH = 2
    const val MAX_WORD_LENGTH = 40
    val LANGUAGES = setOf("es", "en", "fr", "de")

    class InvalidBackup(message: String) : Exception(message)

    data class Backup(
        val version: Int,
        val settings: Map<String, Any>,
        val words: Map<String, List<LearnedWord>>,
    )

    private sealed interface Rule
    private object Bool : Rule
    private class Real(val min: Double, val max: Double) : Rule
    private object AnyInt : Rule
    private class OneOf(val values: Set<String>) : Rule

    /** Setting key -> what is acceptable for it. Keys not listed here are ignored on import. */
    private val SETTINGS: Map<String, Rule> = mapOf(
        "pref_sound" to Bool, "pref_haptic" to Bool, "pref_suggestions" to Bool, "pref_autocorrect" to Bool,
        "pref_autocap" to Bool, "pref_learn_words" to Bool, "pref_double_space_period" to Bool, "pref_shift_word_casing" to Bool,
        "pref_show_number_row" to Bool, "pref_tint_keys" to Bool, "pref_wordflow" to Bool,
        "pref_spacebar_cursor" to Bool, "pref_long_press_space" to Bool, "pref_show_key_popup" to Bool,
        "pref_sound_volume" to Real(0.0, 1.0),
        "pref_height_scale" to Real(0.7, 1.4),
        "pref_accent_color" to AnyInt,
        "pref_haptic_strength_level" to OneOf(setOf("LIGHT", "MEDIUM", "STRONG")),
        "pref_height_mode" to OneOf(setOf("COMPACT", "NORMAL", "TALL")),
        "pref_language" to OneOf(LANGUAGES),
        "pref_layout_override" to OneOf(setOf("AUTO", "QWERTY", "AZERTY", "QWERTZ")),
        "pref_theme" to OneOf(setOf("DARK", "LIGHT", "SYSTEM")),
        "pref_one_handed_mode" to OneOf(setOf("OFF", "LEFT", "RIGHT")),
        "pref_long_press_delay" to OneOf(setOf("SHORT", "NORMAL", "LONG", "VERY_LONG")),
        "pref_backspace_delay" to OneOf(setOf("SHORT", "NORMAL", "LONG")),
        "pref_backspace_speed" to OneOf(setOf("SLOW", "NORMAL", "FAST")),
    )

    fun write(settings: Map<String, Any>, dictionaries: Map<String, List<LearnedWord>>): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("app", "MetroBoard")
        val s = JSONObject()
        for ((k, v) in settings) if (k in SETTINGS) s.put(k, v)
        root.put("settings", s)
        val d = JSONObject()
        for ((lang, words) in dictionaries) {
            if (lang !in LANGUAGES || words.isEmpty()) continue
            val arr = JSONArray()
            for (w in words) arr.put(JSONObject().put("word", w.word).put("frequency", w.frequency))
            d.put(lang, arr)
        }
        root.put("dictionaries", d)
        return root.toString(2)
    }

    /** Reads at most [MAX_BYTES] and decodes strict UTF-8; anything else is rejected. */
    fun readBounded(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_BYTES) throw InvalidBackup("file larger than $MAX_BYTES bytes")
            out.write(buffer, 0, n)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString()
        } catch (_: CharacterCodingException) {
            throw InvalidBackup("not valid UTF-8")
        }
    }

    fun parse(text: String): Backup {
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            throw InvalidBackup("not a JSON object")
        }
        val version = root.optInt("version", -1)
        if (version !in 1..VERSION) throw InvalidBackup("unsupported version $version")

        val words = LinkedHashMap<String, List<LearnedWord>>()
        try {
            if (root.has("dictionaries")) {
                val dictionaries = root.optJSONObject("dictionaries") ?: throw InvalidBackup("dictionaries must be an object")
                for (lang in dictionaries.keys()) {
                    if (lang !in LANGUAGES) continue
                    val arr = dictionaries.optJSONArray(lang) ?: throw InvalidBackup("$lang must be an array")
                    words[lang] = sanitizeWords(arr, lang)
                }
            } else if (root.has("words")) { // v1: one language at the root
                val lang = root.optString("language", "es").lowercase()
                val arr = root.optJSONArray("words") ?: throw InvalidBackup("words must be an array")
                if (lang in LANGUAGES) words[lang] = sanitizeWords(arr, lang)
            } else if (root.has("custom_words")) { // ChiFlow export: a plain list of strings
                val lang = (root.optJSONObject("settings")?.optString("pref_language").orEmpty().ifEmpty { root.optString("language", "es") }).lowercase()
                val arr = root.optJSONArray("custom_words") ?: throw InvalidBackup("custom_words must be an array")
                val asObjects = JSONArray()
                for (i in 0 until arr.length()) asObjects.put(JSONObject().put("word", arr.optString(i, "")))
                if (lang in LANGUAGES) words[lang] = sanitizeWords(asObjects, lang)
            }
        } catch (e: JSONException) {
            throw InvalidBackup("malformed words: ${e.message}")
        }

        val settings = LinkedHashMap<String, Any>()
        root.optJSONObject("settings")?.let { raw ->
            for ((key, rule) in SETTINGS) {
                if (!raw.has(key)) continue
                sanitizeSetting(raw.opt(key), rule)?.let { settings[key] = it }
            }
        }
        return Backup(version, settings, words)
    }

    private fun sanitizeWords(arr: JSONArray, lang: String): List<LearnedWord> {
        if (arr.length() > MAX_WORDS_PER_LANGUAGE) throw InvalidBackup("more than $MAX_WORDS_PER_LANGUAGE words for $lang")
        val seen = HashSet<String>()
        val out = ArrayList<LearnedWord>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val word = item.optString("word", "").trim()
            if (word.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH) continue
            // Letters, digits and apostrophes only: no spaces, no URLs / e-mail addresses / markup.
            if (!word.all { it.isLetterOrDigit() || it == '\'' || it == '’' }) continue
            if (!seen.add(word.lowercase())) continue
            val freq = item.optInt("frequency", 2).coerceIn(1, MAX_FREQUENCY)
            out += LearnedWord(word, freq, lang)
        }
        return out
    }

    private fun sanitizeSetting(value: Any?, rule: Rule): Any? = when (rule) {
        Bool -> value as? Boolean
        AnyInt -> (value as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toInt()
        is Real -> (value as? Number)?.toDouble()?.takeIf { it.isFinite() }?.coerceIn(rule.min, rule.max)
            ?: if (value is Number) rule.min.takeIf { value.toDouble() == Double.NEGATIVE_INFINITY } else null
        is OneOf -> (value as? String)?.takeIf { it in rule.values }
    }
}
