/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holding Backspace starts gently and speeds up; the user chooses how long to wait and how fast it goes. */
class GestureTimingsTest {
    @Test fun holdingBackspaceStartsSlowerThanItEnds() {
        for (speed in listOf("SLOW", "NORMAL", "FAST")) {
            val first = GestureTimings.backspaceIntervalMs(0, speed)
            val last = GestureTimings.backspaceIntervalMs(50, speed)
            assertTrue("$speed: $first then $last", first > last)
        }
    }

    @Test fun theSpeedChoicesAreOrdered() {
        for (index in listOf(0, 3, 20)) {
            val slow = GestureTimings.backspaceIntervalMs(index, "SLOW")
            val normal = GestureTimings.backspaceIntervalMs(index, "NORMAL")
            val fast = GestureTimings.backspaceIntervalMs(index, "FAST")
            assertTrue("repeat $index: $slow $normal $fast", slow > normal && normal > fast)
        }
    }

    @Test fun theDefaultNeverDeletesFasterThanTheOldFixedSixtyMilliseconds() {
        // The old behaviour repeated every 60 ms from the first repeat, which is what felt like "it deletes too much".
        for (index in 0..100) assertTrue(GestureTimings.backspaceIntervalMs(index, "NORMAL") > 60)
    }

    @Test fun withTheDefaultSettingsTheFirstRepeatsAreNoFasterThanTenPerSecond() {
        // The report asked for about 100 ms between the first repeats.
        for (index in 0..7) assertTrue(GestureTimings.backspaceIntervalMs(index, "NORMAL") >= 100)
    }

    @Test fun unknownValuesFallBackToTheDefaults() {
        assertEquals(GestureTimings.backspaceIntervalMs(2, "NORMAL"), GestureTimings.backspaceIntervalMs(2, "nonsense"))
        assertEquals(350L, GestureTimings.longPressMs("nonsense"))
        assertEquals(400L, GestureTimings.backspaceInitialDelayMs("nonsense"))
    }

    @Test fun longPressDelaysAreOrderedAndInTheRequestedRange() {
        val values = listOf("SHORT", "NORMAL", "LONG", "VERY_LONG").map { GestureTimings.longPressMs(it) }
        assertEquals(values.sorted(), values)
        assertEquals(values.distinct(), values)
        assertTrue(values.first() >= 200L && values.last() <= 700L)
    }

    @Test fun backspaceStartDelaysAreOrdered() {
        val values = listOf("SHORT", "NORMAL", "LONG").map { GestureTimings.backspaceInitialDelayMs(it) }
        assertEquals(values.sorted(), values)
        assertEquals(values.distinct(), values)
    }

    @Test fun caseOfTheSettingDoesNotMatter() {
        assertEquals(GestureTimings.longPressMs("LONG"), GestureTimings.longPressMs("long"))
    }
}
