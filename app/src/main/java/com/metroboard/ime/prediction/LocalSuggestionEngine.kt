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

    override fun suggest(prefix: String): List<String> = suggest(prefix, activeLanguage)

    fun suggest(prefix: String, language: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase(Locale.ROOT)
        val dict = LanguageDictionaryRegistry.get(language, context)
        val result = LinkedHashSet<String>()

        // 1. Direct typo or contraction match (e.g. "tambien" -> "también", "dont" -> "don't")
        val typoMatch = dict.autoCorrect(normalized)
            ?: LanguageDictionaryRegistry.get("en", context).autoCorrect(normalized)
            ?: LanguageDictionaryRegistry.get("es", context).autoCorrect(normalized)
        typoMatch?.let {
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
        val next = dict.getBigramSuggestions(previousWord)
        if (next.isNotEmpty()) return next.take(3)
        // Fallback check in other supported languages for bigrams
        return LanguageDictionaryRegistry.get("es", context).getBigramSuggestions(previousWord).ifEmpty {
            LanguageDictionaryRegistry.get("en", context).getBigramSuggestions(previousWord)
        }.take(3)
    }

    override fun suggestNextTrigram(word1: String, word2: String): List<String> =
        suggestNextTrigram(word1, word2, activeLanguage)

    fun suggestNextTrigram(word1: String, word2: String, language: String): List<String> {
        if (word1.isBlank() || word2.isBlank()) return emptyList()
        val dict = LanguageDictionaryRegistry.get(language, context)
        val next = dict.getTrigramSuggestions(word1, word2)
        if (next.isNotEmpty()) return next.take(3)
        // Fallback check in other supported languages for trigrams
        return LanguageDictionaryRegistry.get("es", context).getTrigramSuggestions(word1, word2).ifEmpty {
            LanguageDictionaryRegistry.get("en", context).getTrigramSuggestions(word1, word2)
        }.take(3)
    }

    override fun autoCorrect(word: String): String? = autoCorrect(word, activeLanguage)

    fun autoCorrect(word: String, language: String): String? {
        if (word.isBlank()) return null
        val dict = LanguageDictionaryRegistry.get(language, context)

        // NEVER autocorrect if word is already valid in language dictionary or personal dictionary
        if (dict.contains(word) || personal.wordsWithPrefix(word).any { it.equals(word, ignoreCase = true) }) {
            return null
        }

        dict.autoCorrect(word)?.let { return it }

        // Fallback for well-known typos across supported languages (e.g. teh -> the, dont -> don't)
        return LanguageDictionaryRegistry.get("en", context).autoCorrect(word)
            ?: LanguageDictionaryRegistry.get("es", context).autoCorrect(word)
    }
}
