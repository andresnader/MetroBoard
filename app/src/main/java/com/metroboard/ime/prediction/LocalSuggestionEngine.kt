/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import android.content.Context
import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import com.metroboard.ime.dictionary.LanguageDictionaryRegistry
import com.metroboard.ime.dictionary.PersonalDictionary
import java.util.Locale

class LocalSuggestionEngine(
    private val personal: PersonalDictionary = DisabledPersonalDictionary,
    private val context: Context? = null,
    var activeLanguage: String = "es"
) : SuggestionEngine {

    override fun setLanguage(language: String) {
        activeLanguage = language
    }

    override fun suggest(prefix: String): List<String> = suggest(prefix, activeLanguage)

    fun suggest(prefix: String, language: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase(Locale.ROOT)
        val dict = LanguageDictionaryRegistry.get(language, context)
        val result = LinkedHashSet<String>()

        // 1. Direct typo or contraction match of the ACTIVE language only
        //    (e.g. es: "tambien" -> "también"; en: "dont" -> "don't").
        dict.autoCorrect(normalized)?.let {
            if (it != normalized) result.add(it)
        }

        // 2. Personal dictionary exact prefix matches (highest priority user preference)
        personal.wordsWithPrefix(normalized)
            .filter { it.lowercase(Locale.ROOT) != normalized }
            .take(2)
            .forEach { result.add(it) }

        // 3. Language dictionary prefix matches (ranked by frequency, strictly for active language)
        val dictPrefixMatches = dict.wordsWithPrefix(normalized, 5)
        for (w in dictPrefixMatches) {
            if (result.size >= 3) break
            if (w.lowercase(Locale.ROOT) != normalized) {
                result.add(w)
            }
        }

        // 4. Fuzzy distance with keyboard adjacency (for active language)
        if (result.size < 3 && normalized.length >= 3) {
            val fuzzy = dict.searchFuzzy(normalized, 3)
            for (w in fuzzy) {
                if (result.size >= 3) break
                if (w.lowercase(Locale.ROOT) != normalized) {
                    result.add(w)
                }
            }
        }

        return result.take(3).toList()
    }

    override fun suggestNext(previousWord: String): List<String> = suggestNext(previousWord, activeLanguage)

    fun suggestNext(previousWord: String, language: String): List<String> {
        if (previousWord.isBlank()) return emptyList()
        val dict = LanguageDictionaryRegistry.get(language, context)
        return dict.getBigramSuggestions(previousWord).take(3)
    }

    override fun suggestNextTrigram(word1: String, word2: String): List<String> =
        suggestNextTrigram(word1, word2, activeLanguage)

    fun suggestNextTrigram(word1: String, word2: String, language: String): List<String> {
        if (word1.isBlank() || word2.isBlank()) return emptyList()
        val dict = LanguageDictionaryRegistry.get(language, context)
        return dict.getTrigramSuggestions(word1, word2).take(3)
    }

    override fun autoCorrect(word: String): String? = autoCorrect(word, activeLanguage)

    fun autoCorrect(word: String, language: String): String? {
        if (word.isBlank()) return null
        val dict = LanguageDictionaryRegistry.get(language, context)

        // NEVER autocorrect if word is already valid in language dictionary or personal dictionary
        if (dict.contains(word) || personal.wordsWithPrefix(word).any { it.equals(word, ignoreCase = true) }) {
            return null
        }

        return dict.autoCorrect(word)
    }
}
