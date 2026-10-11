/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.CompactWordTrie
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Reproducible quality metrics on the shipped dictionaries (seeded, no device needed).
 *
 * Synthetic typos are generated from the most frequent words of each language:
 *   transposition ("teclado" -> "tecldao"), omission ("escribiendo" -> "escrbiendo"),
 *   adjacent-key slip ("hola" -> "hila") and doubled letter ("casa" -> "caasa").
 * A typo that happens to be a valid word is skipped (nobody should correct it).
 *
 * The numbers are printed so they can be copied into docs/qa; the assertions are loose guard rails,
 * not targets. They are results of THIS synthetic benchmark, not user-study accuracy.
 */
class PredictionQualityTest {
    private data class Typo(val kind: String, val original: String, val typed: String)

    private fun typosFor(lang: String, rnd: Random): List<Typo> {
        val dict = RealDictionaries.get(lang)
        val words = dict.getTopWords(8000).filter { w -> w.length >= 5 && w.all { it.isLetter() } && w == w.lowercase() }
        val out = ArrayList<Typo>()
        for (w in words.shuffled(rnd).take(1500)) {
            val i = 1 + rnd.nextInt(w.length - 2)
            val cands = listOf(
                Typo("transposition", w, w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)).takeIf { i + 1 < w.length && w[i] != w[i + 1] },
                Typo("omission", w, w.removeRange(i, i + 1)),
                adjacentSlip(w, i, rnd)?.let { Typo("adjacent-key", w, it) },
                Typo("doubled", w, w.substring(0, i) + w[i] + w.substring(i)),
            ).filterNotNull()
            val pick = cands[rnd.nextInt(cands.size)]
            if (!dict.contains(pick.typed) && pick.typed != pick.original) out += pick
        }
        return out
    }

    private fun adjacentSlip(w: String, i: Int, rnd: Random): String? {
        val near = CompactWordTrie.ADJACENT_KEYS[CompactWordTrie.normalize(w[i].toString())[0]]?.toList() ?: return null
        if (near.isEmpty()) return null
        return w.substring(0, i) + near[rnd.nextInt(near.size)] + w.substring(i + 1)
    }

    @Test fun autocorrectIsConservativeOnSyntheticTypos() {
        println("\n=== Autocorrect on synthetic typos (seed 42, 1500 sampled words/lang) ===")
        println("%-4s %-14s %6s %9s %9s %9s".format("lang", "kind", "n", "fixed%", "wrong%", "left%"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val engine = LocalSuggestionEngine(activeLanguage = lang, dictionaryFor = RealDictionaries::get)
            val typos = typosFor(lang, Random(42))
            var fixedAll = 0; var wrongAll = 0
            for ((kind, group) in typos.groupBy { it.kind }) {
                var fixed = 0; var wrong = 0
                for (t in group) {
                    val r = engine.autoCorrect(t.typed)
                    when {
                        r == null -> Unit
                        r.equals(t.original, ignoreCase = true) -> fixed++
                        else -> wrong++
                    }
                }
                fixedAll += fixed; wrongAll += wrong
                println("%-4s %-14s %6d %8.1f%% %8.1f%% %8.1f%%".format(lang, kind, group.size,
                    100.0 * fixed / group.size, 100.0 * wrong / group.size, 100.0 * (group.size - fixed - wrong) / group.size))
            }
            val n = typos.size
            println("%-4s %-14s %6d %8.1f%% %8.1f%% %8.1f%%".format(lang, "ALL", n, 100.0 * fixedAll / n, 100.0 * wrongAll / n,
                100.0 * (n - fixedAll - wrongAll) / n))
            assertTrue("$lang: wrong autocorrections ${100.0 * wrongAll / n}% must stay small", wrongAll * 100.0 / n <= 4.0)
        }
    }

    @Test fun prefixPredictionTop1AndTop3() {
        println("\n=== Prefix prediction on the 5,000 most frequent words (>= 5 letters) ===")
        println("%-4s %-8s %6s %8s %8s".format("lang", "prefix", "n", "top1%", "top3%"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val engine = LocalSuggestionEngine(activeLanguage = lang, dictionaryFor = RealDictionaries::get)
            val words = RealDictionaries.get(lang).getTopWords(5000).filter { it.length >= 5 && it.all(Char::isLetter) }
            for (len in listOf(3, 4)) {
                var top1 = 0; var top3 = 0; var n = 0
                for (w in words) {
                    val got = engine.suggest(w.take(len).lowercase())
                    n++
                    if (got.firstOrNull().equals(w, ignoreCase = true)) top1++
                    if (got.any { it.equals(w, ignoreCase = true) }) top3++
                }
                println("%-4s %-8d %6d %7.1f%% %7.1f%%".format(lang, len, n, 100.0 * top1 / n, 100.0 * top3 / n))
                if (len == 4) assertTrue("$lang top3@4 too low", top3 * 100.0 / n >= 35.0)
            }
        }
    }

    @Test fun correctlyTypedFrequentWordsAreNeverChangedNorHidden() {
        for (lang in listOf("es", "en", "fr", "de")) {
            val engine = LocalSuggestionEngine(activeLanguage = lang, dictionaryFor = RealDictionaries::get)
            val words = RealDictionaries.get(lang).getTopWords(3000).filter { it.all(Char::isLetter) && it.length >= 2 }
            var changed = 0; var notFirst = 0
            for (w in words) {
                if (engine.autoCorrect(w) != null) changed++
                if (w.length >= 4 && w == w.lowercase()) {
                    val top = engine.suggest(w).firstOrNull()
                    if (top != null && !top.equals(w, ignoreCase = true) && RealDictionaries.get(lang).getFrequency(w) >= 100) notFirst++
                }
            }
            println("$lang: valid words changed by autocorrect: $changed / ${words.size}; strong valid words not shown first: $notFirst")
            assertTrue("$lang: a valid word was auto-corrected", changed == 0)
            assertTrue("$lang: strong valid word was not first ($notFirst)", notFirst == 0)
        }
    }
}
