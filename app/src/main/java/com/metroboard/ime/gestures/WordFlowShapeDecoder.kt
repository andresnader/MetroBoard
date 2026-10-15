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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

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
        val ranked = decodeScored(path, language, centersOverride, previousWord, keep = 3)
        // Confidence gate: a scribble must not turn into a confidently wrong word.
        if (ranked.isEmpty() || ranked.first().shapeSimilarity < MIN_CONFIDENCE) return emptyList()
        return ranked.map { it.word }.distinct().take(3)
    }

    /**
     * Ranked candidates with their scores, before the confidence gate. Exposed for calibration. With [keep], only the
     * best [keep] are guaranteed: a word that can no longer reach them is left out before it is fully measured.
     */
    internal fun decodeScored(
        path: List<GesturePoint>,
        language: String,
        centersOverride: Map<Char, KeyCenter>? = null,
        previousWord: String? = null,
        keep: Int = Int.MAX_VALUE,
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
        val fromDictionary = dict.candidatesByEndpoints(startSet, endSet, MIN_CANDIDATE_FREQUENCY, MAX_POOL)

        // Context: the words that usually follow the previous one. They are always candidates (when their first and
        // last letters fit the swipe) and get a bonus by rank; it only separates shapes that fit about equally.
        val nextRank = HashMap<String, Int>()
        previousWord?.takeIf { it.isNotBlank() }?.let { previous ->
            dict.getBigramSuggestions(previous).forEachIndexed { rank, word -> nextRank.putIfAbsent(word.lowercase(Locale.ROOT), rank) }
        }

        // Each word once, in the order it was first offered: learned words, the dictionary's, then the context's.
        val pool = CandidatePool(personalWords.size + fromDictionary.size + nextRank.size)
        for (word in personalWords) pool.add(word, cleanWord(word), dict.getFrequency(word))
        for (i in 0 until fromDictionary.size) pool.add(fromDictionary.words[i], fromDictionary.normalized[i], fromDictionary.frequencies[i])
        for (word in nextRank.keys) {
            val c = cleanWord(word)
            if (c.length >= 2 && c.first() in startSet && c.last() in endSet) pool.add(word, c, dict.getFrequency(word))
        }

        // 4. Geometric scoring with cheap rejections first. Same arithmetic, in the same order, as it always was (the
        //    scores are identical to the last bit, see WordFlowDecoderEquivalenceTest), without its cost: what depends
        //    only on the swipe is worked out once, nothing is allocated per candidate, and a word stops being measured
        //    as soon as it can no longer pass MAX_DISTANCE.
        val ux = FloatArray(SAMPLES)
        val uy = FloatArray(SAMPLES)
        for (i in 0 until SAMPLES) { ux[i] = resampled[i].x; uy[i] = resampled[i].y }
        val keys = KeyTable(centers)
        // Bit i of near[k]: sample i of the swipe lies within LETTER_RADIUS of key k.
        val near = IntArray(keys.size)
        for (k in 0 until keys.size) {
            var mask = 0
            for (i in 0 until SAMPLES) if (fastHypot(ux[i] - keys.x[k], uy[i] - keys.y[k]) <= LETTER_RADIUS) mask = mask or (1 shl i)
            near[k] = mask
        }
        var pathX = FloatArray(16) // the word's key centres, a doubled letter once; grows for long words
        var pathY = FloatArray(16)
        val idealX = FloatArray(SAMPLES)
        val idealY = FloatArray(SAMPLES)
        val resampleScratch = ResampleScratch()
        val rowA = DoubleArray(SAMPLES + 1)
        val rowB = DoubleArray(SAMPLES + 1)
        val best = BestScores(keep)

        val scored = ArrayList<SwipeCandidate>()
        for (c in 0 until pool.size) {
            val candidate = pool.words[c]!!
            val clean = pool.normalized[c]!!
            var count = 0
            var prevChar = ' '
            var unknownKey = false
            for (ch in clean) {
                if (ch == prevChar) continue
                val k = keys.indexOf(ch)
                if (k < 0) { unknownKey = true; break }
                if (count == pathX.size) { pathX = pathX.copyOf(count * 2); pathY = pathY.copyOf(count * 2) }
                pathX[count] = keys.x[k]
                pathY[count] = keys.y[k]
                count++
                prevChar = ch
            }
            if (unknownKey || count < 2) continue

            // A word whose ideal path is far longer or shorter than the swipe cannot be the one.
            var idealLength = 0.0
            for (i in 1 until count) idealLength += fastHypot(pathX[i] - pathX[i - 1], pathY[i] - pathY[i - 1])
            val ratio = idealLength / userLength
            if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) continue

            // Letters must be visited in order along the swipe: each one at the first sample near its key, at or after
            // the previous letter's (a doubled letter may use the same sample).
            var matched = 0
            var from = 0
            for (ch in clean) {
                val hits = near[keys.indexOf(ch)] and (-1 shl from)
                if (hits != 0) { matched++; from = Integer.numberOfTrailingZeros(hits) }
            }
            val completeness = matched.toDouble() / clean.length
            if (completeness < MIN_COMPLETENESS) continue

            val freq = pool.frequencies[c].toDouble() / 255.0 * FREQUENCY_WEIGHT
            val user = if (candidate in personalSet) USER_BONUS else 0.0
            val context = if (nextRank.isEmpty()) 0.0
                else nextRank[candidate.lowercase(Locale.ROOT)]?.let { CONTEXT_BONUS.getOrElse(it) { 0.0 } } ?: 0.0
            // Even a perfect fit would not reach the best ones: the score only falls as the distance grows.
            val floor = best.floor
            if (score(completeness, 0.0, freq, user, context) <= floor) continue

            resampleKeyPath(pathX, pathY, count, idealX, idealY, resampleScratch)
            // The euclidean part first: it is cheap, and since the DTW part can only add to it, it already rules out a
            // word that is too far.
            var euclid = 0.0
            for (i in 0 until SAMPLES) euclid += fastHypot(ux[i] - idealX[i], uy[i] - idealY[i])
            val euclidPart = euclid / SAMPLES * 0.4
            if (euclidPart > MAX_DISTANCE || score(completeness, euclidPart, freq, user, context) <= floor) continue
            val dtw = boundedDtw(ux, uy, idealX, idealY, rowA, rowB, euclidPart) { lowerBound ->
                score(completeness, lowerBound, freq, user, context) <= floor
            }
            if (dtw < 0.0) continue // gave up: it cannot pass MAX_DISTANCE or reach the best ones
            val effectiveDist = dtw / SAMPLES * 0.6 + euclidPart
            if (effectiveDist > MAX_DISTANCE) continue

            val shape = completeness * 2.2 / (1.0 + effectiveDist * SHAPE_SHARPNESS)
            val total = shape + freq + user + context
            scored.add(SwipeCandidate(candidate, total, shape, freq, user, context))
            best.offer(total)
        }

        return scored.sortedByDescending { it.score }
    }

    /**
     * The score of a word whose effective distance is [effectiveDist], written exactly as the real score is computed.
     * IEEE arithmetic is monotonic, so with a lower bound of the distance it is an upper bound of the score.
     */
    private fun score(completeness: Double, effectiveDist: Double, freq: Double, user: Double, context: Double): Double =
        completeness * 2.2 / (1.0 + effectiveDist * SHAPE_SHARPNESS) + freq + user + context

    /**
     * The [keep] best scores so far. A word that can score at most [floor] cannot be among them: on a tie the word met
     * first keeps its place (the final sort is stable), so leaving it out never changes the best [keep].
     */
    private class BestScores(keep: Int) {
        private val top = if (keep in 1..MAX_TRACKED) DoubleArray(keep) { Double.NEGATIVE_INFINITY } else null

        val floor: Double get() = top?.last() ?: Double.NEGATIVE_INFINITY

        fun offer(score: Double) {
            val t = top ?: return
            if (score <= t.last()) return
            var i = t.size - 1
            while (i > 0 && t[i - 1] < score) { t[i] = t[i - 1]; i-- }
            t[i] = score
        }

        private companion object {
            const val MAX_TRACKED = 16 // beyond this, keep every candidate (calibration wants them all)
        }
    }

    /** The candidates of one swipe as parallel arrays, each word once, in the order it was first offered. */
    private class CandidatePool(capacity: Int) {
        val words = arrayOfNulls<String>(capacity)
        val normalized = arrayOfNulls<String>(capacity)
        val frequencies = IntArray(capacity)
        var size = 0
            private set
        private val seen = HashSet<String>(capacity * 2)

        fun add(word: String, clean: String, frequency: Int) {
            if (!seen.add(word)) return
            words[size] = word
            normalized[size] = clean
            frequencies[size] = frequency
            size++
        }
    }

    /** The keys of a layout by index, and a lookup from a (normalised) letter to its index, -1 for none. */
    private class KeyTable(centers: Map<Char, KeyCenter>) {
        val size = centers.size
        val x = FloatArray(size)
        val y = FloatArray(size)
        private val latin = IntArray(LATIN_CHARS) { -1 } // the letters of every supported language
        private val others = HashMap<Char, Int>()

        init {
            var k = 0
            for ((ch, center) in centers) {
                x[k] = center.x
                y[k] = center.y
                if (ch.code < LATIN_CHARS) latin[ch.code] = k else others[ch] = k
                k++
            }
        }

        fun indexOf(ch: Char): Int = if (ch.code < LATIN_CHARS) latin[ch.code] else others[ch] ?: -1

        private companion object {
            const val LATIN_CHARS = 0x250 // up to Latin Extended-B
        }
    }

    /** Buffers for [resampleKeyPath]; they grow with the longest word of the swipe. */
    private class ResampleScratch {
        var x = FloatArray(16)
        var y = FloatArray(16)
        var segments = FloatArray(16)

        fun ensure(points: Int) {
            if (x.size >= points) return
            x = FloatArray(points * 2)
            y = FloatArray(points * 2)
            segments = FloatArray(points * 2)
        }
    }

    /**
     * [GestureResampler.resample] of a key path ([count] points in [px]/[py]) into [outX]/[outY], with the same
     * arithmetic in the same order, so the ideal path of a word is bit for bit the one it always was.
     */
    private fun resampleKeyPath(px: FloatArray, py: FloatArray, count: Int, outX: FloatArray, outY: FloatArray, scratch: ResampleScratch) {
        scratch.ensure(count)
        val fx = scratch.x
        val fy = scratch.y
        val segments = scratch.segments
        // 1. A point closer than 0.001 to the previous one kept is dropped.
        var kept = 1
        fx[0] = px[0]
        fy[0] = py[0]
        for (i in 1 until count) {
            if (fastHypot(px[i] - fx[kept - 1], py[i] - fy[kept - 1]) >= 0.001f) {
                fx[kept] = px[i]
                fy[kept] = py[i]
                kept++
            }
        }
        if (kept < 2) { outX.fill(fx[0]); outY.fill(fy[0]); return }
        // 2. Segment lengths and total length.
        var totalLength = 0.0f
        for (i in 0 until kept - 1) {
            val dist = fastHypot(fx[i + 1] - fx[i], fy[i + 1] - fy[i])
            segments[i] = dist
            totalLength += dist
        }
        if (totalLength <= 0.0f) { outX.fill(fx[0]); outY.fill(fy[0]); return }
        // 3. SAMPLES points at equal distances along the path.
        val interval = totalLength / (SAMPLES - 1).toFloat()
        outX[0] = fx[0]
        outY[0] = fy[0]
        var accumulatedLength = 0.0f
        var currentSegment = 0
        for (i in 1 until SAMPLES - 1) {
            val targetDistance = i * interval
            while (currentSegment < kept - 1 && accumulatedLength + segments[currentSegment] < targetDistance) {
                accumulatedLength += segments[currentSegment]
                currentSegment++
            }
            if (currentSegment >= kept - 1) {
                outX[i] = fx[kept - 1]
                outY[i] = fy[kept - 1]
            } else {
                val segLen = segments[currentSegment]
                val t = if (segLen > 0f) (targetDistance - accumulatedLength) / segLen else 0f
                outX[i] = fx[currentSegment] + t * (fx[currentSegment + 1] - fx[currentSegment])
                outY[i] = fy[currentSegment] + t * (fy[currentSegment + 1] - fy[currentSegment])
            }
        }
        outX[SAMPLES - 1] = fx[kept - 1]
        outY[SAMPLES - 1] = fy[kept - 1]
    }

    /**
     * [dtwDistance] (window 8) of the swipe and an ideal path, both of SAMPLES points, with the same arithmetic but
     * no allocation, giving up as soon as the word can no longer pass MAX_DISTANCE or [cannotReachBest] says so for a
     * lower bound of its effective distance: every warping path crosses each row and no cost is negative, so the
     * smallest value in a row is a lower bound of the final distance. Returns -1.0 when it gave up.
     */
    private inline fun boundedDtw(
        ux: FloatArray, uy: FloatArray, vx: FloatArray, vy: FloatArray, rowA: DoubleArray, rowB: DoubleArray,
        euclidPart: Double, cannotReachBest: (effectiveDistLowerBound: Double) -> Boolean,
    ): Double {
        var prev = rowA
        var cur = rowB
        prev.fill(Double.POSITIVE_INFINITY)
        prev[0] = 0.0
        for (i in 1..SAMPLES) {
            cur.fill(Double.POSITIVE_INFINITY)
            val x = ux[i - 1]
            val y = uy[i - 1]
            var rowMin = Double.POSITIVE_INFINITY
            for (j in maxOf(1, i - DTW_WINDOW)..minOf(SAMPLES, i + DTW_WINDOW)) {
                val value = fastHypot(x - vx[j - 1], y - vy[j - 1]).toDouble() + minOf(prev[j], cur[j - 1], prev[j - 1])
                cur[j] = value
                if (value < rowMin) rowMin = value
            }
            val lowerBound = rowMin / SAMPLES * 0.6 + euclidPart // the effective distance, as computed after the loop
            if (lowerBound > MAX_DISTANCE || cannotReachBest(lowerBound)) return -1.0
            val done = prev
            prev = cur
            cur = done
        }
        return prev[SAMPLES]
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

        /** Sakoe-Chiba window of the DTW between the swipe and a word's ideal path. */
        private const val DTW_WINDOW = 8

        fun cleanWord(raw: String): String = CompactWordTrie.normalize(raw)

        /**
         * kotlin.math.hypot(dx, dy), bit for bit, at a fraction of its cost: Math.hypot is a careful library routine
         * (about 14 ns per call on a laptop JVM) and a swipe used to call it over half a million times. The square root
         * of the sum of squares in double is within 2 ulp (of a double) of the exact length, and so is Math.hypot, so
         * both round to the same Float unless the length sits right next to the midpoint between two Floats: only then
         * is Math.hypot asked.
         */
        internal fun fastHypot(dx: Float, dy: Float): Float {
            val x = dx.toDouble()
            val y = dy.toDouble()
            val s = sqrt(x * x + y * y) // the squares of two Floats are exact in a double
            val f = s.toFloat()
            val fd = f.toDouble()
            val tolerance = 4.0 * Math.ulp(s)
            if (abs(s - (fd + Math.nextUp(f).toDouble()) * 0.5) <= tolerance ||
                abs(s - (fd + Math.nextDown(f).toDouble()) * 0.5) <= tolerance
            ) return hypot(dx, dy)
            return f
        }

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
