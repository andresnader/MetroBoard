package com.windowsphonekeyboard.ime.prediction

import com.windowsphonekeyboard.ime.dictionary.DisabledPersonalDictionary
import org.junit.Assert.*
import org.junit.Test

class LocalSuggestionEngineTest {
    @Test fun returnsAtMostThreeMatchingWords() {
        val words = LocalSuggestionEngine().suggest("a")
        assertEquals(3, words.size)
        assertTrue(words.all { it.startsWith("a") })
    }
    @Test fun prefixIsCaseInsensitive() {
        assertEquals(LocalSuggestionEngine().suggest("ho"), LocalSuggestionEngine().suggest("HO"))
    }
    @Test fun blankPrefixDoesNotSuggest() { assertTrue(LocalSuggestionEngine().suggest("").isEmpty()) }
    @Test fun exactWordIsNotSuggestedAgain() {
        assertFalse(LocalSuggestionEngine().suggest("hola").contains("hola"))
    }
    @Test fun personalDictionaryStoresNothing() {
        DisabledPersonalDictionary.learn("secreto")
        assertTrue(DisabledPersonalDictionary.wordsWithPrefix("sec").isEmpty())
    }
    @Test fun bigramNextWordSuggestions() {
        val next = LocalSuggestionEngine().suggestNext("gracias")
        assertTrue(next.contains("por"))
    }
    @Test fun contractionAndTypoSuggestions() {
        val english = LocalSuggestionEngine().suggest("dont")
        assertTrue(english.contains("don't"))
        val spanish = LocalSuggestionEngine().suggest("tambien")
        assertTrue(spanish.contains("también"))
    }
    @Test fun autoCorrectRecognizesTypos() {
        val engine = LocalSuggestionEngine()
        assertEquals("the", engine.autoCorrect("teh"))
        assertEquals("don't", engine.autoCorrect("dont"))
        assertEquals("también", engine.autoCorrect("tambien"))
        assertNull(engine.autoCorrect("hola"))
    }
    @Test fun proximityFuzzyMatchingOnAdjacentKeys() {
        val suggestions = LocalSuggestionEngine().suggest("gracuas")
        assertTrue(suggestions.contains("gracias"))
    }
}
