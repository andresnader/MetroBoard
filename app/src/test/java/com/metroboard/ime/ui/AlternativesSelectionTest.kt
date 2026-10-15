/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hold a key, slide onto an accent, lift: the tile under the finger is the one typed. Sliding is only a choice
 * once the finger has left the key and is up in the popup; a finger that never left the key picks nothing.
 */
class AlternativesSelectionTest {
    // Four 106 px tiles with 11 px gaps, popup ending at y = 1840; the key is below it.
    private val lefts = intArrayOf(100, 217, 334, 451)
    private val rights = intArrayOf(206, 323, 440, 557)
    private fun at(x: Float, y: Float, slack: Int = 40) =
        AlternativesSelection.indexAt(x, y, lefts, rights, bottom = 1840, slack = slack)

    @Test fun fingerOverATilePicksIt() {
        assertEquals(0, at(150f, 1770f))
        assertEquals(2, at(380f, 1770f))
        assertEquals(3, at(500f, 1700f))
    }

    @Test fun fingerStillOnTheKeyPicksNothing() {
        // The key sits just under the popup; releasing without having slid up must not type the tile above it.
        assertEquals(-1, at(150f, 1960f))
        assertEquals(-1, at(380f, 1900f))
    }

    @Test fun fingerAbovePopupStillPicksTheTileInThatColumn() {
        // Sliding past the top edge keeps the selection instead of dropping it.
        assertEquals(1, at(270f, 1500f))
    }

    @Test fun gapBetweenTilesPicksTheNearestOne() {
        assertEquals(0, at(209f, 1770f))
        assertEquals(1, at(214f, 1770f))
    }

    @Test fun justOutsideTheEndsStillPicksTheEdgeTileButFarAwayPicksNothing() {
        assertEquals(0, at(80f, 1770f))
        assertEquals(3, at(580f, 1770f))
        assertEquals(-1, at(20f, 1770f))
        assertEquals(-1, at(900f, 1770f))
    }

    @Test fun anEmptyPopupPicksNothing() {
        assertEquals(-1, AlternativesSelection.indexAt(100f, 1770f, IntArray(0), IntArray(0), 1840, 40))
    }
}
