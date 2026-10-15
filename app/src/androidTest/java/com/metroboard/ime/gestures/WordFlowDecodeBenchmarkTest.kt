/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroboard.ime.dictionary.CompactWordTrie
import com.metroboard.ime.dictionary.LanguageDictionaryRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random
import kotlin.math.hypot

/**
 * How long Word Flow takes to turn a finished swipe into words on the device (ART), with the shipped dictionaries:
 * the wait between lifting the finger and seeing the word. Synthetic swipes (the generator of WordFlowQualityTest).
 * Results go to logcat (tag MB_BENCH); the bound is a generous guard rail for an emulator, not a promise.
 */
@RunWith(AndroidJUnit4::class)
class WordFlowDecodeBenchmarkTest {
    @Test
    fun decodeTimeOnTheDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        for (lang in listOf("es", "en", "fr", "de")) {
            val dict = LanguageDictionaryRegistry.get(lang, ctx)
            val decoder = WordFlowShapeDecoder(dictionaryFor = { dict })
            val centers = KeyboardGeometry.centers(lang)
            val rnd = Random(11)
            val swipes = dict.getTopWords(3000).filter { usable(centers, it) }.take(200)
                .map { it to swipe(it, centers, rnd) }.filter { it.second.size >= 3 }
            for ((_, path) in swipes) decoder.decode(path, lang) // warm-up (lazy index, JIT)

            val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1
            val times = LongArray(swipes.size)
            var top1 = 0
            for ((i, swipe) in swipes.withIndex()) {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val got = decoder.decode(swipe.second, lang)
                times[i] = SystemClock.elapsedRealtimeNanos() - t0
                if (got.firstOrNull().equals(swipe.first, ignoreCase = true)) top1++
            }
            val gcAfter = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: -1
            times.sort()
            fun ms(p: Double) = times[((times.size - 1) * p).toInt()] / 1e6
            val mean = times.average() / 1e6
            Log.i(
                "MB_BENCH",
                "decode %s n=%d mean=%.2fms p50=%.2fms p95=%.2fms max=%.2fms top1=%.1f%% gcs=%d".format(
                    lang, times.size, mean, ms(0.5), ms(0.95), times.last() / 1e6, 100.0 * top1 / times.size, gcAfter - gcBefore,
                ),
            )
            assertTrue("$lang mean decode ${mean}ms", mean < 1000.0)
        }
    }

    private fun usable(centers: Map<Char, KeyCenter>, w: String): Boolean {
        val norm = CompactWordTrie.normalize(w)
        return w == w.lowercase() && norm.length >= 3 && norm.all { it in centers }
    }

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
        val smooth = dense.indices.map { i ->
            val lo = maxOf(0, i - 2); val hi = minOf(dense.size - 1, i + 2)
            val n = hi - lo + 1
            GesturePoint(dense.subList(lo, hi + 1).sumOf { it.x.toDouble() }.toFloat() / n,
                dense.subList(lo, hi + 1).sumOf { it.y.toDouble() }.toFloat() / n, dense[i].timeMillis)
        }
        return smooth.map { GesturePoint(it.x + rnd.nextGaussian().toFloat() * jitter, it.y + rnd.nextGaussian().toFloat() * jitter, it.timeMillis) }
    }
}
