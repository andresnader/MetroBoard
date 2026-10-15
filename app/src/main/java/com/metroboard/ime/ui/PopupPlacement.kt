/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

/** Where to put a popup window, in the coordinates `PopupWindow.showAtLocation` expects. */
data class PopupPosition(val x: Int, val y: Int)

object PopupPlacement {
    /**
     * Places a popup centred over a key and directly *above* it.
     *
     * Inputs are in screen coordinates; the result is relative to the top-left of the host window
     * ([windowLeft], [windowTop], the window's own screen position). That conversion matters for an IME:
     * its window sits at the bottom of the screen, and `showAtLocation` measures from the window's top, so
     * a raw screen Y lands a whole keyboard-height too low (and the screen then pushes it up against the
     * bottom edge, over the space bar).
     *
     * The popup is a window of its own, so it may rise above the keyboard window; it only has to fit on the
     * *screen*. It goes below the key only when there is genuinely no room above it on the screen.
     */
    fun above(
        anchorLeft: Int, anchorTop: Int, anchorWidth: Int, anchorHeight: Int,
        popupWidth: Int, popupHeight: Int,
        screenWidth: Int, screenHeight: Int,
        gap: Int, margin: Int,
        windowLeft: Int, windowTop: Int,
    ): PopupPosition {
        val idealX = anchorLeft + (anchorWidth - popupWidth) / 2
        val screenX = idealX.coerceIn(margin, (screenWidth - popupWidth - margin).coerceAtLeast(margin))

        val above = anchorTop - popupHeight - gap
        val below = anchorTop + anchorHeight + gap
        val screenY = when {
            above >= margin -> above
            below + popupHeight <= screenHeight - margin -> below
            else -> above.coerceAtLeast(margin)
        }
        return PopupPosition(screenX - windowLeft, screenY - windowTop)
    }
}
