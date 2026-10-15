/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ui

/** Which accent tile is under the finger while a key is held. Pure arithmetic so it is unit-tested on the JVM. */
object AlternativesSelection {
    /**
     * Index of the tile the finger at ([x], [y]) is choosing, or -1 for none. All values are in one coordinate
     * system (screen pixels). Tile `i` spans `lefts[i]..rights[i]` horizontally; the popup ends at [bottom].
     *
     * - A finger lower than [bottom] + [slack] has not left the key yet, so it picks nothing: releasing in place
     *   must not type the tile that happens to sit above the key.
     * - Above the popup the column still counts, so sliding past the top edge keeps the selection.
     * - Horizontally the nearest tile wins, but only within [slack] of it.
     */
    fun indexAt(x: Float, y: Float, lefts: IntArray, rights: IntArray, bottom: Int, slack: Int): Int {
        if (lefts.isEmpty() || lefts.size != rights.size) return -1
        if (y > bottom + slack) return -1
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in lefts.indices) {
            val distance = maxOf(lefts[i] - x, 0f, x - rights[i])
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return if (bestDistance <= slack) best else -1
    }
}
