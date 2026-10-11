/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import java.util.Locale
import kotlin.math.abs

class LanguageDictionary(
    override val language: String,
    entries: List<Pair<String, Int>>,
    private val typos: Map<String, String> = emptyMap(),
    private val bigrams: Map<String, List<String>> = emptyMap(),
    private val trigrams: Map<String, List<String>> = emptyMap(),
    override val defaultLayout: String = "QWERTY"
) : DictionaryProvider {

    private val trie = CompactWordTrie()
    private val topWords = ArrayList<String>()

    init {
        val loaded = ArrayList<Pair<String, Int>>(entries.size)
        for ((word, freq) in entries) {
            val entry = DictionaryEntry(
                word = word,
                frequency = freq,
                normalized = CompactWordTrie.normalize(word),
                language = language
            )
            trie.insert(entry)
            loaded.add(word to freq)
        }
        // `getTopWords()` se consume truncado (el shape-writer pide 500) y esa
        // lista es su pool de candidatos, así que el orden debe ser por
        // frecuencia y no por orden de archivo: antes devolvía en orden de
        // inserción, lo que solo funcionaba por casualidad porque los assets
        // pequeños venían ordenados a mano. Los diccionarios de respaldo no
        // lo están, y con 76.440 palabras el truncado importa de verdad.
        loaded.sortWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        for ((word, _) in loaded) {
            topWords.add(word)
        }
    }

    override fun getFrequency(word: String): Int = trie.getFrequency(word)

    override fun contains(word: String): Boolean = trie.contains(word)

    override fun wordsWithPrefix(prefix: String, limit: Int): List<String> {
        val matches = trie.wordsWithPrefix(prefix, limit * 2)
        // Ensure exact word is not repeated or filtered as needed
        return matches.take(limit)
    }

    override fun searchFuzzy(word: String, limit: Int): List<String> {
        if (word.length < 3) return emptyList()
        val norm = normalize(word)

        // El barrido del trie acotado sustituye al escaneo de las 76.440
        // palabras con una matriz DP por candidata. Ver CompactWordTrie.collectWithinDistance.
        return trie.collectWithinDistance(norm, FUZZY_TRIE_MARGIN)
            .asSequence()
            .filter { abs(it.length - word.length) <= 1 }
            .map { candidate -> candidate to proximityDamerauLevenshtein(norm, normalize(candidate)) }
            .filter { (_, dist) -> dist <= FUZZY_MAX_DISTANCE }
            .sortedBy { (cand, dist) -> dist * 100 - (trie.getFrequency(cand).toDouble() * 0.1) }
            .map { it.first }
            .take(limit)
            .toList()
    }

    override fun getTopWords(limit: Int): List<String> = topWords.take(limit)

    override fun getBigramSuggestions(previousWord: String): List<String> {
        val norm = normalize(previousWord)
        return bigrams[norm] ?: emptyList()
    }

    override fun getTrigramSuggestions(word1: String, word2: String): List<String> {
        val key = "${normalize(word1)} ${normalize(word2)}"
        return trigrams[key] ?: emptyList()
    }

    override fun autoCorrect(word: String): String? = autoCorrect(word, 0)

    override fun autoCorrect(word: String, minFrequency: Int): String? {
        if (word.isBlank()) return null
        val norm = normalize(word)

        // 1. Check direct typo / contraction map
        typos[norm]?.let { return it }

        // 2. If already valid word in dictionary, do NOT autocorrect
        if (contains(word)) return null

        // 3. High confidence fuzzy match (distance <= 0.8)
        if (word.length >= 3) {
            val fuzzy = searchFuzzy(word, 1)
            if (fuzzy.isNotEmpty()) {
                val candidate = fuzzy.first()
                val dist = proximityDamerauLevenshtein(norm, normalize(candidate))
                if (dist <= AUTOCORRECT_MAX_DISTANCE && trie.getFrequency(candidate) >= minFrequency) {
                    return candidate
                }
            }
        }
        return null
    }

    override fun normalize(word: String): String = CompactWordTrie.normalize(word)

    companion object {
        /** Distancia máxima (ponderada) para considerar un candidato difuso. */
        private const val FUZZY_MAX_DISTANCE = 1.4

        /**
         * Margen del barrido del trie. El barrido no modela transposiciones,
         * así que se explora con margen 2 y luego se vuelve a puntuar cada
         * candidato con la distancia completa, que sí las cubre. Con margen 1
         * se perderían las transposiciones, que son el error de tecleo más
         * frecuente en teclado.
         */
        private const val FUZZY_TRIE_MARGIN = 2

        /** Distancia máxima para aplicar autocorrección automática. */
        private const val AUTOCORRECT_MAX_DISTANCE = 0.8

        val ADJACENT_KEYS: Map<Char, Set<Char>> get() = CompactWordTrie.ADJACENT_KEYS

        fun proximityDamerauLevenshtein(s1: String, s2: String): Double {
            val len1 = s1.length
            val len2 = s2.length
            val dp = Array(len1 + 1) { DoubleArray(len2 + 1) }

            for (i in 0..len1) dp[i][0] = i.toDouble()
            for (j in 0..len2) dp[0][j] = j.toDouble()

            for (i in 1..len1) {
                val c1 = s1[i - 1]
                for (j in 1..len2) {
                    val c2 = s2[j - 1]
                    val cost = when {
                        c1 == c2 -> 0.0
                        ADJACENT_KEYS[c1]?.contains(c2) == true -> CompactWordTrie.ADJACENT_SUBSTITUTION_COST
                        else -> 1.0
                    }
                    var minCost = minOf(
                        dp[i - 1][j] + 1.0,
                        dp[i][j - 1] + 1.0,
                        dp[i - 1][j - 1] + cost
                    )
                    if (i > 1 && j > 1 && c1 == s2[j - 2] && s1[i - 2] == c2) {
                        minCost = minOf(minCost, dp[i - 2][j - 2] + 0.8)
                    }
                    dp[i][j] = minCost
                }
            }
            return dp[len1][len2]
        }
    }
}
