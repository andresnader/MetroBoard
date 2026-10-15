/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.LocalPersonalDictionary
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersonalLearningTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun engine(lang: String, dir: java.io.File) =
        LocalSuggestionEngine(LocalPersonalDictionary(dir), null, lang, RealDictionaries::get)

    @Test fun aTypoSeenOnceIsNotProtectedSoItIsStillCorrectedNextTime() {
        val es = engine("es", tmp.newFolder())
        es.learn("tecldao") // typed once, left as is
        assertEquals("teclado", es.autoCorrect("tecldao"))
    }

    @Test fun aWordUsedTwiceIsProtectedAndSuggested() {
        val es = engine("es", tmp.newFolder())
        es.learn("Ameizin"); es.learn("Ameizin")
        assertNull(es.autoCorrect("ameizin"))
        assertTrue(es.suggest("ameiz").contains("Ameizin"))
    }

    @Test fun undoingACorrectionIsAnExplicitInsistenceAndProtectsAtOnce() {
        val es = engine("es", tmp.newFolder())
        es.learn("tecldao", explicit = true)
        assertNull(es.autoCorrect("tecldao"))
    }

    @Test fun dictionaryWordsAreNeverStoredAsLearned() {
        val dir = tmp.newFolder()
        val es = engine("es", dir)
        repeat(3) { es.learn("hola") }
        assertTrue(LocalPersonalDictionary(dir).getAllWords("es").isEmpty())
    }

    @Test fun learnedWordsDoNotLeakAcrossLanguages() {
        val dir = tmp.newFolder()
        val es = engine("es", dir)
        es.learn("Ameizin", explicit = true)
        val fr = engine("fr", dir)
        assertFalse(fr.suggest("ameiz").contains("Ameizin"))
        assertFalse(fr.isKnownWord("Ameizin"))
        assertTrue(es.isKnownWord("Ameizin"))
    }

    @Test fun wordsWithDigitsOrSymbolsAreNotLearned() {
        val dir = tmp.newFolder()
        val es = engine("es", dir)
        es.learn("abc123", explicit = true); es.learn("a@b.com", explicit = true); es.learn("x", explicit = true)
        assertTrue(LocalPersonalDictionary(dir).getAllWords("es").isEmpty())
    }
}
