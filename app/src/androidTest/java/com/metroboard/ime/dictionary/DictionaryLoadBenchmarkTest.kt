/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroboard.ime.prediction.LocalSuggestionEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-load time, heap and per-query latency of the shipped dictionaries through the real
 * AssetManager. Results go to logcat (tag MB_BENCH). The assertions are generous guard rails
 * for an emulator; they are not a promise about any phone.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryLoadBenchmarkTest {
    private fun usedHeapKb(): Long {
        System.gc(); System.runFinalization(); System.gc()
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024
    }

    @Test
    fun loadTimeHeapAndQueryLatency() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        LanguageDictionaryRegistry.clearCache()
        val baseline = usedHeapKb()
        var previous = baseline
        for (lang in listOf("es", "en", "fr", "de")) {
            val t0 = SystemClock.elapsedRealtime()
            val dict = LanguageDictionaryRegistry.get(lang, ctx)
            val loadMs = SystemClock.elapsedRealtime() - t0
            val heap = usedHeapKb()
            val engine = LocalSuggestionEngine(context = ctx, activeLanguage = lang)

            val probes = dict.getTopWords(2000).filter { it.length >= 5 }
            val q0 = SystemClock.elapsedRealtimeNanos()
            var n = 0
            for (w in probes) { engine.suggest(w.take(3)); n++ }
            val suggestUs = (SystemClock.elapsedRealtimeNanos() - q0) / 1000.0 / n

            val typos = probes.take(500).map { it.removeRange(2, 3) }
            val a0 = SystemClock.elapsedRealtimeNanos()
            for (t in typos) engine.autoCorrect(t)
            val correctUs = (SystemClock.elapsedRealtimeNanos() - a0) / 1000.0 / typos.size

            Log.i("MB_BENCH", "%s load=%dms heapDelta=%dKB suggest=%.0fus/query autocorrect(typo)=%.0fus/query"
                .format(lang, loadMs, heap - previous, suggestUs, correctUs))
            previous = heap
            assertTrue("$lang cold load took ${loadMs}ms", loadMs < 8000)
            assertTrue("$lang suggest ${suggestUs}us", suggestUs < 20_000)
        }
        Log.i("MB_BENCH", "total heap for 4 languages = ${previous - baseline} KB")
    }
}
