/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.dictionary.CompactWordTrie
import com.metroboard.ime.dictionary.DictionaryProvider
import com.metroboard.ime.dictionary.PersonalDictionary
import com.metroboard.ime.dictionary.RealDictionaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.hypot

/**
 * The faster decoder (0.5) must rank exactly as the one it replaced: same candidates, same order, same scores to the
 * last bit. [ReferenceDecoder] is the 0.4.1 implementation, kept here verbatim as the oracle; the swipes cover the
 * four languages, clean and noisy shapes, learned words, the previous-word context and random scribbles.
 */
class WordFlowDecoderEquivalenceTest {

    private class FixedPersonal(private val byLanguage: Map<String, List<String>>) : PersonalDictionary {
        override fun wordsWithPrefix(prefix: String) = emptyList<String>()
        override fun learn(word: String) = Unit
        override fun clear() = Unit
        override fun words(language: String) = byLanguage[language].orEmpty()
    }

    private val personal = FixedPersonal(
        mapOf(
            "es" to listOf("Ameizin", "chévere", "hola", "Quito", "wasap"),
            "en" to listOf("Ameizin", "gonna", "hello", "Brooklyn"),
            "fr" to listOf("Ameizin", "bonjour", "ouf"),
            "de" to listOf("Ameizin", "hallo", "Kumpel"),
        ),
    )

    @Test fun fastHypotIsBitForBitKotlinHypot() {
        val rnd = Random(5)
        var checked = 0
        repeat(2_000_000) {
            val dx = (rnd.nextFloat() * 2.4f - 1.2f) * if (it % 7 == 0) 1e-3f else 1f
            val dy = (rnd.nextFloat() * 2.4f - 1.2f) * if (it % 11 == 0) 1e-3f else 1f
            val expected = hypot(dx, dy)
            val actual = WordFlowShapeDecoder.fastHypot(dx, dy)
            if (expected.toRawBits() != actual.toRawBits()) throw AssertionError("fastHypot($dx, $dy) = $actual, hypot = $expected")
            checked++
        }
        assertEquals(0f, WordFlowShapeDecoder.fastHypot(0f, 0f))
        assertEquals(2_000_000, checked)
    }

    @Test fun rankingAndScoresAreIdenticalToThePreviousDecoder() {
        var compared = 0
        var nonEmpty = 0
        for (lang in listOf("es", "en", "fr", "de")) {
            val dict = RealDictionaries.get(lang)
            val centers = KeyboardGeometry.centers(lang)
            val fast = WordFlowShapeDecoder(personal, dictionaryFor = RealDictionaries::get)
            val reference = ReferenceDecoder(personal, dict)
            val words = dict.getTopWords(3000).filter { usable(centers, it) }
            val previousWords = dict.getTopWords(400).filter { dict.getBigramSuggestions(it).isNotEmpty() }
            val rnd = Random(lang.hashCode().toLong())

            fun check(path: List<GesturePoint>, previous: String?, what: String) {
                val expected = reference.decodeScored(path, lang, centers, previous)
                val actual = fast.decodeScored(path, lang, centers, previous)
                if (expected != actual) {
                    val firstDiff = expected.indices.firstOrNull { it >= actual.size || expected[it] != actual[it] }
                    throw AssertionError(
                        "[$lang] $what previous=$previous: ${expected.size} vs ${actual.size} candidates, first difference at " +
                            "$firstDiff: ${firstDiff?.let(expected::getOrNull)} vs ${firstDiff?.let(actual::getOrNull)}",
                    )
                }
                // What the keyboard actually uses: decode() keeps only the best three and may skip the rest.
                val expectedTop = expected.take(3)
                val prunedTop = fast.decodeScored(path, lang, centers, previous, keep = 3).take(3)
                if (expectedTop != prunedTop) throw AssertionError("[$lang] $what previous=$previous: best three $expectedTop vs $prunedTop")
                val expectedWords = if (expected.isEmpty() || expected.first().shapeSimilarity < WordFlowShapeDecoder.MIN_CONFIDENCE) emptyList()
                    else expected.map { it.word }.distinct().take(3)
                assertEquals("[$lang] $what previous=$previous", expectedWords, fast.decode(path, lang, centers, previous))
                compared++
                if (expected.isNotEmpty()) nonEmpty++
            }

            for ((i, w) in words.take(120).withIndex()) {
                for (jitter in floatArrayOf(0.02f, 0.05f)) {
                    val path = swipe(w, centers, rnd, jitter)
                    if (path.size < 3) continue
                    check(path, null, "swipe '$w' jitter $jitter")
                    check(path, previousWords[i % previousWords.size], "swipe '$w' jitter $jitter")
                }
            }
            for (w in personal.words(lang)) {
                val path = swipe(w, centers, rnd, 0.02f)
                if (path.size >= 3) check(path, null, "learned word '$w'")
            }
            repeat(40) { s ->
                val scribble = (0 until 10 + s % 30).map { GesturePoint(rnd.nextFloat(), rnd.nextFloat(), it * 8L) }
                check(scribble, previousWords[s % previousWords.size], "scribble $s")
            }
        }
        println("WordFlowDecoderEquivalenceTest: $compared swipes compared ($nonEmpty with candidates), all identical")
        assertTrue(nonEmpty > 1500)
    }

    // ---- synthetic swipes, the generator of WordFlowQualityTest ---------------------------------------------------

    private fun usable(centers: Map<Char, KeyCenter>, w: String): Boolean {
        val norm = CompactWordTrie.normalize(w)
        return w == w.lowercase() && norm.length >= 3 && norm.all { it in centers }
    }

    private fun swipe(word: String, centers: Map<Char, KeyCenter>, rnd: Random, jitter: Float): List<GesturePoint> {
        val keys = ArrayList<KeyCenter>()
        var prev = ' '
        for (ch in CompactWordTrie.normalize(word)) {
            if (ch == prev) continue
            centers[ch]?.let { keys += it; prev = ch }
        }
        if (keys.size < 2) return emptyList()
        val dense = ArrayList<GesturePoint>()
        var t = 0L
        for (i in 0 until keys.size - 1) {
            val a = keys[i]; val b = keys[i + 1]
            val steps = maxOf(2, (hypot(b.x - a.x, b.y - a.y) / 0.015f).toInt())
            for (s in 0 until steps) {
                val f = s / steps.toFloat()
                dense += GesturePoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, t); t += 8
            }
        }
        dense += GesturePoint(keys.last().x, keys.last().y, t)
        val smooth = dense.indices.map { i ->
            val lo = maxOf(0, i - 2); val hi = minOf(dense.size - 1, i + 2)
            val n = hi - lo + 1
            GesturePoint(dense.subList(lo, hi + 1).sumOf { it.x.toDouble() }.toFloat() / n,
                dense.subList(lo, hi + 1).sumOf { it.y.toDouble() }.toFloat() / n, dense[i].timeMillis)
        }
        return smooth.map { GesturePoint(it.x + rnd.nextGaussian().toFloat() * jitter, it.y + rnd.nextGaussian().toFloat() * jitter, it.timeMillis) }
    }

    /** WordFlowShapeDecoder.decodeScored as shipped in 0.4.1 (and its dictionary candidate merge), unchanged. */
    private class ReferenceDecoder(private val personalDictionary: PersonalDictionary?, private val dict: DictionaryProvider) {
        private val endpointIndex: Map<Int, List<String>> by lazy {
            val index = HashMap<Int, MutableList<String>>()
            for (word in dict.getTopWords(Int.MAX_VALUE)) {
                val norm = CompactWordTrie.normalize(word)
                if (norm.length < 2) continue
                index.getOrPut(norm.first().code shl 16 or norm.last().code) { ArrayList() }.add(word)
            }
            index
        }

        private fun wordsByEndpoints(first: Set<Char>, last: Set<Char>, minFrequency: Int, limit: Int): List<String> {
            val pools = ArrayList<List<String>>()
            for (f in first) for (l in last) endpointIndex[f.code shl 16 or l.code]?.let { pools += it }
            val cursor = IntArray(pools.size)
            val out = ArrayList<String>()
            while (out.size < limit) {
                var bestPool = -1
                var bestFreq = -1
                for (p in pools.indices) {
                    val w = pools[p].getOrNull(cursor[p]) ?: continue
                    val f = dict.getFrequency(w)
                    if (f > bestFreq) { bestFreq = f; bestPool = p }
                }
                if (bestPool < 0 || bestFreq < minFrequency) break
                out += pools[bestPool][cursor[bestPool]++]
            }
            return out
        }

        fun decodeScored(path: List<GesturePoint>, language: String, centers: Map<Char, KeyCenter>, previousWord: String?): List<SwipeCandidate> {
            if (path.size < 3) return emptyList()
            val simplified = simplifyPath(path)
            if (simplified.size < 3) return emptyList()
            val resampled = GestureResampler.resample(normalizeCoordinates(simplified), SAMPLES)
            if (resampled.size < SAMPLES) return emptyList()
            val userLength = polylineLength(resampled)
            if (userLength < WordFlowShapeDecoder.MIN_PATH_LENGTH) return emptyList()

            val startKeys = nearestKeys(centers, resampled.first(), WordFlowShapeDecoder.ENDPOINT_KEYS, WordFlowShapeDecoder.ENDPOINT_RADIUS)
            val endKeys = nearestKeys(centers, resampled.last(), WordFlowShapeDecoder.ENDPOINT_KEYS, WordFlowShapeDecoder.ENDPOINT_RADIUS)
            if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

            val startSet = startKeys.toSet()
            val endSet = endKeys.toSet()
            val personalWords = personalDictionary?.words(language).orEmpty().filter {
                val c = WordFlowShapeDecoder.cleanWord(it)
                c.length >= 2 && c.first() in startSet && c.last() in endSet
            }
            val personalSet = personalWords.toHashSet()
            val pool = LinkedHashSet<String>(personalWords)
            pool.addAll(wordsByEndpoints(startSet, endSet, WordFlowShapeDecoder.MIN_CANDIDATE_FREQUENCY, WordFlowShapeDecoder.MAX_POOL))

            val nextRank = HashMap<String, Int>()
            previousWord?.takeIf { it.isNotBlank() }?.let { previous ->
                dict.getBigramSuggestions(previous).forEachIndexed { rank, word -> nextRank.putIfAbsent(word.lowercase(Locale.ROOT), rank) }
            }
            for (word in nextRank.keys) {
                val c = WordFlowShapeDecoder.cleanWord(word)
                if (c.length >= 2 && c.first() in startSet && c.last() in endSet) pool.add(word)
            }

            val scored = ArrayList<SwipeCandidate>()
            for (candidate in pool) {
                val clean = WordFlowShapeDecoder.cleanWord(candidate)
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

                val idealLength = polylineLength(keyPoints)
                val ratio = idealLength / userLength
                if (ratio < WordFlowShapeDecoder.MIN_LENGTH_RATIO || ratio > WordFlowShapeDecoder.MAX_LENGTH_RATIO) continue

                var matched = 0
                var from = 0
                for (ch in clean) {
                    val center = centers[ch] ?: continue
                    var hit = -1
                    for (i in from until resampled.size) {
                        if (hypot(resampled[i].x - center.x, resampled[i].y - center.y) <= WordFlowShapeDecoder.LETTER_RADIUS) { hit = i; break }
                    }
                    if (hit >= 0) { matched++; from = hit }
                }
                val completeness = matched.toDouble() / clean.length
                if (completeness < WordFlowShapeDecoder.MIN_COMPLETENESS) continue

                val ideal = GestureResampler.resample(keyPoints, SAMPLES)
                val dtw = WordFlowShapeDecoder.dtwDistance(resampled, ideal, window = 8) / SAMPLES
                var euclid = 0.0
                for (i in 0 until SAMPLES) euclid += hypot(resampled[i].x - ideal[i].x, resampled[i].y - ideal[i].y)
                val effectiveDist = dtw * 0.6 + euclid / SAMPLES * 0.4
                if (effectiveDist > WordFlowShapeDecoder.MAX_DISTANCE) continue

                val shape = completeness * 2.2 / (1.0 + effectiveDist * WordFlowShapeDecoder.SHAPE_SHARPNESS)
                val freq = dict.getFrequency(candidate).toDouble() / 255.0 * WordFlowShapeDecoder.FREQUENCY_WEIGHT
                val user = if (candidate in personalSet) WordFlowShapeDecoder.USER_BONUS else 0.0
                val context = nextRank[candidate.lowercase(Locale.ROOT)]?.let { WordFlowShapeDecoder.CONTEXT_BONUS.getOrElse(it) { 0.0 } } ?: 0.0
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
                if (hypot(curr.x - prev.x, curr.y - prev.y) >= threshold) { result.add(curr); prev = curr }
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
            return path.map { p -> GesturePoint((p.x - minX) / width, (p.y - minY) / height, p.timeMillis) }
        }

        private companion object {
            const val SAMPLES = WordFlowShapeDecoder.SAMPLES
        }
    }
}
