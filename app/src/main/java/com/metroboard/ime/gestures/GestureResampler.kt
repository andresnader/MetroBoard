/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import kotlin.math.hypot

object GestureResampler {
    /**
     * Resamples an irregular stroke path into [targetCount] equidistant points
     * using arc-length parameterization and linear interpolation between segments.
     */
    fun resample(points: List<GesturePoint>, targetCount: Int = 32): List<GesturePoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return List(targetCount) { points[0] }

        // 1. Filter micro-jitter (< 1.0 distance)
        val filtered = ArrayList<GesturePoint>(points.size)
        filtered.add(points[0])
        for (i in 1 until points.size) {
            val prev = filtered.last()
            val curr = points[i]
            if (hypot(curr.x - prev.x, curr.y - prev.y) >= 0.001f) {
                filtered.add(curr)
            }
        }
        if (filtered.size < 2) return List(targetCount) { filtered[0] }

        // 2. Compute segment lengths and total path length
        val segmentLengths = FloatArray(filtered.size - 1)
        var totalLength = 0.0f
        for (i in 0 until filtered.size - 1) {
            val dist = hypot(filtered[i + 1].x - filtered[i].x, filtered[i + 1].y - filtered[i].y)
            segmentLengths[i] = dist
            totalLength += dist
        }

        if (totalLength <= 0.0f) return List(targetCount) { filtered[0] }

        // 3. Resample targetCount equidistant points along the path
        val interval = totalLength / (targetCount - 1).toFloat()
        val result = ArrayList<GesturePoint>(targetCount)
        result.add(filtered.first())

        var accumulatedLength = 0.0f
        var currentSegment = 0

        for (i in 1 until targetCount - 1) {
            val targetDistance = i * interval
            while (currentSegment < segmentLengths.size &&
                accumulatedLength + segmentLengths[currentSegment] < targetDistance
            ) {
                accumulatedLength += segmentLengths[currentSegment]
                currentSegment++
            }

            if (currentSegment >= segmentLengths.size) {
                result.add(filtered.last())
            } else {
                val segLen = segmentLengths[currentSegment]
                val t = if (segLen > 0f) (targetDistance - accumulatedLength) / segLen else 0f
                val p0 = filtered[currentSegment]
                val p1 = filtered[currentSegment + 1]
                val x = p0.x + t * (p1.x - p0.x)
                val y = p0.y + t * (p1.y - p0.y)
                val time = (p0.timeMillis + t * (p1.timeMillis - p0.timeMillis)).toLong()
                result.add(GesturePoint(x, y, time))
            }
        }
        result.add(filtered.last())
        return result
    }
}
