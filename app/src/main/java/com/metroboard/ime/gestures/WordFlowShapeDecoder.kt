/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.dictionary.CompactWordTrie
import com.metroboard.ime.dictionary.LanguageDictionaryRegistry
import com.metroboard.ime.dictionary.PersonalDictionary
import java.util.Locale
import kotlin.math.hypot

data class SwipeCandidate(
    val word: String,
    val score: Double,
    val shapeSimilarity: Double = 0.0,
    val frequencyScore: Double = 0.0,
    val userBonus: Double = 0.0
)

class KeyHitMap(
    val path: List<GesturePoint>,
    val language: String
) {
    val keyHits: List<Char>

    init {
        val centers = KeyboardGeometry.centers(language)
        val hits = ArrayList<Char>()
        var prevChar = ' '
        for (pt in path) {
            val nearest = KeyboardGeometry.nearestKey(pt.x, pt.y, language) ?: continue
            if (nearest != prevChar) {
                hits.add(nearest)
                prevChar = nearest
            }
        }
        keyHits = hits
    }
}

class WordFlowShapeDecoder(
    private val personalDictionary: PersonalDictionary? = null,
    /**
     * Contexto usado para leer los diccionarios empaquetados en assets.
     * Sin él el decodificador solo puede usar la lista de respaldo de 240
     * palabras, que es lo que pasaba antes: `LanguageDictionaryRegistry.get()`
     * se llamaba sin contexto y por tanto nunca alcanzaba `es.dict`.
     */
    private val context: android.content.Context? = null
) : ShapeWritingDecoder {

    override fun decode(path: List<GesturePoint>): List<String> = decode(path, "es")

    fun decode(path: List<GesturePoint>, language: String = "es"): List<String> {
        if (path.size < 3) return emptyList()

        // 1. Noise reduction and normalization
        val simplified = simplifyPath(path)
        if (simplified.size < 3) return emptyList()

        val normalizedPath = normalizeCoordinates(simplified)
        val resampled = GestureResampler.resample(normalizedPath, 32)
        if (resampled.size < 32) return emptyList()

        val startPoint = resampled.first()
        val endPoint = resampled.last()
        val centers = KeyboardGeometry.centers(language)

        val startKeys = KeyboardGeometry.findKeysNear(startPoint.x, startPoint.y, 0.24f, language)
        val endKeys = KeyboardGeometry.findKeysNear(endPoint.x, endPoint.y, 0.24f, language)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        // 2. Fetch dictionary for active language
        // Never load a dictionary on the UI thread: if it is not ready yet, start loading and decode nothing.
        val ctx = context
        val dict = if (ctx == null) LanguageDictionaryRegistry.get(language, null)
        else LanguageDictionaryRegistry.getIfLoaded(language)
            ?: run { LanguageDictionaryRegistry.preload(language, ctx); return emptyList() }
        val dictWords = dict.getTopWords(500)
        val personalWords = personalDictionary?.wordsWithPrefix("") ?: emptyList()
        val candidatePool = (personalWords + dictWords).distinct()

        val candidates = ArrayList<String>()
        val startSet = startKeys.toSet()
        val endSet = endKeys.toSet()

        for (raw in candidatePool) {
            val clean = cleanWord(raw)
            if (clean.length >= 2 && clean.first() in startSet && clean.last() in endSet) {
                candidates.add(raw)
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val scored = ArrayList<SwipeCandidate>()

        for (candidate in candidates) {
            val clean = cleanWord(candidate)
            val keyPoints = ArrayList<GesturePoint>()
            var prevChar = ' '
            for (ch in clean) {
                if (ch != prevChar) {
                    val center = centers[ch] ?: continue
                    keyPoints.add(GesturePoint(center.x, center.y, 0L))
                    prevChar = ch
                }
            }
            if (keyPoints.size < 2) continue

            // Ideal 32-point polyline for this word
            val idealResampled = GestureResampler.resample(keyPoints, 32)

            // Dynamic Time Warping (DTW) elastic matching
            val dtwDist = dtwDistance(resampled, idealResampled, window = 8) / 32.0

            // Fixed Euclidean average distance for baseline comparison
            var totalEuclidDistance = 0.0
            for (i in 0 until 32) {
                val p1 = resampled[i]
                val p2 = idealResampled[i]
                totalEuclidDistance += hypot(p1.x - p2.x, p1.y - p2.y)
            }
            val avgEuclidDist = totalEuclidDistance / 32.0

            // Combined DTW (60%) + Euclidean (40%) metric
            val effectiveDist = (dtwDist * 0.6) + (avgEuclidDist * 0.4)

            // Letter ordering alignment
            var matchedLetterIdx = 0
            var lastMatchedPathIdx = 0
            for (charIdx in clean.indices) {
                val targetChar = clean[charIdx]
                val center = centers[targetChar] ?: continue
                var found = false
                for (pIdx in lastMatchedPathIdx until resampled.size) {
                    val p = resampled[pIdx]
                    if (hypot(p.x - center.x, p.y - center.y) <= 0.26f) {
                        found = true
                        lastMatchedPathIdx = pIdx
                        break
                    }
                }
                if (found) matchedLetterIdx++
            }

            val completeness = matchedLetterIdx.toDouble() / clean.length.toDouble()
            if (completeness < 0.60 || effectiveDist > 0.35) continue

            val shapeSimilarity = (completeness * 2.2) / (1.0 + effectiveDist * 3.8)
            val freqScore = (dict.getFrequency(candidate).toDouble() / 255.0) * 0.8
            val userBonus = if (personalWords.contains(candidate)) 1.2 else 0.0

            val totalScore = shapeSimilarity + freqScore + userBonus
            scored.add(SwipeCandidate(candidate, totalScore, shapeSimilarity, freqScore, userBonus))
        }

        return scored.sortedByDescending { it.score }
            .map { it.word }
            .distinct()
            .take(3)
    }

    private fun simplifyPath(points: List<GesturePoint>, threshold: Float = 0.015f): List<GesturePoint> {
        if (points.size <= 2) return points
        val result = ArrayList<GesturePoint>()
        result.add(points.first())
        var prev = points.first()

        for (i in 1 until points.size - 1) {
            val curr = points[i]
            val dist = hypot(curr.x - prev.x, curr.y - prev.y)
            if (dist >= threshold) {
                result.add(curr)
                prev = curr
            }
        }
        result.add(points.last())
        return result
    }

    private fun normalizeCoordinates(path: List<GesturePoint>): List<GesturePoint> {
        val maxX = path.maxOf { it.x }
        val maxY = path.maxOf { it.y }

        if (maxX <= 1.5f && maxY <= 1.5f) return path

        val minX = path.minOf { it.x }
        val minY = path.minOf { it.y }
        val width = maxOf(maxX - minX, 1.0f)
        val height = maxOf(maxY - minY, 1.0f)

        return path.map { p ->
            GesturePoint(
                x = (p.x - minX) / width,
                y = (p.y - minY) / height,
                timeMillis = p.timeMillis
            )
        }
    }

    companion object {
        fun cleanWord(raw: String): String = CompactWordTrie.normalize(raw)

        /**
         * Dynamic Time Warping (DTW) distance with Sakoe-Chiba constraint window.
         * Measures elastic similarity between user swipe and ideal word polyline.
         */
        fun dtwDistance(
            seq1: List<GesturePoint>,
            seq2: List<GesturePoint>,
            window: Int = 8
        ): Double {
            val n = seq1.size
            val m = seq2.size
            if (n == 0 || m == 0) return Double.MAX_VALUE

            val w = maxOf(window, kotlin.math.abs(n - m))
            val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
            dtw[0][0] = 0.0

            for (i in 1..n) {
                val p1 = seq1[i - 1]
                val jStart = maxOf(1, i - w)
                val jEnd = minOf(m, i + w)
                for (j in jStart..jEnd) {
                    val p2 = seq2[j - 1]
                    val cost = hypot(p1.x - p2.x, p1.y - p2.y).toDouble()
                    val prevMin = minOf(
                        dtw[i - 1][j],
                        dtw[i][j - 1],
                        dtw[i - 1][j - 1]
                    )
                    dtw[i][j] = cost + prevMin
                }
            }
            return dtw[n][m]
        }
    }
}
