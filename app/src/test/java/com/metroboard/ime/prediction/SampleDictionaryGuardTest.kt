/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.LanguageDictionary
import com.metroboard.ime.dictionary.LocalPersonalDictionary
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * While the real dictionary is still loading in the background the engine only has the tiny built-in
 * sample. It must not act on it: no autocorrection, no suggestions, no learning.
 */
class SampleDictionaryGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sample = LanguageDictionary("es", listOf("hola" to 200, "casa" to 190, "vamos" to 180), isSample = true)

    private fun loading(dir: java.io.File = tmp.newFolder()) =
        LocalSuggestionEngine(LocalPersonalDictionary(dir), null, "es", { sample }, waitForRealDictionary = true)

    @Test fun noAutocorrectWhileOnlyTheSampleIsAvailable() {
        // "vemos" is a valid word the sample does not contain; it must not become "vamos".
        assertNull(loading().autoCorrect("vemos"))
        assertNull(loading().autoCorrect("casaa"))
    }

    @Test fun noSuggestionsWhileOnlyTheSampleIsAvailable() {
        assertTrue(loading().suggest("ho").isEmpty())
        assertTrue(loading().suggestReplacements("hola").isEmpty())
    }

    @Test fun nothingIsLearnedWhileOnlyTheSampleIsAvailable() {
        val dir = tmp.newFolder()
        val engine = loading(dir)
        repeat(3) { engine.learn("tiempo", explicit = true) } // a very common word, "unknown" to the sample
        assertTrue(LocalPersonalDictionary(dir).getAllWords("es").isEmpty())
    }

    @Test fun theRealDictionaryBehavesNormally() {
        val engine = LocalSuggestionEngine(LocalPersonalDictionary(tmp.newFolder()), null, "es", RealDictionaries::get, waitForRealDictionary = true)
        assertEquals("teclado", engine.autoCorrect("tecldao"))
        assertTrue(engine.suggest("hol").isNotEmpty())
    }

    @Test fun withoutAContextTheSampleStillWorksForUnitTests() {
        // Default for tests / headless use: waitForRealDictionary is false when there is no Android Context.
        val engine = LocalSuggestionEngine(activeLanguage = "es")
        assertFalse(engine.suggest("ho").isEmpty())
    }
}
