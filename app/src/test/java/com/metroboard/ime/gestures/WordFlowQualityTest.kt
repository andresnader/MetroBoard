/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.dictionary.CompactWordTrie
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.hypot

/**
 * Shape-writing accuracy on SYNTHETIC swipes against the real dictionaries. A swipe is the polyline
 * through the key centres of the word, resampled, with corner cutting and gaussian jitter. It models
 * a careful user, not a real thumb: the numbers are a regression benchmark, not a user-study result.
 */
class WordFlowQualityTest {
    private fun decoder() = WordFlowShapeDecoder(dictionaryFor = RealDictionaries::get)

    /** Normalised swipe over [centers] (same coordinate system the decoder expects). */
    private fun swipe(word: String, centers: Map<Char, KeyCenter>, rnd: Random, jitter: Float = 0.02f): List<GesturePoint> {
        val keys = ArrayList<KeyCenter>()
        var prev = ' '
        for (ch in CompactWordTrie.normalize(word)) {
            if (ch == prev) continue
            centers[ch]?.let { keys += it; prev = ch }
        }
        if (keys.size < 2) return emptyList()
        val dense = ArrayList<GesturePoint>()
        var t = 0L
        for (i in 0 until keys.size - 1) {
            val a = keys[i]; val b = keys[i + 1]
            val steps = maxOf(2, (hypot(b.x - a.x, b.y - a.y) / 0.015f).toInt())
            for (s in 0 until steps) {
                val f = s / steps.toFloat()
                dense += GesturePoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, t); t += 8
            }
        }
        dense += GesturePoint(keys.last().x, keys.last().y, t)
        // corner cutting: moving average over 5 samples
        val smooth = dense.indices.map { i ->
            val lo = maxOf(0, i - 2); val hi = minOf(dense.size - 1, i + 2)
            val n = hi - lo + 1
            GesturePoint(dense.subList(lo, hi + 1).sumOf { it.x.toDouble() }.toFloat() / n,
                dense.subList(lo, hi + 1).sumOf { it.y.toDouble() }.toFloat() / n, dense[i].timeMillis)
        }
        return smooth.map { GesturePoint(it.x + rnd.nextGaussian().toFloat() * jitter, it.y + rnd.nextGaussian().toFloat() * jitter, it.timeMillis) }
    }

    private fun usable(lang: String, w: String): Boolean {
        val centers = KeyboardGeometry.centers(lang)
        val norm = CompactWordTrie.normalize(w)
        return w == w.lowercase() && norm.length >= 3 && norm.all { it in centers }
    }

    @Test fun benchmarkTop1Top3AndLatency() {
        println("\n=== Word Flow on synthetic swipes (seed 11, jitter 0.02 of key-board width = 20% of a key) ===")
        println("%-4s %5s %7s %7s %10s %10s".format("lang", "n", "top1%", "top3%", "avg ms", "max ms"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val d = decoder()
            val centers = KeyboardGeometry.centers(lang)
            val words = RealDictionaries.get(lang).getTopWords(3000).filter { usable(lang, it) }.take(1200)
            val rnd = Random(11)
            var top1 = 0; var top3 = 0; var n = 0; var totalNs = 0L; var maxNs = 0L
            for (w in words) {
                val path = swipe(w, centers, rnd)
                if (path.size < 3) continue
                val t0 = System.nanoTime()
                val got = d.decode(path, lang)
                val dt = System.nanoTime() - t0
                totalNs += dt; if (dt > maxNs) maxNs = dt
                n++
                if (got.firstOrNull().equals(w, ignoreCase = true)) top1++
                if (got.any { it.equals(w, ignoreCase = true) }) top3++
            }
            println("%-4s %5d %6.1f%% %6.1f%% %10.2f %10.2f".format(lang, n, 100.0 * top1 / n, 100.0 * top3 / n, totalNs / 1e6 / n, maxNs / 1e6))
        }
    }

    @Test fun theReferenceWordsFromTheExperienceSuite() {
        val ref = mapOf(
            "es" to listOf("hola", "gracias", "mañana", "teclado", "escribir", "cuando", "porque", "mensaje"),
            "en" to listOf("hello", "keyboard", "writing", "world", "thanks", "message"),
            "fr" to listOf("bonjour", "clavier", "écrire", "message", "merci"),
            "de" to listOf("hallo", "tastatur", "schreiben", "nachricht", "danke"),
        )
        println("\n=== Reference words (C01-C08), 20 noisy swipes each ===")
        var failed = 0
        for ((lang, words) in ref) {
            val d = decoder()
            val centers = KeyboardGeometry.centers(lang)
            for (w in words) {
                val rnd = Random(w.hashCode().toLong())
                var t1 = 0; var t3 = 0
                repeat(20) {
                    val got = d.decode(swipe(w, centers, rnd), lang)
                    if (got.firstOrNull().equals(w, ignoreCase = true)) t1++
                    if (got.any { it.equals(w, ignoreCase = true) }) t3++
                }
                println("%-3s %-10s top1 %2d/20  top3 %2d/20".format(lang, w, t1, t3))
                if (t3 < 14) failed++
            }
        }
        assertTrue("$failed reference words below 70% top-3", failed == 0)
    }

    @Test fun robustnessToNoiseAndRejectionOfScribbles() {
        println("\n=== Noise robustness (top-3) and false acceptance of random scribbles ===")
        println("%-4s %12s %12s %12s %16s".format("lang", "jit 0.02", "jit 0.035", "jit 0.05", "scribble->word%"))
        for (lang in listOf("es", "en", "fr", "de")) {
            val d = decoder()
            val centers = KeyboardGeometry.centers(lang)
            val words = RealDictionaries.get(lang).getTopWords(3000).filter { usable(lang, it) }.take(500)
            val cells = ArrayList<String>()
            for (jit in listOf(0.02f, 0.035f, 0.05f)) {
                val rnd = Random(5)
                var t3 = 0; var n = 0
                for (w in words) {
                    val path = swipe(w, centers, rnd, jit)
                    if (path.size < 3) continue
                    n++
                    if (d.decode(path, lang).any { it.equals(w, ignoreCase = true) }) t3++
                }
                cells += "%.1f%%".format(100.0 * t3 / n)
            }
            // random walks that wander over the keyboard: nobody meant a word
            val rnd = Random(99)
            var accepted = 0; val trials = 600
            repeat(trials) {
                var x = rnd.nextFloat(); var y = rnd.nextFloat()
                val pts = ArrayList<GesturePoint>()
                for (i in 0 until 60) {
                    x = (x + (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f)
                    y = (y + (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f)
                    pts += GesturePoint(x, y, i * 8L)
                }
                if (d.decode(pts, lang).isNotEmpty()) accepted++
            }
            println("%-4s %12s %12s %12s %15.1f%%".format(lang, cells[0], cells[1], cells[2], 100.0 * accepted / trials))
            assertTrue("$lang: ${100.0 * accepted / trials}% of random scribbles became a word", accepted * 100.0 / trials <= 8.0)
            assertTrue("$lang: clean swipes must still be recognised (${cells[0]})", cells[0].removeSuffix("%").toDouble() >= 96.0)
        }
    }

    @Test fun aSwipeThatNeverLeavesOneKeyProducesNothing() {
        val d = decoder()
        val c = KeyboardGeometry.centers("es")['h']!!
        val rnd = Random(3)
        val path = (0 until 20).map { GesturePoint(c.x + rnd.nextFloat() * 0.01f, c.y + rnd.nextFloat() * 0.01f, it * 8L) }
        assertTrue(d.decode(path, "es").isEmpty())
    }

    @Test fun confidenceDistributionForCalibration() {
        println("\n=== best-candidate geometric 'shape' score: percentiles (p5 p25 p50) ===")
        fun pct(v: List<Double>, q: Double) = v.sorted()[((v.size - 1) * q).toInt()]
        for (lang in listOf("es", "en", "fr", "de")) {
            val d = decoder()
            val centers = KeyboardGeometry.centers(lang)
            val words = RealDictionaries.get(lang).getTopWords(3000).filter { usable(lang, it) }.take(500)
            for (jit in listOf(0.02f, 0.035f, 0.05f)) {
                val rnd = Random(5)
                val v = ArrayList<Double>()
                for (w in words) { val r = d.decodeScored(swipe(w, centers, rnd, jit), lang); if (r.isNotEmpty() && r.first().word.equals(w, true)) v += r.first().shapeSimilarity }
                println("%-3s true swipes jit %.3f (correct top1) p5=%.2f p25=%.2f p50=%.2f n=%d".format(lang, jit, pct(v, .05), pct(v, .25), pct(v, .5), v.size))
            }
            val rnd = Random(99); val sc = ArrayList<Double>()
            repeat(600) {
                var x = rnd.nextFloat(); var y = rnd.nextFloat()
                val pts = ArrayList<GesturePoint>()
                for (i in 0 until 60) { x = (x + (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f); y = (y + (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f); pts += GesturePoint(x, y, i * 8L) }
                val r = d.decodeScored(pts, lang); if (r.isNotEmpty()) sc += r.first().shapeSimilarity
            }
            println("%-3s scribbles with a candidate: n=%d  p50=%.2f p75=%.2f p95=%.2f max=%.2f".format(lang, sc.size, pct(sc, .5), pct(sc, .75), pct(sc, .95), sc.max()))
        }
    }
}
