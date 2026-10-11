/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import android.content.Context
import com.metroboard.ime.dictionary.DictionaryProvider
import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import com.metroboard.ime.dictionary.LanguageDictionaryRegistry
import com.metroboard.ime.dictionary.PersonalDictionary
import com.metroboard.ime.language.LanguagePacks
import java.util.Locale

class LocalSuggestionEngine(
    private val personal: PersonalDictionary = DisabledPersonalDictionary,
    private val context: Context? = null,
    var activeLanguage: String = "es",
    /** Dictionary source; tests inject the real shipped dictionaries here. */
    private val dictionaryFor: (String) -> DictionaryProvider =
        { language -> LanguageDictionaryRegistry.getNonBlocking(language, context) },
) : SuggestionEngine {

    override fun setLanguage(language: String) {
        activeLanguage = language
    }

    override fun suggest(prefix: String): List<String> = suggest(prefix, activeLanguage)

    /**
     * Candidates for the word being typed, best first (the UI puts the first one in the
     * emphasised centre slot). A word that is already valid is kept verbatim as the top
     * candidate and is never padded with fuzzy look-alikes; fuzzy matching is only used for
     * words the language does not know, and never proposes rare words.
     */
    fun suggest(prefix: String, language: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val typed = prefix.lowercase(Locale.ROOT)
        val dict = dictionaryFor(language)
        val valid = dict.contains(typed) || personal.knows(typed, language)
        val result = LinkedHashSet<String>()

        // 1. A valid, common word is preserved exactly as typed and stays first. A valid but
        //    rare word (e.g. the letter name "cu" while typing "cuando") competes by frequency.
        val strongValid = valid && (personal.knows(typed, language) || dict.getFrequency(typed) >= STRONG_WORD_MIN_FREQUENCY)
        if (strongValid) result.add(typed)

        // 2. Curated typo / contraction fix of the ACTIVE language (es: tambien -> también).
        if (!valid) dict.autoCorrect(typed, AUTOCORRECT_MIN_FREQUENCY)?.let { if (it != typed) result.add(it) }

        // 3. Words the user has kept (language-scoped, used at least twice).
        personal.wordsWithPrefix(typed, language)
            .filter { it.lowercase(Locale.ROOT) != typed }
            .take(2)
            .forEach { result.add(it) }

        // 4. Completions by frequency. Continuations of an already complete word must be common.
        for (w in dict.wordsWithPrefix(typed, 8)) {
            if (result.size >= MAX_CANDIDATES) break
            if (w.lowercase(Locale.ROOT) == typed) continue
            if (valid && dict.getFrequency(w) < VALID_WORD_COMPLETION_MIN_FREQUENCY) continue
            result.add(w)
        }

        if (valid && !strongValid) {
            // Rare-but-valid word: it competes by frequency with the continuations (so "cu" does
            // not outrank cuando/cuenta/cualquier) but is never replaced by autocorrect.
            val typedFreq = dict.getFrequency(typed)
            val at = result.indexOfFirst { dict.getFrequency(it) < typedFreq }.let { if (it < 0) result.size else it }
            val merged = result.toMutableList().apply { add(at, typed) }
            result.clear(); result.addAll(merged)
        }

        // 5. Fuzzy neighbours only for unknown words, and only common ones.
        if (!valid && result.size < MAX_CANDIDATES && typed.length >= 3) {
            for (w in dict.searchFuzzy(typed, 5)) {
                if (result.size >= MAX_CANDIDATES) break
                if (w.lowercase(Locale.ROOT) != typed && dict.getFrequency(w) >= FUZZY_SUGGESTION_MIN_FREQUENCY) {
                    result.add(w)
                }
            }
        }

        return result.take(MAX_CANDIDATES).toList()
    }

    override fun suggestNext(previousWord: String): List<String> = suggestNext(previousWord, activeLanguage)

    fun suggestNext(previousWord: String, language: String): List<String> {
        if (previousWord.isBlank()) return emptyList()
        val dict = dictionaryFor(language)
        return dict.getBigramSuggestions(previousWord).take(3)
    }

    override fun suggestNextTrigram(word1: String, word2: String): List<String> =
        suggestNextTrigram(word1, word2, activeLanguage)

    fun suggestNextTrigram(word1: String, word2: String, language: String): List<String> {
        if (word1.isBlank() || word2.isBlank()) return emptyList()
        val dict = dictionaryFor(language)
        return dict.getTrigramSuggestions(word1, word2).take(3)
    }

    override fun autoCorrect(word: String): String? = autoCorrect(word, activeLanguage)

    fun autoCorrect(word: String, language: String): String? {
        if (word.isBlank()) return null
        val typed = word.lowercase(Locale.ROOT)
        val dict = dictionaryFor(language)

        // NEVER replace a word that is valid in the language or that the user has kept.
        if (dict.contains(typed) || personal.knows(typed, language)) return null

        return dict.autoCorrect(typed, AUTOCORRECT_MIN_FREQUENCY)
    }

    override fun suggestReplacements(word: String): List<String> {
        val typed = word.lowercase(Locale.ROOT)
        val dict = dictionaryFor(activeLanguage)
        val out = LinkedHashSet<String>()
        // 1. A typo fix or a missing accent ("tecldao" -> "teclado", "comodo" -> "cómodo").
        dict.autoCorrect(typed, AUTOCORRECT_MIN_FREQUENCY)?.let { out.add(it) }
        // 2. Close words, most frequent first.
        if (typed.length >= 3) {
            for (w in dict.searchFuzzy(typed, 6)) {
                if (dict.getFrequency(w) >= FUZZY_SUGGESTION_MIN_FREQUENCY) out.add(w)
            }
        }
        out.removeAll { it.equals(word, ignoreCase = true) }
        return out.take(MAX_CANDIDATES)
    }

    override fun learn(word: String, explicit: Boolean) {
        val typed = word.trim()
        if (typed.length < 2 || typed.any { !it.isLetter() && it != '\'' && it != '\u2019' }) return
        // Dictionary words need no learning; only genuinely new words are worth keeping.
        if (dictionaryFor(activeLanguage).contains(typed.lowercase(Locale.ROOT))) return
        if (explicit) personal.keep(typed, activeLanguage) else personal.learn(typed, activeLanguage)
    }

    override val elidesApostrophe: Boolean
        get() = LanguagePacks.of(activeLanguage).elidesApostrophe

    override fun isKnownWord(word: String): Boolean {
        val typed = word.lowercase(Locale.ROOT)
        return dictionaryFor(activeLanguage).contains(typed) || personal.knows(typed, activeLanguage)
    }

    private companion object {
        const val MAX_CANDIDATES = 3

        /**
         * Frequency scale is 1..255, derived from corpus rank by tools/build_dictionaries.py:
         * freq = 255 * (1 - ln(rank) / ln(300000)). 48 is about rank 32,000 of the 60,000 shipped
         * words: fuzzy replacements and suggestions come from that common half, never from the
         * rare tail, so we do not "correct" into obscure words. Recalibrate if the build changes.
         */
        const val AUTOCORRECT_MIN_FREQUENCY = 48
        const val FUZZY_SUGGESTION_MIN_FREQUENCY = 48

        /** A valid word at least this frequent (about the top 3,000) is always kept first. */
        const val STRONG_WORD_MIN_FREQUENCY = 100

        /** Longer words offered after a complete valid word (e.g. "cuando" -> "cuándo") must be common. */
        const val VALID_WORD_COMPLETION_MIN_FREQUENCY = 70
    }
}
