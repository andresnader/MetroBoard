/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The next-word lists built from the Leipzig corpus (tools/build_bigrams.py) work on top of the hand-written pairs. */
class NextWordsTest {
    private val common = mapOf("es" to "el", "en" to "the", "fr" to "le", "de" to "der")

    @Test fun veryCommonWordsNowPredictWhatComesNextInEveryLanguage() {
        for ((lang, word) in common) {
            val next = RealDictionaries.get(lang).getBigramSuggestions(word)
            assertTrue("$lang '$word' -> $next", next.size >= 2)
        }
    }

    @Test fun theCuratedPairsStillComeFirst() {
        // Hand-written conversational pairs outrank the corpus ones.
        assertEquals("por", RealDictionaries.get("es").getBigramSuggestions("gracias").first())
        assertEquals("días", RealDictionaries.get("es").getBigramSuggestions("buenos").first())
    }

    @Test fun lookupIgnoresCaseAndAccents() {
        val es = RealDictionaries.get("es")
        assertEquals(es.getBigramSuggestions("el"), es.getBigramSuggestions("El"))
        assertEquals(es.getBigramSuggestions("después"), es.getBigramSuggestions("despues"))
    }

    @Test fun everySuggestionIsARealWordOfThatLanguageWithoutRepeats() {
        for ((lang, word) in common) {
            val dict = RealDictionaries.get(lang)
            val next = dict.getBigramSuggestions(word)
            assertEquals(next.size, next.map { it.lowercase() }.toSet().size)
            next.forEach { assertTrue("$lang '$it' is not in the dictionary", dict.contains(it.lowercase())) }
        }
    }

    @Test fun mostOfTheCommonVocabularyHasSuggestions() {
        // The old hand-written lists covered about twenty words per language. Of the 1,000 most frequent
        // words, a large share now has next words.
        for (lang in common.keys) {
            val dict = RealDictionaries.get(lang)
            val assets = listOf("src/main/assets", "app/src/main/assets").map { File(it) }.first { it.exists() }
            val top = File(assets, "dictionaries/$lang.dict").readLines().take(1000).map { it.substringBefore('\t') }
            val covered = top.count { dict.getBigramSuggestions(it).isNotEmpty() }
            assertTrue("$lang: only $covered of 1000", covered >= 600)
        }
    }
}
