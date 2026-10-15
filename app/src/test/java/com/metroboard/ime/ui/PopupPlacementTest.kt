/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The accent popup must sit above the key it belongs to, wherever the keyboard window is on the screen. */
class PopupPlacementTest {
    private val screenW = 1080
    private val screenH = 2400

    // The 1080x2400 emulator: the keyboard window starts at y = 1460, the E key is at (226, 1680), 106x143 px.
    private fun place(anchorTop: Int, anchorLeft: Int = 226, windowTop: Int = 1460, popupW: Int = 484) =
        PopupPlacement.above(
            anchorLeft = anchorLeft, anchorTop = anchorTop, anchorWidth = 106, anchorHeight = 143,
            popupWidth = popupW, popupHeight = 143,
            screenWidth = screenW, screenHeight = screenH,
            gap = 11, margin = 11, windowLeft = 0, windowTop = windowTop,
        )

    @Test fun popupIsAboveTheKeyOnTheScreen() {
        val at = place(anchorTop = 1680)
        assertEquals("directly above: key top - popup height - gap", 1680 - 143 - 11, at.y + 1460)
    }

    @Test fun resultIsRelativeToTheKeyboardWindowNotTheScreen() {
        // The bug: a screen Y (about 1526) given to showAtLocation was read from the window top, a whole
        // keyboard-height too low. The relative Y must be small.
        val at = place(anchorTop = 1680)
        assertTrue("relative y should be about 66, was ${at.y}", at.y in 0..200)
    }

    @Test fun topRowKeyStillGetsItsPopupAboveEvenAboveTheKeyboardWindow() {
        // Top row without a number row: the key is 44 dp (about 121 px) under the window top, too close for
        // the 143 px popup to fit inside the window, but there is plenty of room on the screen.
        val at = place(anchorTop = 1460 + 121)
        assertTrue("popup should be above the key", at.y + 1460 + 143 + 11 <= 1460 + 121)
        assertTrue("it rises above the keyboard window", at.y < 0)
    }

    @Test fun popupGoesBelowOnlyWhenTheScreenTopHasNoRoom() {
        val at = place(anchorTop = 100, windowTop = 0)
        assertEquals(100 + 143 + 11, at.y)
    }

    @Test fun popupIsCentredOnTheKeyAndClampedToTheScreen() {
        val centred = place(anchorTop = 1680, anchorLeft = 500)
        assertEquals(500 + (106 - 484) / 2, centred.x)
        assertEquals("clamped at the left margin", 11, place(anchorTop = 1680, anchorLeft = 0).x)
        assertEquals("clamped at the right margin", screenW - 484 - 11, place(anchorTop = 1680, anchorLeft = 1000).x)
    }
}
