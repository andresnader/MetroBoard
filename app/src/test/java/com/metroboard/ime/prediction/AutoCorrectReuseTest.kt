/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.DictionaryProvider
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The space after a misspelt word asks for its correction on the UI thread. The suggestion thread has already worked
 * it out while the word was typed, so the fuzzy search must not run a second time, and the answer must not change.
 */
class AutoCorrectReuseTest {
    private class Counting(private val inner: DictionaryProvider) : DictionaryProvider by inner {
        val asked = ArrayList<String>()
        override fun autoCorrect(word: String, minFrequency: Int): String? {
            synchronized(asked) { asked += word }
            return inner.autoCorrect(word, minFrequency)
        }
    }

    @Test fun theSpaceReusesTheCorrectionWorkedOutWhileTyping() {
        val dict = Counting(RealDictionaries.get("es"))
        val engine = LocalSuggestionEngine(activeLanguage = "es", dictionaryFor = { dict })
        val reference = LocalSuggestionEngine(activeLanguage = "es", dictionaryFor = { Counting(RealDictionaries.get("es")) })
        for (typo in listOf("tecldao", "hloa", "graicas", "mensjae", "porqeu", "comodo")) {
            val bar = engine.suggest(typo) // suggestion thread, while the word is typed
            val searches = dict.asked.size
            val onSpace = engine.autoCorrect(typo) // UI thread, on the space
            assertEquals("a second search for '$typo'", searches, dict.asked.size)
            assertEquals(reference.autoCorrect(typo), onSpace)
            assertEquals(reference.suggest(typo), bar)
            assertEquals(reference.suggestReplacements(typo), engine.suggestReplacements(typo))
        }
    }

    @Test fun anotherDictionaryNeverReusesAnOldAnswer() {
        var current: DictionaryProvider = Counting(RealDictionaries.get("es"))
        val engine = LocalSuggestionEngine(activeLanguage = "es", dictionaryFor = { current })
        engine.suggest("hloa")
        val english = Counting(RealDictionaries.get("en"))
        current = english // e.g. the user switched language, or the real dictionary replaced the sample
        val expected = LocalSuggestionEngine(activeLanguage = "es", dictionaryFor = { RealDictionaries.get("en") }).autoCorrect("hloa")
        assertEquals(expected, engine.autoCorrect("hloa"))
        assertEquals(listOf("hloa"), english.asked)
    }
}
