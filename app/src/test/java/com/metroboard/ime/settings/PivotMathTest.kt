/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pivot is a ring: swiping past the last section must arrive at the first, and back again. */
class PivotMathTest {
    private val n = 8

    @Test fun positionWrapsInBothDirections() {
        assertEquals(0f, PivotMath.normalize(8f, n), 1e-6f)
        assertEquals(7.5f, PivotMath.normalize(-0.5f, n), 1e-6f)
        assertEquals(1.25f, PivotMath.normalize(9.25f, n), 1e-6f)
    }

    @Test fun lastPageIsFollowedByTheFirstAndFirstByTheLast() {
        assertEquals(0, PivotMath.nearestPage(7.6f, n))
        assertEquals(7, PivotMath.nearestPage(-0.6f, n))
        assertEquals(7, PivotMath.nearestPage(7.4f, n))
    }

    @Test fun offsetTakesTheShorterWayRound() {
        assertEquals(1f, PivotMath.offset(0, 7f, n), 1e-6f)   // first page is one step right of the last
        assertEquals(-1f, PivotMath.offset(7, 0f, n), 1e-6f)  // last page is one step left of the first
        assertEquals(-4f, PivotMath.offset(4, 0f, n), 1e-6f)  // the exact opposite page: a tie, resolved to the left
        assertEquals(3f, PivotMath.offset(3, 0f, n), 1e-6f)
    }

    @Test fun onlyTheCurrentAndOneNeighbourAreVisibleMidDrag() {
        val position = 7.3f // between the last page and the first
        val visible = (0 until n).filter { kotlin.math.abs(PivotMath.offset(it, position, n)) < 1f }
        assertEquals(listOf(0, 7), visible)
    }

    @Test fun dragPastTheEndSettlesOnTheFirstPage() {
        assertEquals(8, PivotMath.settleTarget(start = 7, position = 7.7f, velocity = 0f))
        assertEquals(0, PivotMath.nearestPage(8f, n))
    }

    @Test fun dragBeforeTheStartSettlesOnTheLastPage() {
        assertEquals(-1, PivotMath.settleTarget(start = 0, position = -0.7f, velocity = 0f))
        assertEquals(7, PivotMath.nearestPage(-1f, n))
    }

    @Test fun shortSlowDragReturnsToTheSamePage() {
        assertEquals(3, PivotMath.settleTarget(start = 3, position = 3.3f, velocity = 0.2f))
    }

    @Test fun quickFlickTurnsThePageWithAShortDrag() {
        assertEquals(4, PivotMath.settleTarget(start = 3, position = 3.15f, velocity = 3f))
        assertEquals(2, PivotMath.settleTarget(start = 3, position = 2.85f, velocity = -3f))
    }

    @Test fun flickAgainstTheDragDirectionDoesNotTurnThePage() {
        assertEquals(3, PivotMath.settleTarget(start = 3, position = 3.2f, velocity = -3f))
    }

    @Test fun neverMovesMoreThanOnePage() {
        assertEquals(4, PivotMath.settleTarget(start = 3, position = 5.9f, velocity = 9f))
        assertEquals(2, PivotMath.settleTarget(start = 3, position = 0.1f, velocity = -9f))
    }

    @Test fun headerStartsAtTheActiveLabel() {
        val widths = intArrayOf(100, 80, 120, 90)
        val x = PivotMath.headerOffsets(widths, gap = 10, position = 2f)
        // Ring from page 2: 2, 3, 0, 1.
        assertArrayEquals(floatArrayOf(230f, 340f, 0f, 130f), x, 1e-4f)
    }

    @Test fun headerRingIsContinuousWhenThePivotMovesOnePage() {
        val widths = intArrayOf(100, 80, 120, 90)
        val justBefore = PivotMath.headerOffsets(widths, gap = 10, position = 1.999f)
        val after = PivotMath.headerOffsets(widths, gap = 10, position = 2f)
        // Every label except the one that wraps round to the far end moves by less than a pixel.
        for (index in widths.indices) {
            if (index == 1) continue // page 1 has just left on the left and rejoined the ring at the end
            assertEquals("label $index jumped", justBefore[index], after[index], 0.5f)
        }
    }

    @Test fun labelThatLeavesOnTheLeftIsOffScreenBeforeItWraps() {
        val widths = intArrayOf(100, 80, 120, 90)
        val x = PivotMath.headerOffsets(widths, gap = 10, position = 0.999f)
        assertEquals(-110f, x[0], 0.5f) // fully left of the start edge, so its jump to the end is not seen
    }

    @Test fun headerWrapsAroundTheEndOfTheRing() {
        val widths = intArrayOf(100, 80, 120, 90)
        val x = PivotMath.headerOffsets(widths, gap = 10, position = 3f)
        assertEquals(0f, x[3], 1e-4f)
        assertEquals(100f, x[0], 1e-4f) // the first label comes straight after the last one
    }

    @Test fun activenessPeaksOnTheCurrentPageAndFadesAcrossTheSeam() {
        assertEquals(1f, PivotMath.activeness(7, 7f, n), 1e-6f)
        assertEquals(0.5f, PivotMath.activeness(0, 7.5f, n), 1e-6f)
        assertEquals(0.5f, PivotMath.activeness(7, 7.5f, n), 1e-6f)
        assertEquals(0f, PivotMath.activeness(3, 7f, n), 1e-6f)
    }
}
