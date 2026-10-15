/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Experience-suite group D (languages) against the dictionaries that ship in the APK. */
class RealMultilingualPredictionTest {
    private fun engine(lang: String) =
        LocalSuggestionEngine(activeLanguage = lang, dictionaryFor = RealDictionaries::get)

    private val sentences = mapOf(
        "es" to "El pingüino tomó café mientras escribía un mensaje.",
        "en" to "The quick brown fox jumps over the lazy dog.",
        "fr" to "Bonjour, je voudrais écrire un message en français.",
        "de" to "Guten Morgen, ich möchte eine Nachricht schreiben.",
    )

    @Test fun everyWordOfTheReferenceSentencesIsKnownAndNeverReplaced() {
        for ((lang, sentence) in sentences) {
            val e = engine(lang)
            for (w in sentence.split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }) {
                assertTrue("[$lang] '$w' must be a known word", e.isKnownWord(w))
                assertNull("[$lang] '$w' must not be auto-corrected", e.autoCorrect(w))
                assertEquals("[$lang] '$w' kept as the top candidate", w.lowercase(), e.suggest(w).first().lowercase())
            }
        }
    }

    @Test fun realTyposAreFixedInEachLanguage() {
        assertEquals("keyboard", engine("en").autoCorrect("keybaord"))
        assertEquals("the", engine("en").autoCorrect("teh"))
        assertEquals("don't", engine("en").autoCorrect("dont"))
        assertEquals("bonjour", engine("fr").autoCorrect("bonjuor"))
        assertEquals("écrire", engine("fr").autoCorrect("ecrire")) // missing accent is restored
        assertEquals("Nachricht", engine("de").autoCorrect("nachrciht"))
        assertEquals("Tastatur", engine("de").autoCorrect("tastatru"))
        assertEquals("teclado", engine("es").autoCorrect("tecldao"))
        assertEquals("gracias", engine("es").autoCorrect("gracuas"))
    }

    @Test fun suggestionsNeverComeFromAnotherLanguage() {
        assertFalse(engine("en").suggest("hol").contains("hola"))
        assertFalse(engine("es").suggest("hel").contains("hello"))
        assertFalse(engine("fr").suggest("hall").contains("hallo"))
        assertFalse(engine("de").suggest("bonj").contains("bonjour"))
        assertTrue(engine("fr").suggest("bonj").contains("bonjour"))
        assertTrue(engine("de").suggest("nachr").any { it.equals("Nachricht", ignoreCase = true) })
    }

    @Test fun switchingLanguageRepeatedlyKeepsEachLexiconSeparate() {
        val e = engine("es")
        repeat(3) {
            for ((lang, probe) in listOf("es" to "hol", "en" to "hel", "fr" to "bonj", "de" to "hall")) {
                e.setLanguage(lang)
                val got = e.suggest(probe)
                assertTrue("[$lang] '$probe' -> $got", got.isNotEmpty())
                assertTrue("[$lang] wrong language in $got", got.all { RealDictionaries.get(lang).contains(it) })
            }
        }
    }

    @Test fun accentedAndSpecialCharactersSurviveTheRoundTrip() {
        assertTrue(engine("es").suggest("pingu").any { it == "pingüino" })
        // "ñ" is its own letter in Spanish (not an accent), so it is typed, never folded into "n".
        assertTrue(engine("es").suggest("mañ").any { it == "mañana" })
        assertTrue(engine("de").suggest("möch").any { it.startsWith("möch") })
        assertTrue(engine("fr").suggest("fran").any { it == "français" || it.startsWith("fran") })
    }
}
