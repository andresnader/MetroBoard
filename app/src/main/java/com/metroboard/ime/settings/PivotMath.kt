/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The geometry of a circular pivot, free of Android types so it can be unit-tested on the JVM.
 *
 * The pivot has a single continuous `position` (page 2.4 is 40 % of the way from page 2 to page 3). Pages
 * sit on a ring, so past the last one comes the first again; every other quantity is derived from it.
 */
internal object PivotMath {
    /** A fling faster than this many pages per second turns the page even if the finger travelled little. */
    const val FLING_PAGES_PER_SECOND = 1.2f

    /** Wraps [position] into `[0, count)`. */
    fun normalize(position: Float, count: Int): Float {
        val wrapped = position % count
        return if (wrapped < 0f) wrapped + count else wrapped
    }

    /** The page the pivot is on (or about to settle on) at [position]. */
    fun nearestPage(position: Float, count: Int): Int =
        normalize(position, count).roundToInt() % count

    /**
     * Signed distance in pages from [position] to page [index] along the shorter way round the ring, in
     * `[-count / 2, count / 2)`. Negative means the page is on the left.
     */
    fun offset(index: Int, position: Float, count: Int): Float {
        val half = count / 2f
        var d = (index - position) % count
        if (d >= half) d -= count else if (d < -half) d += count
        return d
    }

    /**
     * Where a drag that began on page [start] should settle, as an un-wrapped page number. A drag moves at
     * most one page; a quick flick in the drag direction turns the page without needing half a screen.
     *
     * @param velocity pages per second, positive when the content is moving towards the *next* page.
     */
    fun settleTarget(start: Int, position: Float, velocity: Float): Int {
        val progress = position - start
        val target = when {
            velocity > FLING_PAGES_PER_SECOND && progress > 0f -> start + 1
            velocity < -FLING_PAGES_PER_SECOND && progress < 0f -> start - 1
            else -> position.roundToInt()
        }
        return target.coerceIn(start - 1, start + 1)
    }

    /**
     * Horizontal position of every header label for [position], measured from the start edge.
     *
     * The ring is laid out starting at the active label, and slides left by the width of that label as the
     * pivot moves to the next page, so the label that has just been passed leaves on the left and
     * reappears at the far end of the ring.
     */
    fun headerOffsets(widths: IntArray, gap: Int, position: Float): FloatArray {
        val count = widths.size
        val wrapped = normalize(position, count)
        val first = floor(wrapped).toInt() % count
        val fraction = wrapped - floor(wrapped)
        val result = FloatArray(count)
        var cursor = -fraction * (widths[first] + gap)
        for (step in 0 until count) {
            val index = (first + step) % count
            result[index] = cursor
            cursor += widths[index] + gap
        }
        return result
    }

    /** How strongly page [index] counts as the active one: 1 when centred, 0 a page or more away. */
    fun activeness(index: Int, position: Float, count: Int): Float =
        (1f - abs(offset(index, position, count))).coerceIn(0f, 1f)
}
