package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import org.junit.Assert.*
import org.junit.Test

class LocalSuggestionEngineTest {
    @Test fun returnsAtMostThreeMatchingWords() {
        val words = LocalSuggestionEngine(language = "es").suggest("a")
        assertEquals(3, words.size)
        assertTrue(words.all { it.startsWith("a") })
    }

    @Test fun prefixIsCaseInsensitive() {
        val engine = LocalSuggestionEngine(language = "es")
        assertEquals(engine.suggest("ho"), engine.suggest("HO"))
    }

    @Test fun blankPrefixDoesNotSuggest() {
        assertTrue(LocalSuggestionEngine().suggest("").isEmpty())
    }

    @Test fun exactWordIsNotSuggestedAgain() {
        assertFalse(LocalSuggestionEngine(language = "es").suggest("hola").contains("hola"))
    }

    @Test fun personalDictionaryStoresNothing() {
        DisabledPersonalDictionary.learn("secreto")
        assertTrue(DisabledPersonalDictionary.wordsWithPrefix("sec").isEmpty())
    }

    @Test fun spanishBigramNextWordSuggestions() {
        val next = LocalSuggestionEngine(language = "es").suggestNext("gracias")
        assertTrue(next.contains("por"))
    }

    @Test fun languageSwitchSeparatesLexicons() {
        val engine = LocalSuggestionEngine(language = "es")
        assertTrue(engine.suggest("ho").contains("hola"))
        assertFalse(engine.suggest("th").contains("the"))

        engine.setLanguage("en")
        assertTrue(engine.suggest("th").contains("the") || engine.suggest("th").contains("think"))
        assertFalse(engine.suggest("ho").contains("hola"))
    }

    @Test fun contractionAndTypoSuggestionsAreLanguageSpecific() {
        val engine = LocalSuggestionEngine(language = "en")
        assertTrue(engine.suggest("dont").contains("don't"))
        assertFalse(engine.suggest("tambien").contains("también"))

        engine.setLanguage("es")
        assertTrue(engine.suggest("tambien").contains("también"))
        assertFalse(engine.suggest("dont").contains("don't"))
    }

    @Test fun autoCorrectUsesActiveLanguageOnly() {
        val engine = LocalSuggestionEngine(language = "en")
        assertEquals("the", engine.autoCorrect("teh"))
        assertEquals("don't", engine.autoCorrect("dont"))
        assertNull(engine.autoCorrect("tambien"))

        engine.setLanguage("es")
        assertEquals("también", engine.autoCorrect("tambien"))
        assertNull(engine.autoCorrect("teh"))
        assertNull(engine.autoCorrect("hola"))
    }

    @Test fun proximityFuzzyMatchingOnAdjacentKeys() {
        val suggestions = LocalSuggestionEngine(language = "es").suggest("gracuas")
        assertTrue(suggestions.contains("gracias"))
    }
}
