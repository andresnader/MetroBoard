/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class LearnedWord(
    val word: String,
    var frequency: Int = 1,
    val language: String = "es"
)

class LocalPersonalDictionary(
    private val storageDir: File? = null
) : PersonalDictionary {

    constructor(context: Context) : this(context.filesDir)

    private val wordMap = HashMap<String, MutableMap<String, LearnedWord>>() // language -> (lowercaseWord -> LearnedWord)

    companion object {
        /** Uses before a learned word is protected from autocorrect and offered as a suggestion. */
        const val MIN_USES_TO_SUGGEST = 2
    }

    init {
        loadFromFile()
    }

    @Synchronized
    override fun wordsWithPrefix(prefix: String): List<String> = wordsWithPrefix(prefix, "es")

    @Synchronized
    override fun words(language: String): List<String> =
        (wordMap[language.lowercase(Locale.ROOT)]?.values ?: emptyList())
            .filter { it.frequency >= MIN_USES_TO_SUGGEST }
            .sortedByDescending { it.frequency }
            .map { it.word }

    @Synchronized
    override fun knows(word: String, language: String): Boolean =
        (wordMap[language.lowercase(Locale.ROOT)]?.get(word.trim().lowercase(Locale.ROOT))?.frequency ?: 0) >= MIN_USES_TO_SUGGEST

    @Synchronized
    override fun wordsWithPrefix(prefix: String, language: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val langMap = wordMap[language.lowercase(Locale.ROOT)] ?: return emptyList()
        val norm = prefix.lowercase(Locale.ROOT)
        return langMap.values
            // A word seen once may be a typo: it is neither suggested nor protected until reused.
            .filter { it.frequency >= MIN_USES_TO_SUGGEST }
            .filter { it.word.lowercase(Locale.ROOT).startsWith(norm) }
            .sortedByDescending { it.frequency }
            .map { it.word }
    }

    @Synchronized
    override fun learn(word: String) = learn(word, "es")

    @Synchronized
    override fun learn(word: String, language: String) {
        val trimmed = word.trim()
        if (trimmed.length < 2 || trimmed.length > 40) return
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap.getOrPut(lang) { HashMap() }
        val key = trimmed.lowercase(Locale.ROOT)

        val existing = langMap[key]
        if (existing != null) {
            existing.frequency++
        } else {
            langMap[key] = LearnedWord(trimmed, frequency = 1, language = lang)
        }
        saveToFile()
    }

    @Synchronized
    fun remove(word: String, language: String): Boolean {
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap[lang] ?: return false
        val removed = langMap.remove(word.lowercase(Locale.ROOT)) != null
        if (removed) saveToFile()
        return removed
    }

    @Synchronized
    fun getAllWords(language: String): List<LearnedWord> {
        val lang = language.lowercase(Locale.ROOT)
        return (wordMap[lang]?.values ?: emptyList()).sortedByDescending { it.frequency }
    }

    @Synchronized
    override fun clear() {
        wordMap.clear()
        saveToFile()
    }

    @Synchronized
    fun clearLanguage(language: String) {
        wordMap.remove(language.lowercase(Locale.ROOT))
        saveToFile()
    }

    @Synchronized
    fun exportJson(language: String): String {
        val lang = language.lowercase(Locale.ROOT)
        val words = getAllWords(lang)
        val root = JSONObject()
        root.put("version", 1)
        root.put("language", lang)
        val array = JSONArray()
        for (item in words) {
            val obj = JSONObject()
            obj.put("word", item.word)
            obj.put("frequency", item.frequency)
            array.put(obj)
        }
        root.put("words", array)
        return root.toString(2)
    }

    @Synchronized
    fun importJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val lang = root.optString("language", "es").lowercase(Locale.ROOT)
        var count = 0
        if (root.has("words")) {
            val array = root.getJSONArray("words")
            val langMap = wordMap.getOrPut(lang) { HashMap() }
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val word = item.getString("word")
                val freq = item.optInt("frequency", 1)
                val key = word.lowercase(Locale.ROOT)
                val existing = langMap[key]
                if (existing != null) {
                    existing.frequency = maxOf(existing.frequency, freq)
                } else {
                    langMap[key] = LearnedWord(word, frequency = freq, language = lang)
                }
                count++
            }
            saveToFile()
        }
        return count
    }

    @Synchronized
    fun importFromSystemUserDictionary(context: Context): Int {
        var count = 0
        try {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                android.provider.UserDictionary.Words.CONTENT_URI,
                arrayOf(
                    android.provider.UserDictionary.Words.WORD,
                    android.provider.UserDictionary.Words.FREQUENCY,
                    android.provider.UserDictionary.Words.LOCALE
                ),
                null,
                null,
                null
            )
            cursor?.use { c ->
                val wordCol = c.getColumnIndex(android.provider.UserDictionary.Words.WORD)
                val freqCol = c.getColumnIndex(android.provider.UserDictionary.Words.FREQUENCY)
                val localeCol = c.getColumnIndex(android.provider.UserDictionary.Words.LOCALE)
                while (c.moveToNext()) {
                    val word = if (wordCol >= 0) c.getString(wordCol) else null
                    if (!word.isNullOrBlank()) {
                        val trimmed = word.trim()
                        if (trimmed.length in 2..40) {
                            val freq = if (freqCol >= 0) c.getInt(freqCol).coerceAtLeast(1) else 1
                            val loc = if (localeCol >= 0) c.getString(localeCol)?.take(2)?.lowercase(Locale.ROOT) else null
                            val lang = if (!loc.isNullOrBlank()) loc else "es"
                            val langMap = wordMap.getOrPut(lang) { HashMap() }
                            val key = trimmed.lowercase(Locale.ROOT)
                            val existing = langMap[key]
                            if (existing != null) {
                                existing.frequency = maxOf(existing.frequency, freq)
                            } else {
                                langMap[key] = LearnedWord(trimmed, frequency = freq, language = lang)
                            }
                            count++
                        }
                    }
                }
                if (count > 0) {
                    saveToFile()
                }
            }
        } catch (_: Exception) {}
        return count
    }

    private fun getFile(): File? {
        if (storageDir == null) return null
        return File(storageDir, "personal_dictionary.json")
    }

    private fun saveToFile() {
        val file = getFile() ?: return
        try {
            val root = JSONObject()
            root.put("version", 1)
            val langObj = JSONObject()
            for ((lang, map) in wordMap) {
                val array = JSONArray()
                for (item in map.values) {
                    val obj = JSONObject()
                    obj.put("word", item.word)
                    obj.put("frequency", item.frequency)
                    array.put(obj)
                }
                langObj.put(lang, array)
            }
            root.put("languages", langObj)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun loadFromFile() {
        val file = getFile() ?: return
        if (!file.exists()) return
        try {
            val text = file.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            if (root.has("languages")) {
                val langObj = root.getJSONObject("languages")
                for (lang in langObj.keys()) {
                    val array = langObj.getJSONArray(lang)
                    val map = wordMap.getOrPut(lang) { HashMap() }
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val word = obj.getString("word")
                        val freq = obj.optInt("frequency", 1)
                        map[word.lowercase(Locale.ROOT)] = LearnedWord(word, freq, lang)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
