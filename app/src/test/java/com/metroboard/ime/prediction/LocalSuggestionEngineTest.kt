/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import org.junit.Assert.*
import org.junit.Test

class LocalSuggestionEngineTest {
    @Test fun returnsAtMostThreeMatchingWords() {
        val words = LocalSuggestionEngine(activeLanguage = "es").suggest("a")
        assertEquals(3, words.size)
        assertTrue(words.all { it.startsWith("a") })
    }

    @Test fun prefixIsCaseInsensitive() {
        val engine = LocalSuggestionEngine(activeLanguage = "es")
        assertEquals(engine.suggest("ho"), engine.suggest("HO"))
    }

    @Test fun blankPrefixDoesNotSuggest() {
        assertTrue(LocalSuggestionEngine().suggest("").isEmpty())
    }

    @Test fun exactWordIsNotSuggestedAgain() {
        assertFalse(LocalSuggestionEngine(activeLanguage = "es").suggest("hola").contains("hola"))
    }

    @Test fun personalDictionaryStoresNothing() {
        DisabledPersonalDictionary.learn("secreto")
        assertTrue(DisabledPersonalDictionary.wordsWithPrefix("sec").isEmpty())
    }

    @Test fun spanishBigramNextWordSuggestions() {
        val next = LocalSuggestionEngine(activeLanguage = "es").suggestNext("gracias")
        assertTrue(next.contains("por"))
    }

    @Test fun languageSwitchSeparatesLexicons() {
        val engine = LocalSuggestionEngine(activeLanguage = "es")
        assertTrue(engine.suggest("ho").contains("hola"))
        assertFalse(engine.suggest("th").contains("the"))

        engine.setLanguage("en")
        assertTrue(engine.suggest("th").contains("the") || engine.suggest("th").contains("think"))
        assertFalse(engine.suggest("ho").contains("hola"))
    }

    @Test fun contractionAndTypoSuggestionsAreLanguageSpecific() {
        val engine = LocalSuggestionEngine(activeLanguage = "en")
        assertTrue(engine.suggest("dont").contains("don't"))
        assertFalse(engine.suggest("tambien").contains("también"))

        engine.setLanguage("es")
        assertTrue(engine.suggest("tambien").contains("también"))
        assertFalse(engine.suggest("dont").contains("don't"))
    }

    @Test fun autoCorrectUsesActiveLanguageOnly() {
        val engine = LocalSuggestionEngine(activeLanguage = "en")
        assertEquals("the", engine.autoCorrect("teh"))
        assertEquals("don't", engine.autoCorrect("dont"))
        assertNull(engine.autoCorrect("tambien"))

        engine.setLanguage("es")
        assertEquals("también", engine.autoCorrect("tambien"))
        assertNull(engine.autoCorrect("teh"))
        assertNull(engine.autoCorrect("hola"))
    }

    @Test fun proximityFuzzyMatchingOnAdjacentKeys() {
        val suggestions = LocalSuggestionEngine(activeLanguage = "es").suggest("gracuas")
        assertTrue(suggestions.contains("gracias"))
    }

    @Test fun setLanguageThroughTheInterfaceChangesTheActiveLanguage() {
        val engine: SuggestionEngine = LocalSuggestionEngine(activeLanguage = "es")
        engine.setLanguage("fr")
        assertEquals("fr", (engine as LocalSuggestionEngine).activeLanguage)
        assertTrue(engine.suggest("bonj").contains("bonjour"))
    }

    @Test fun frenchAndGermanDoNotInheritSpanishOrEnglishCorrections() {
        val fr = LocalSuggestionEngine(activeLanguage = "fr")
        assertNull(fr.autoCorrect("teh"))
        assertNull(fr.autoCorrect("tambien"))
        assertEquals("c'est", fr.autoCorrect("cest"))
        assertFalse(fr.suggest("dont").contains("don't"))

        val de = LocalSuggestionEngine(activeLanguage = "de")
        assertNull(de.autoCorrect("teh"))
        assertNull(de.autoCorrect("tambien"))
        assertEquals("über", de.autoCorrect("uber"))
    }

    @Test fun nextWordPredictionIsLanguageSpecific() {
        assertTrue(LocalSuggestionEngine(activeLanguage = "es").suggestNext("gracias").contains("por"))
        assertTrue(LocalSuggestionEngine(activeLanguage = "en").suggestNext("thank").contains("you"))
        assertTrue(LocalSuggestionEngine(activeLanguage = "fr").suggestNext("gracias").isEmpty())
        assertTrue(LocalSuggestionEngine(activeLanguage = "en").suggestNext("gracias").isEmpty())
        assertTrue(LocalSuggestionEngine(activeLanguage = "es").suggestNext("thank").isEmpty())
    }

    @Test fun trigramPredictionIsLanguageSpecific() {
        assertTrue(LocalSuggestionEngine(activeLanguage = "es").suggestNextTrigram("muchas", "gracias").contains("por"))
        assertTrue(LocalSuggestionEngine(activeLanguage = "en").suggestNextTrigram("muchas", "gracias").isEmpty())
        assertTrue(LocalSuggestionEngine(activeLanguage = "fr").suggestNextTrigram("thank", "you").isEmpty())
    }

    @Test fun trigramNextWordSuggestions() {
        val engine = LocalSuggestionEngine()
        val next = engine.suggestNextTrigram("muchas", "gracias")
        assertTrue(next.contains("por"))
    }
}
