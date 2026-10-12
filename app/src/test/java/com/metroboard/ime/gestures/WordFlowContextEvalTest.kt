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
import java.io.File
import java.util.Random
import kotlin.math.hypot

/**
 * Does knowing the previous word help? Real word pairs (previous -> next, from the shipped next-word lists), a noisy
 * synthetic swipe of the second word, decoded with and without the first as context. Synthetic like the rest of the
 * swipe quality tests: it models a careful thumb, so absolute numbers are optimistic; the comparison is what counts.
 */
class WordFlowContextEvalTest {
    private fun swipe(word: String, centers: Map<Char, KeyCenter>, rnd: Random, jitter: Float): List<GesturePoint> {
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
        val smooth = dense.indices.map { i ->
            val lo = maxOf(0, i - 2); val hi = minOf(dense.size - 1, i + 2)
            val n = hi - lo + 1
            GesturePoint(dense.subList(lo, hi + 1).sumOf { it.x.toDouble() }.toFloat() / n,
                dense.subList(lo, hi + 1).sumOf { it.y.toDouble() }.toFloat() / n, dense[i].timeMillis)
        }
        return smooth.map { GesturePoint(it.x + rnd.nextGaussian().toFloat() * jitter, it.y + rnd.nextGaussian().toFloat() * jitter, it.timeMillis) }
    }

    private fun pairs(lang: String, limit: Int): List<Pair<String, String>> {
        val assets = listOf("src/main/assets", "app/src/main/assets").map { File(it) }.first { it.exists() }
        val dict = RealDictionaries.get(lang)
        val centers = KeyboardGeometry.centers(lang)
        return File(assets, "dictionaries/$lang.nextwords").readLines().asSequence()
            .map { it.split('\t') }
            .filter { it.size >= 2 }
            .map { it[0] to it[1] }
            .filter { (prev, next) ->
                prev.length >= 2 && next.length >= 3 && dict.contains(next.lowercase()) &&
                    CompactWordTrie.normalize(next).all { it in centers }
            }
            .take(limit).toList()
    }

    private fun evaluate(lang: String, jitter: Float): Triple<Int, Int, Int> {
        val decoder = WordFlowShapeDecoder(dictionaryFor = RealDictionaries::get)
        val centers = KeyboardGeometry.centers(lang)
        var without = 0; var with = 0; var total = 0
        for ((prev, next) in pairs(lang, 400)) {
            val path = swipe(next, centers, Random(next.hashCode().toLong()), jitter)
            if (path.isEmpty()) continue
            total++
            if (decoder.decode(path, lang, null).firstOrNull()?.equals(next, ignoreCase = true) == true) without++
            if (decoder.decode(path, lang, null, previousWord = prev).firstOrNull()?.equals(next, ignoreCase = true) == true) with++
        }
        println("CTXEVAL $lang jitter=$jitter pairs=$total  top1 without context=$without (${100 * without / total}%)  with=$with (${100 * with / total}%)")
        return Triple(total, without, with)
    }

    @Test fun contextHelpsWhenTheSwipeIsSloppyAndNeverHurtsMuch() {
        for (lang in listOf("es", "en", "fr", "de")) {
            for (jitter in listOf(0.03f, 0.05f)) {
                val (total, without, with) = evaluate(lang, jitter)
                // A small loss is possible (the bonus can promote a wrong word), a real one is not acceptable.
                assertTrue("$lang $jitter: $with with context vs $without without of $total", with >= without - total / 100)
            }
        }
    }
}
