/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long the work done on every keystroke takes with the real 60,000-word dictionaries. The keyboard asks
 * for suggestions on the UI thread after each key, so this is the part that could make typing feel slow.
 *
 * These are timings of the algorithms on the machine running the tests (a laptop JVM), not of a phone: a
 * budget check and a regression guard, not a promise about any device. The bound is deliberately loose.
 */
class KeystrokeLatencyTest {
    private fun engine(lang: String) =
        LocalSuggestionEngine(activeLanguage = lang, dictionaryFor = RealDictionaries::get)

    private fun percentile(sorted: LongArray, p: Double) = sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0

    @Test fun suggestionsAfterEachKeystrokeStayWithinAFrameBudget() {
        println("\n=== Per-keystroke suggestions, real dictionaries (JVM on a laptop, not a phone) ===")
        println("%-4s %7s %9s %9s %9s".format("lang", "calls", "p50 ms", "p95 ms", "max ms"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val e = engine(lang)
            e.suggest("a") // first call builds lazy structures; a real user never pays that on a keystroke
            val words = RealDictionaries.get(lang).getTopWords(1500)
            val samples = ArrayList<Long>()
            for (w in words) for (len in 1..minOf(w.length, 8)) {
                val prefix = w.substring(0, len)
                val t0 = System.nanoTime()
                e.suggest(prefix)
                samples += System.nanoTime() - t0
            }
            val sorted = samples.toLongArray().also { it.sort() }
            val p95 = percentile(sorted, 0.95)
            println("%-4s %7d %9.3f %9.3f %9.3f".format(lang, sorted.size, percentile(sorted, 0.5), p95, sorted.last() / 1_000_000.0))
            assertTrue("[$lang] p95 per-keystroke suggestion time $p95 ms is too slow for the UI thread", p95 < 16.0)
        }
    }

    @Test fun autoCorrectOnSpaceStaysWithinAFrameBudget() {
        println("\n=== Auto-correct on space, 1,000 typos per language (JVM on a laptop, not a phone) ===")
        println("%-4s %9s %9s %9s".format("lang", "p50 ms", "p95 ms", "max ms"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val e = engine(lang)
            e.autoCorrect("ab")
            val words = RealDictionaries.get(lang).getTopWords(3000).filter { it.length in 4..10 }.take(1000)
            val samples = words.map { w ->
                val typo = w.substring(0, 1) + w.substring(2, 3) + w.substring(1, 2) + w.substring(3) // swap letters 2 and 3
                val t0 = System.nanoTime()
                e.autoCorrect(typo)
                System.nanoTime() - t0
            }.toLongArray().also { it.sort() }
            val p95 = percentile(samples, 0.95)
            println("%-4s %9.3f %9.3f %9.3f".format(lang, percentile(samples, 0.5), p95, samples.last() / 1_000_000.0))
            assertTrue("[$lang] p95 auto-correct time $p95 ms is too slow", p95 < 16.0)
        }
    }
}
