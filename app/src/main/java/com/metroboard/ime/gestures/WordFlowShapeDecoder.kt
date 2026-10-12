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
    val userBonus: Double = 0.0,
    val contextBonus: Double = 0.0,
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
    personalDictionary: PersonalDictionary? = null,
    /**
     * Contexto usado para leer los diccionarios empaquetados en assets.
     * Sin él el decodificador solo puede usar la lista de respaldo de 240
     * palabras, que es lo que pasaba antes: `LanguageDictionaryRegistry.get()`
     * se llamaba sin contexto y por tanto nunca alcanzaba `es.dict`.
     */
    private val context: android.content.Context? = null,
    /** Test seam: supplies dictionaries directly instead of the Android-backed registry. */
    private val dictionaryFor: ((String) -> com.metroboard.ime.dictionary.DictionaryProvider)? = null,
) : ShapeWritingDecoder {

    /** Learned words are offered to the decoder too; set by the IME. */
    var personalDictionary: PersonalDictionary? = personalDictionary

    override fun decode(path: List<GesturePoint>): List<String> = decode(path, "es")

    /**
     * Gesture Capture -> Resampling -> Geometric Decoding -> Candidate Generation -> Language Ranking.
     *
     * [path] and [centersOverride] must share one coordinate system (the caller normalises both to
     * the bounding box of the letter keys). Without [centersOverride] the static layout maps are used.
     */
    fun decode(
        path: List<GesturePoint>,
        language: String = "es",
        centersOverride: Map<Char, KeyCenter>? = null,
        /** The word typed just before this swipe, if any: words that usually follow it are favoured. */
        previousWord: String? = null,
    ): List<String> {
        val ranked = decodeScored(path, language, centersOverride, previousWord)
        // Confidence gate: a scribble must not turn into a confidently wrong word.
        if (ranked.isEmpty() || ranked.first().shapeSimilarity < MIN_CONFIDENCE) return emptyList()
        return ranked.map { it.word }.distinct().take(3)
    }

    /** Ranked candidates with their scores, before the confidence gate. Exposed for calibration. */
    internal fun decodeScored(
        path: List<GesturePoint>,
        language: String,
        centersOverride: Map<Char, KeyCenter>? = null,
        previousWord: String? = null,
    ): List<SwipeCandidate> {
        if (path.size < 3) return emptyList()
        val centers = centersOverride ?: KeyboardGeometry.centers(language)

        // 1. Noise reduction, normalisation, fixed-length resampling.
        val simplified = simplifyPath(path)
        if (simplified.size < 3) return emptyList()
        val resampled = GestureResampler.resample(normalizeCoordinates(simplified), SAMPLES)
        if (resampled.size < SAMPLES) return emptyList()
        val userLength = polylineLength(resampled)
        if (userLength < MIN_PATH_LENGTH) return emptyList() // too short to be a word

        val startKeys = nearestKeys(centers, resampled.first(), ENDPOINT_KEYS, ENDPOINT_RADIUS)
        val endKeys = nearestKeys(centers, resampled.last(), ENDPOINT_KEYS, ENDPOINT_RADIUS)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        // 2. Dictionary of the active language. Never load one on the UI thread.
        val ctx = context
        val dict = dictionaryFor?.invoke(language) ?: if (ctx == null) LanguageDictionaryRegistry.get(language, null)
        else LanguageDictionaryRegistry.getIfLoaded(language)
            ?: run { LanguageDictionaryRegistry.preload(language, ctx); return emptyList() }

        // 3. Candidate generation over the WHOLE lexicon (not just the most frequent words).
        val startSet = startKeys.toSet()
        val endSet = endKeys.toSet()
        val personalWords = personalDictionary?.words(language).orEmpty().filter {
            val c = cleanWord(it)
            c.length >= 2 && c.first() in startSet && c.last() in endSet
        }
        val personalSet = personalWords.toHashSet()
        val pool = LinkedHashSet<String>(personalWords)
        pool.addAll(dict.wordsByEndpoints(startSet, endSet, MIN_CANDIDATE_FREQUENCY, MAX_POOL))

        // Context: the words that usually follow the previous one. They are always candidates (when their first and
        // last letters fit the swipe) and get a bonus by rank; it only separates shapes that fit about equally.
        val nextRank = HashMap<String, Int>()
        previousWord?.takeIf { it.isNotBlank() }?.let { previous ->
            dict.getBigramSuggestions(previous).forEachIndexed { rank, word -> nextRank.putIfAbsent(word.lowercase(Locale.ROOT), rank) }
        }
        for (word in nextRank.keys) {
            val c = cleanWord(word)
            if (c.length >= 2 && c.first() in startSet && c.last() in endSet) pool.add(word)
        }

        // 4. Geometric scoring with cheap rejections first.
        val scored = ArrayList<SwipeCandidate>()
        for (candidate in pool) {
            val clean = cleanWord(candidate)
            val keyPoints = ArrayList<GesturePoint>(clean.length)
            var prevChar = ' '
            var unknownKey = false
            for (ch in clean) {
                if (ch == prevChar) continue
                val center = centers[ch]
                if (center == null) { unknownKey = true; break }
                keyPoints.add(GesturePoint(center.x, center.y, 0L))
                prevChar = ch
            }
            if (unknownKey || keyPoints.size < 2) continue

            // A word whose ideal path is far longer or shorter than the swipe cannot be the one.
            val idealLength = polylineLength(keyPoints)
            val ratio = idealLength / userLength
            if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) continue

            // Letters must be visited in order along the swipe.
            var matched = 0
            var from = 0
            for (ch in clean) {
                val center = centers[ch] ?: continue
                var hit = -1
                for (i in from until resampled.size) {
                    if (hypot(resampled[i].x - center.x, resampled[i].y - center.y) <= LETTER_RADIUS) { hit = i; break }
                }
                if (hit >= 0) { matched++; from = hit }
            }
            val completeness = matched.toDouble() / clean.length
            if (completeness < MIN_COMPLETENESS) continue

            val ideal = GestureResampler.resample(keyPoints, SAMPLES)
            val dtw = dtwDistance(resampled, ideal, window = 8) / SAMPLES
            var euclid = 0.0
            for (i in 0 until SAMPLES) euclid += hypot(resampled[i].x - ideal[i].x, resampled[i].y - ideal[i].y)
            val effectiveDist = dtw * 0.6 + euclid / SAMPLES * 0.4
            if (effectiveDist > MAX_DISTANCE) continue

            val shape = completeness * 2.2 / (1.0 + effectiveDist * SHAPE_SHARPNESS)
            val freq = dict.getFrequency(candidate).toDouble() / 255.0 * FREQUENCY_WEIGHT
            val user = if (candidate in personalSet) USER_BONUS else 0.0
            val context = nextRank[candidate.lowercase(Locale.ROOT)]?.let { CONTEXT_BONUS.getOrElse(it) { 0.0 } } ?: 0.0
            scored.add(SwipeCandidate(candidate, shape + freq + user + context, shape, freq, user, context))
        }

        return scored.sortedByDescending { it.score }
    }

    private fun polylineLength(points: List<GesturePoint>): Double {
        var len = 0.0
        for (i in 1 until points.size) len += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return len
    }

    private fun nearestKeys(centers: Map<Char, KeyCenter>, p: GesturePoint, count: Int, maxDistance: Float): List<Char> =
        centers.values.map { it.char to hypot(it.x - p.x, it.y - p.y) }
            .filter { it.second <= maxDistance }
            .sortedBy { it.second }
            .take(count)
            .map { it.first }

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
        // Tunables, calibrated with WordFlowQualityTest (synthetic swipes on the real dictionaries).
        const val SAMPLES = 32
        const val MIN_PATH_LENGTH = 0.15            // normalised units; a shorter gesture is a tap/jitter
        const val ENDPOINT_KEYS = 4                 // keys considered for the first/last letter
        const val ENDPOINT_RADIUS = 0.18f
        const val MIN_CANDIDATE_FREQUENCY = 24
        const val MAX_POOL = 6000
        const val MIN_LENGTH_RATIO = 0.55
        const val MAX_LENGTH_RATIO = 1.9
        const val LETTER_RADIUS = 0.16f
        const val MIN_COMPLETENESS = 0.60
        const val MAX_DISTANCE = 0.30
        const val SHAPE_SHARPNESS = 3.8
        const val FREQUENCY_WEIGHT = 0.8
        const val USER_BONUS = 1.2
        /**
         * Bonus for a word that is the 1st, 2nd or 3rd likeliest to follow the previous word. Calibrated against the
         * shape score: a swipe that goes exactly through the neighbouring key (one key width away) scores 0.23 lower
         * (tema/yema), so the bonus stays below that. Context decides between near ties, it never beats a clear swipe.
         */
        val CONTEXT_BONUS = doubleArrayOf(0.15, 0.10, 0.06)
        /**
         * Minimum geometric similarity of the best candidate. Calibrated on synthetic data: correct
         * swipes score p5 >= 1.72 (jitter up to 50% of a key) and random scribbles peak at about 1.63
         * (p95 ~1.45). 1.55 keeps margin on the swipe side for noisier real thumbs.
         */
        const val MIN_CONFIDENCE = 1.55

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
