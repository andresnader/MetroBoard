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

/** Exercises the engine against the dictionaries that actually ship in the APK. */
class RealSpanishPredictionTest {
    private val es = LocalSuggestionEngine(activeLanguage = "es", dictionaryFor = RealDictionaries::get)

    @Test fun aCorrectlyTypedWordIsPreservedAsTheTopCandidate() {
        for (w in listOf("vamos", "estoy", "tengo", "mañana", "hola", "quiero", "embargo", "cómodo", "porque", "cuando")) {
            assertEquals("'$w' is valid and must stay first", w, es.suggest(w).firstOrNull())
        }
    }

    @Test fun aValidWordIsNeverAccompaniedByFuzzyNeighbours() {
        // Seen in the field: "vamos" -> [gamos, vanos], "tengo" -> [rengo, tentó], "mañana" -> [macana, mazana].
        val bad = mapOf(
            "vamos" to listOf("gamos", "vanos"),
            "tengo" to listOf("rengo", "tentó"),
            "mañana" to listOf("macana", "mazana"),
            "estoy" to listOf("estay"),
        )
        for ((typed, junk) in bad) {
            val got = es.suggest(typed)
            junk.forEach { assertFalse("'$typed' suggested junk '$it': $got", got.contains(it)) }
        }
    }

    @Test fun lowFrequencyLookAlikesAreNotOfferedForAnUnknownWord() {
        // Field report V02: "demoro" -> [decoro, memoro] (both heuristic-band rare words).
        val got = es.suggest("demoro")
        assertFalse(got.toString(), got.contains("decoro"))
        assertFalse(got.toString(), got.contains("memoro"))
    }

    @Test fun prefixSuggestionsAreRankedByFrequencyBestFirst() {
        val emba = es.suggest("emba")
        assertEquals("embargo", emba.first())
        assertTrue(emba.toString(), emba.all { it.startsWith("emba") })
        // Ranking follows the corpus: the list must be non-increasing in frequency.
        val dict = com.metroboard.ime.dictionary.RealDictionaries.get("es")
        assertEquals(emba, emba.sortedByDescending { dict.getFrequency(it) })
        // "cu" is itself a (rare) word, yet the user is clearly typing cuando/cuenta/cualquier.
        val cu = es.suggest("cu")
        assertEquals(cu.toString(), "cuando", cu.first())
        assertTrue(cu.toString(), cu.containsAll(listOf("cuenta", "cualquier")))
    }

    @Test fun wordsThatWereMissingFromTheOldWordListAreNowKnown() {
        // Field report: "gracias", "escribiendo", "demoro", "pingüino" were absent and got "corrected".
        for (w in listOf("gracias", "escribiendo", "demoro", "pingüino", "mañana", "teclado", "mensaje", "hola")) {
            assertEquals("'$w'", w, es.suggest(w).first())
            assertNull("'$w' must not be replaced", es.autoCorrect(w))
        }
    }

    @Test fun typicalTyposOfCommonWordsAreFixed() {
        assertEquals("gracias", es.autoCorrect("gracuas"))
        assertEquals("escribiendo", es.autoCorrect("escrbiendo"))
        assertEquals("mensaje", es.autoCorrect("mnesaje"))
    }

    @Test fun conservativeAutocorrectFixesRealTyposButNeverValidWords() {
        assertEquals("teclado", es.autoCorrect("tecldao"))
        assertEquals("porque", es.autoCorrect("poruqe"))
        assertEquals("entonces", es.autoCorrect("entonses"))
        for (w in listOf("hola", "vamos", "estoy", "mañana", "mas", "esta", "si", "como", "demoro")) {
            assertNull("'$w' must never be replaced", es.autoCorrect(w))
        }
    }

    @Test fun autocorrectNeverTargetsRareWords() {
        // Every candidate for this typo is in the low-frequency heuristic band.
        assertNull(es.autoCorrect("zorroastrismo"))
    }
}
