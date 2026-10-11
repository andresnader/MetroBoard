/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionaryAndPersonalDictTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCompactWordTrieBasicOperations() {
        val trie = CompactWordTrie()
        trie.insert(DictionaryEntry("hola", 100, "hola", "es"))
        trie.insert(DictionaryEntry("hoy", 120, "hoy", "es"))
        trie.insert(DictionaryEntry("hombre", 90, "hombre", "es"))
        trie.insert(DictionaryEntry("hospital", 40, "hospital", "es"))

        assertTrue(trie.contains("hola"))
        assertTrue(trie.contains("HOLA"))
        assertFalse(trie.contains("adios"))

        assertEquals(100, trie.getFrequency("hola"))
        assertEquals(120, trie.getFrequency("hoy"))

        val prefixResults = trie.wordsWithPrefix("ho", limit = 3)
        assertEquals(3, prefixResults.size)
        // Highest frequency first: hoy (120), hola (100), hombre (90)
        assertEquals("hoy", prefixResults[0])
        assertEquals("hola", prefixResults[1])
        assertEquals("hombre", prefixResults[2])
    }

    @Test
    fun testLocalPersonalDictionaryLifecycleAndJsonExportImport() {
        val storageDir = tempFolder.newFolder("dict_test")
        val dict = LocalPersonalDictionary(storageDir)

        // Learn words in Spanish
        dict.learn("Ameizin", "es")
        dict.learn("Ameizin", "es") // Increment freq
        dict.learn("Chevere", "es")

        // Learn words in English
        dict.learn("Supercalifragilistic", "en")
        dict.learn("Supercalifragilistic", "en") // suggested from the second use

        // Test language isolation
        val esWords = dict.wordsWithPrefix("ame", "es")
        assertEquals(1, esWords.size)
        assertEquals("Ameizin", esWords[0])

        val enWords = dict.wordsWithPrefix("sup", "en")
        assertEquals(1, enWords.size)
        assertEquals("Supercalifragilistic", enWords[0])

        // English prefix shouldn't find Spanish words
        assertTrue(dict.wordsWithPrefix("ame", "en").isEmpty())

        // Test JSON Export
        val jsonString = dict.exportJson("es")
        val json = JSONObject(jsonString)
        assertEquals(1, json.getInt("version"))
        assertEquals("es", json.getString("language"))
        val wordsArray = json.getJSONArray("words")
        assertEquals(2, wordsArray.length())

        val firstObj = wordsArray.getJSONObject(0)
        assertEquals("Ameizin", firstObj.getString("word"))
        assertEquals(2, firstObj.getInt("frequency"))

        // Create a new instance pointing to same directory to test persistence
        val reloadedDict = LocalPersonalDictionary(storageDir)
        // A word used once may be a typo: neither protected from autocorrect nor suggested.
        assertFalse(reloadedDict.knows("Chevere", "es"))
        assertTrue(reloadedDict.wordsWithPrefix("che", "es").isEmpty())
        reloadedDict.learn("Chevere", "es")
        assertTrue(reloadedDict.knows("Chevere", "es"))
        assertEquals(listOf("Chevere"), reloadedDict.wordsWithPrefix("che", "es"))
        assertFalse(reloadedDict.knows("Chevere", "en"))

        // An explicit insistence (undoing an autocorrection) counts immediately.
        reloadedDict.keep("Quitoo", "es")
        assertTrue(reloadedDict.knows("Quitoo", "es"))

        // Test JSON Import on fresh dictionary
        val freshStorage = tempFolder.newFolder("dict_import_test")
        val freshDict = LocalPersonalDictionary(freshStorage)
        val importedCount = freshDict.importJson(jsonString)
        assertEquals(2, importedCount)
        assertEquals(listOf("Ameizin"), freshDict.wordsWithPrefix("ame", "es"))
    }

    @Test
    fun testLanguageIsolationInRegistry() {
        // Spanish
        val esDict = LanguageDictionaryRegistry.get("es")
        assertEquals("es", esDict.language)
        assertEquals("QWERTY", esDict.defaultLayout)
        assertTrue(esDict.contains("gracias"))
        val esSuggestions = esDict.wordsWithPrefix("gra", 3)
        assertTrue(esSuggestions.contains("gracias"))

        // English
        val enDict = LanguageDictionaryRegistry.get("en")
        assertEquals("en", enDict.language)
        assertEquals("QWERTY", enDict.defaultLayout)
        assertTrue(enDict.contains("great"))
        val enSuggestions = enDict.wordsWithPrefix("gre", 3)
        assertTrue(enSuggestions.contains("great"))

        // French
        val frDict = LanguageDictionaryRegistry.get("fr")
        assertEquals("fr", frDict.language)
        assertEquals("AZERTY", frDict.defaultLayout)
        assertTrue(frDict.contains("merci"))
        val frSuggestions = frDict.wordsWithPrefix("mer", 3)
        assertTrue(frSuggestions.contains("merci"))

        // German
        val deDict = LanguageDictionaryRegistry.get("de")
        assertEquals("de", deDict.language)
        assertEquals("QWERTZ", deDict.defaultLayout)
        assertTrue(deDict.contains("danke"))
        val deSuggestions = deDict.wordsWithPrefix("dan", 3)
        assertTrue(deSuggestions.contains("danke"))
    }

    @Test
    fun testTrigramsInDictionaryRegistry() {
        val esDict = LanguageDictionaryRegistry.get("es")
        val tri1 = esDict.getTrigramSuggestions("muchas", "gracias")
        assertTrue(tri1.contains("por"))

        val tri2 = esDict.getTrigramSuggestions("por", "favor")
        assertTrue(tri2.contains("avísame"))

        val enDict = LanguageDictionaryRegistry.get("en")
        val triEn = enDict.getTrigramSuggestions("thank", "you")
        assertTrue(triEn.contains("very") || triEn.contains("so"))
    }

    @Test
    fun savingIsAtomicAndLeavesNoTemporaryFile() {
        val dir = tempFolder.newFolder("atomic")
        val dict = LocalPersonalDictionary(dir)
        repeat(3) { dict.learn("Ameizin", "es") }
        assertEquals(listOf("personal_dictionary.json"), dir.list()!!.sorted())
        assertEquals(listOf("Ameizin"), LocalPersonalDictionary(dir).wordsWithPrefix("ame", "es"))
    }

    @Test
    fun aCorruptFileDoesNotCrashAndIsRepairedByTheNextSave() {
        val dir = tempFolder.newFolder("corrupt")
        java.io.File(dir, "personal_dictionary.json").writeText("{ this is not json")
        val dict = LocalPersonalDictionary(dir)
        assertTrue(dict.getAllWords("es").isEmpty())
        repeat(2) { dict.learn("Quimeraz", "es") }
        org.json.JSONObject(java.io.File(dir, "personal_dictionary.json").readText()) // parses again
        assertEquals(listOf("Quimeraz"), LocalPersonalDictionary(dir).wordsWithPrefix("quim", "es"))
    }
}
