/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The on-device diagnostics: numbers about how fast the keyboard answered, never anything that was typed. */
class LatencyLogTest {
    private val device = LatencyLog.DeviceInfo("Pixel Test", 34, 8, 192, 80)

    @Test fun percentilesAreComputedOverWhatWasRecorded() {
        val log = LatencyLog()
        for (ms in 1L..100L) log.recordKey(queuedMs = ms, handledMs = ms * 2)
        val s = log.keySummary()
        assertEquals(100, s.count)
        assertEquals(50.0, s.queued.median, 1.0)
        assertEquals(95.0, s.queued.p95, 1.0)
        assertEquals(100.0, s.queued.max, 0.001)
        assertEquals(200.0, s.handled.max, 0.001)
    }

    @Test fun onlyTheLastKeysAreKept() {
        val log = LatencyLog(capacity = 50)
        for (ms in 1L..500L) log.recordKey(ms, ms)
        val s = log.keySummary()
        assertEquals(50, s.count)
        assertEquals(500.0, s.queued.max, 0.001)
        assertTrue("old values are dropped", s.queued.median > 400)
    }

    @Test fun anEmptyLogReportsNothingInsteadOfCrashing() {
        val text = LatencyLog().report("0.4.0", device)
        assertTrue(text.contains("No keys recorded yet"))
    }

    @Test fun phasesAreSummarisedByName() {
        val log = LatencyLog()
        for (i in 1..20) { log.recordPhase("suggest", i.toDouble()); log.recordPhase("ipc.before", 1.0) }
        val phases = log.phaseSummaries()
        assertEquals(setOf("suggest", "ipc.before"), phases.keys)
        assertEquals(20.0, phases.getValue("suggest").max, 0.001)
        assertEquals(1.0, phases.getValue("ipc.before").p95, 0.001)
    }

    @Test fun mainThreadStallsAreCountedByHowLongTheyLasted() {
        val log = LatencyLog()
        for (ms in listOf(20L, 55L, 80L, 250L, 1400L)) log.recordStall(ms)
        val stalls = log.stallSummary()
        assertEquals(4, stalls.over50)      // 55, 80, 250 and 1400 (20 is below the 50 ms threshold)
        assertEquals(2, stalls.over200)
        assertEquals(1, stalls.over1000)
        assertEquals(1400L, stalls.longest)
    }

    @Test fun theReportCarriesTheDeviceAndTheNumbersButNoTypedText() {
        val log = LatencyLog()
        log.recordKey(3, 4)
        log.recordField("com.whatsapp", 0x20001, 0x4000000)
        val text = log.report("0.4.0", device)
        assertTrue(text.contains("Pixel Test"))
        assertTrue(text.contains("Android SDK 34"))
        assertTrue(text.contains("0.4.0"))
        assertTrue(text.contains("com.whatsapp"))
        assertTrue(text.contains("0x20001"))
        assertTrue(text.contains("no typed text"))
    }

    @Test fun onlyTheLastFewFieldsAreListedWithoutRepeats() {
        val log = LatencyLog()
        for (i in 1..10) log.recordField("app$i", 1, 0)
        log.recordField("app10", 1, 0)
        val text = log.report("0.4.0", device)
        assertFalse(text.contains("app1 "))
        assertEquals(1, Regex("app10").findAll(text).count())
    }

    @Test fun clearingForgetsEverything() {
        val log = LatencyLog()
        log.recordKey(1, 1); log.recordStall(500); log.recordPhase("x", 1.0); log.recordField("a", 1, 1)
        log.clear()
        assertEquals(0, log.keySummary().count)
        assertEquals(0, log.stallSummary().over200)
        assertTrue(log.phaseSummaries().isEmpty())
    }

    @Test fun dictionaryLoadTimesAreInTheReportBecauseAColdStartIsWhereASlowPhoneShowsIt() {
        val log = LatencyLog()
        log.recordLoad("es", 2310)
        log.recordLoad("en", 1900)
        val text = log.report("0.4.0", device)
        assertTrue(text.contains("Dictionary loads"))
        assertTrue(text.contains("es 2310 ms"))
        assertTrue(text.contains("en 1900 ms"))
    }

    @Test fun anotherLoadOfTheSameLanguageReplacesTheOldNumber() {
        val log = LatencyLog()
        log.recordLoad("es", 5000)
        log.recordLoad("es", 800)
        val text = log.report("0.4.0", device)
        assertTrue(text.contains("es 800 ms"))
        assertFalse(text.contains("5000"))
    }
}
