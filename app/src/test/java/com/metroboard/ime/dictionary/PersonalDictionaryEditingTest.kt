/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The personal dictionary can be edited by hand: add, rename, delete. Words typed by the user are protected at once. */
class PersonalDictionaryEditingTest {
    @get:Rule val folder = TemporaryFolder()

    private fun dictionary() = LocalPersonalDictionary(folder.root)

    @Test fun aWordAddedByHandIsKnownAndSuggestedWithoutHavingBeenTyped() {
        val dict = dictionary()
        assertTrue(dict.addWord("Ameizin", "es"))
        assertTrue(dict.knows("ameizin", "es"))
        assertEquals(listOf("Ameizin"), dict.wordsWithPrefix("ame", "es"))
    }

    @Test fun addedWordsKeepTheCapitalisationTheUserTyped() {
        val dict = dictionary()
        dict.addWord("iPhone", "en")
        assertEquals("iPhone", dict.getAllWords("en").single().word)
    }

    @Test fun addingTheSameWordAgainFixesItsSpellingInsteadOfDuplicatingIt() {
        val dict = dictionary()
        dict.addWord("ameizin", "es")
        assertTrue(dict.addWord("Ameizin", "es"))
        assertEquals(listOf("Ameizin"), dict.getAllWords("es").map { it.word })
    }

    @Test fun wordsThatCannotBeWordsAreRejectedAndNothingIsStored() {
        val dict = dictionary()
        for (bad in listOf("", "  ", "a", "dos palabras", "abc123", "a@b.com", "x".repeat(41))) {
            assertFalse("'$bad' should be rejected", dict.addWord(bad, "es"))
        }
        assertTrue(dict.getAllWords("es").isEmpty())
    }

    @Test fun cleanTrimsAndAcceptsAccentsApostrophesAndHyphens() {
        assertEquals("canción", LocalPersonalDictionary.clean("  canción "))
        assertEquals("l'ami", LocalPersonalDictionary.clean("l'ami"))
        assertEquals("l’ami", LocalPersonalDictionary.clean("l’ami"))
        assertEquals("Jean-Luc", LocalPersonalDictionary.clean("Jean-Luc"))
        assertNull(LocalPersonalDictionary.clean("no valid"))
        assertNull(LocalPersonalDictionary.clean("3d"))
    }

    @Test fun languagesDoNotShareAddedWords() {
        val dict = dictionary()
        dict.addWord("Ameizin", "es")
        assertFalse(dict.knows("ameizin", "en"))
        assertTrue(dict.getAllWords("en").isEmpty())
    }

    @Test fun renamingMovesTheWordAndKeepsItsCount() {
        val dict = dictionary()
        dict.addWord("Ameisin", "es")
        repeat(5) { dict.learn("Ameisin", "es") }
        val before = dict.getAllWords("es").single().frequency
        assertTrue(dict.renameWord("Ameisin", "Ameizin", "es"))
        val after = dict.getAllWords("es").single()
        assertEquals("Ameizin", after.word)
        assertEquals(before, after.frequency)
        assertFalse(dict.knows("ameisin", "es"))
        assertTrue(dict.knows("ameizin", "es"))
    }

    @Test fun renamingOnlyTheCapitalisationReplacesTheSpelling() {
        val dict = dictionary()
        dict.addWord("ameizin", "es")
        assertTrue(dict.renameWord("ameizin", "Ameizin", "es"))
        assertEquals(listOf("Ameizin"), dict.getAllWords("es").map { it.word })
    }

    @Test fun renamingOntoAnExistingWordMergesThemKeepingTheHigherCount() {
        val dict = dictionary()
        dict.addWord("Foo", "es")
        dict.addWord("Bar", "es")
        repeat(7) { dict.learn("Bar", "es") }
        val high = dict.getAllWords("es").first { it.word == "Bar" }.frequency
        assertTrue(dict.renameWord("Foo", "Bar", "es"))
        val words = dict.getAllWords("es")
        assertEquals(listOf("Bar"), words.map { it.word })
        assertEquals(high, words.single().frequency)
    }

    @Test fun renamingAMissingWordOrToAnInvalidOneChangesNothing() {
        val dict = dictionary()
        dict.addWord("Foo", "es")
        assertFalse(dict.renameWord("Nope", "Bar", "es"))
        assertFalse(dict.renameWord("Foo", "two words", "es"))
        assertEquals(listOf("Foo"), dict.getAllWords("es").map { it.word })
    }

    @Test fun editsSurviveRestartingTheApp() {
        val dict = dictionary()
        dict.addWord("Foo", "es")
        dict.addWord("Bar", "es")
        dict.renameWord("Foo", "Baz", "es")
        dict.remove("Bar", "es")
        assertEquals(listOf("Baz"), dictionary().getAllWords("es").map { it.word })
    }

    // Settings and the keyboard service run in one process. Each used to open its own copy of the file, so the
    // keyboard kept the old words in memory and its next save brought back whatever had been deleted or renamed.
    @Test fun settingsAndTheKeyboardShareOneDictionaryPerFolder() {
        val inSettings = LocalPersonalDictionary.shared(folder.root)
        val inKeyboard = LocalPersonalDictionary.shared(folder.root)
        assertTrue(inSettings === inKeyboard)
        inSettings.addWord("Foo", "es")
        inSettings.remove("Foo", "es")
        inKeyboard.learn("Bar", "es")
        assertEquals(listOf("Bar"), LocalPersonalDictionary(folder.root).getAllWords("es").map { it.word })
    }

    @Test fun differentFoldersGetDifferentDictionaries() {
        val other = TemporaryFolder().also { it.create() }
        try {
            assertTrue(LocalPersonalDictionary.shared(folder.root) !== LocalPersonalDictionary.shared(other.root))
        } finally {
            other.delete()
        }
    }
}
