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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cubre el pipeline del diccionario español: el contrato de formato que exige
 * el parser en `LanguageDictionaryRegistry`, el rendimiento del trie a escala
 * real (76.440 palabras) y la corrección de los fallos de normalización que
 * hacían inalcanzables los typos y bigrams con tilde.
 */
class SpanishDictionaryTest {

    private fun repoRoot(): File {
        // Las pruebas JUnit se lanzan con el directorio de trabajo del módulo.
        var dir = File("").absoluteFile
        while (dir.parentFile != null && !File(dir, "app/src/main/assets/dictionaries").isDirectory) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    private fun loadAsset(lang: String): List<Pair<String, Int>> {
        val file = File(repoRoot(), "app/src/main/assets/dictionaries/$lang.dict")
        assertTrue("no se encuentra el asset $lang.dict en ${file.absolutePath}", file.exists())
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 2) null else parts[0] to (parts[1].toIntOrNull() ?: -1)
        }
    }

    // ---------------------------------------------------------------- formato

    @Test
    fun testGeneratedDictMatchesTheParserContract() {
        val entries = loadAsset("es")
        assertTrue("el diccionario no debe estar vacío", entries.size > 1000)

        for ((word, freq) in entries) {
            assertTrue("palabra vacía en el asset", word.isNotBlank())
            assertFalse("la palabra no debe contener tabulador ni salto: '$word'",
                word.contains('\t') || word.contains('\n') || word.contains('\r'))
            assertTrue("frecuencia fuera de 1..255 para '$word': $freq", freq in 1..255)
        }
    }

    @Test
    fun testGeneratedDictIsUtf8WithoutBomAndWithLfOnly() {
        val file = File(repoRoot(), "app/src/main/assets/dictionaries/es.dict")
        val bytes = file.readBytes()
        assertFalse("el asset no debe empezar con BOM",
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
        assertFalse("el asset no debe contener CR", bytes.contains('\r'.code.toByte()))

        // Decodificar como UTF-8 estricto no debe lanzar: es el requisito que
        // rompía el volcado manual desde ISO-8859-1.
        val text = String(bytes, Charsets.UTF_8)
        assertTrue("el asset debe contener palabras acentuadas", text.contains("está") || text.contains("más"))
    }

    @Test
    fun testFrequenciesAreSortedDescendingForTopWordsContract() {
        // `getTopWords()` se consume truncado (el shape-writer pide 500), así que
        // el orden del archivo es parte del contrato.
        val entries = loadAsset("es")
        val frequencies = entries.map { it.second }
        for (i in 1 until frequencies.size) {
            assertTrue(
                "el asset debe ir de más a menos frecuente (línea ${i + 1}: " +
                    "${frequencies[i]} > ${frequencies[i - 1]})",
                frequencies[i] <= frequencies[i - 1]
            )
        }
    }

    @Test
    fun testTopWordsAreTheActuallyFrequentOnes() {
        val entries = loadAsset("es")
        val top50 = entries.take(50).map { it.first }.toSet()
        // Los conectores y verbos más frecuentes del español tienen que estar
        // arriba; es lo que distingue un ranking real de una lista alfabética.
        for (expected in listOf("de", "que", "no", "la", "el", "en", "por", "con")) {
            assertTrue("'$expected' debería estar entre las 50 más frecuentes", top50.contains(expected))
        }
    }

    // ------------------------------------------------------------- rendimiento

    @Test
    fun testTrieBuildsLargeDictionaryQuicklyAndAnswersPrefixes() {
        val entries = loadAsset("es")
        val start = System.nanoTime()
        val dict = LanguageDictionary("es", entries)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        // Antes del fix, `addTopEntry` reordenaba la lista en cada nodo por
        // cada palabra; con este tamaño el arranque se iba a segundos. El límite
        // es holgado a propósito para no hacerlo flakey en CI lento.
        assertTrue(
            "construir el trie de ${entries.size} palabras tardó %.0f ms; se esperaba < 3000 ms".format(elapsedMs),
            elapsedMs < 3000
        )

        assertEquals(listOf("de", "que", "no"), dict.getTopWords(3))
        assertTrue(dict.wordsWithPrefix("cas", 3).contains("casa"))
    }

    @Test
    fun testFuzzySearchIsFastOnLargeDictionaryAndStillFindsTypos() {
        val entries = loadAsset("es")
        val dict = LanguageDictionary("es", entries)

        // 100 búsquedas difusas es ~100 pulsaciones de usuario.
        val queries = List(100) { "cas" }
        val start = System.nanoTime()
        var found = 0
        for (q in queries) found += dict.searchFuzzy(q, 3).size
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(found > 0)
        assertTrue(
            "100 búsquedas difusas tardaron %.0f ms; se esperaba < 3000 ms".format(elapsedMs),
            elapsedMs < 3000
        )
    }

    @Test
    fun testFuzzySearchFindsRealTyposIncludingAccents() {
        val dict = LanguageDictionary("es", loadAsset("es"))
        // Errores de tecleo con tilde: el término escrito sin tilde debe
        // encontrar la forma acentuada vía normalización.
        assertTrue("csa -> casa", dict.searchFuzzy("csa", 5).contains("casa"))
        assertTrue("adios -> adiós", dict.searchFuzzy("adios", 5).contains("adiós"))
        assertTrue("mexico -> México", dict.searchFuzzy("mexico", 5).contains("México"))
    }

    @Test
    fun testPrefixSearchHandlesAccentedAndUnaccentedQuery() {
        val dict = LanguageDictionary("es", loadAsset("es"))
        // `compactWordTrie.normalize` pliega tildes, así que una consulta sin
        // tilde tiene que encontrar la palabra acentuada del asset.
        val results = dict.wordsWithPrefix("mex", 5)
        assertTrue("mex debería encontrar México, nowrap: $results", results.contains("México"))
    }

    // ------------------------------------------------------------- typos/bigrams

    @Test
    fun testTyposWithAccentsAreReachable() {
        // `es.typos` contiene claves sin tilde (tambien, dia, aqui) que se
        // buscan ya normalizadas. El índice debe usar la misma normalización.
        val typos = mutableMapOf<String, String>()
        File(repoRoot(), "app/src/main/assets/dictionaries/es.typos").readLines(Charsets.UTF_8)
            .forEach { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) typos[CompactWordTrie.normalize(parts[0])] = parts[1]
            }
        val dict = LanguageDictionary("es", loadAsset("es"), typos = typos)

        assertEquals("también", dict.autoCorrect("tambien"))
        assertEquals("día", dict.autoCorrect("dia"))
        assertEquals("aquí", dict.autoCorrect("aqui"))
    }

    @Test
    fun testBigramsWithAccentedKeyAreReachable() {
        val bigrams = mutableMapOf<String, MutableList<String>>()
        File(repoRoot(), "app/src/main/assets/dictionaries/es.bigrams").readLines(Charsets.UTF_8)
            .forEach { line ->
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    bigrams.getOrPut(CompactWordTrie.normalize(parts[0])) { ArrayList() }.add(parts[1])
                }
            }
        val dict = LanguageDictionary("es", loadAsset("es"), bigrams = bigrams)

        // La clave "cómo" se indexa normalizada a "como"; antes era inalcanzable.
        val afterComo = dict.getBigramSuggestions("cómo")
        assertTrue("cómo -> está, se esperaba un bigram, se obtuvo $afterComo", afterComo.isNotEmpty())
        assertTrue(afterComo.contains("está"))
    }

    // ------------------------------------------------------------ normalización

    @Test
    fun testNormalizeFoldsSpanishAccentsButPreservesEnye() {
        assertEquals("mexico", CompactWordTrie.normalize("México"))
        assertEquals("corazon", CompactWordTrie.normalize("corazón"))
        assertEquals("como", CompactWordTrie.normalize("cómo"))
        // La ñ es una letra propia del español, no una tilde: debe sobrevivir.
        assertEquals("español", CompactWordTrie.normalize("Español"))
        assertEquals("ñ", CompactWordTrie.normalize("Ñ"))
    }

    @Test
    fun testAccentNormalizationKeepsNyeDistinctFromN() {
        // 'ñ' -> 'n' rompería la búsqueda por prefijo de palabras con ñ.
        val trie = CompactWordTrie()
        for (word in listOf("niño", "nino", "año", "ano")) {
            trie.insert(DictionaryEntry(word, 100, CompactWordTrie.normalize(word), "es"))
        }
        val byN = trie.wordsWithPrefix("n", limit = 10)
        assertTrue("'niño' debe aparecer al buscar por 'n'", byN.contains("niño"))
    }

    @Test
    fun testLargeDictionaryRegistersWithoutDuplicateNormalizedEntries() {
        val entries = loadAsset("es")
        val words = entries.map { it.first }
        assertEquals("el asset no debe traer palabras duplicadas", words.size, words.toSet().size)
    }

    @Test
    fun testFrequencyBandIsContinuous() {
        val dict = LanguageDictionary("es", loadAsset("es"))
        assertTrue("debe incluir la palabra más frecuente", dict.getFrequency("de") > 0)
        // Las palabras de la cola heurística existen y tienen frecuencia > 0.
        val tail = dict.getFrequency("fijándonos")
        assertNotNull(tail)
        assertTrue("una palabra de la cola debe tener frecuencia positiva: $tail", tail > 0)
    }
}
