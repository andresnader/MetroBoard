package com.metroboard.ime.gestures

import com.metroboard.ime.prediction.LocalSuggestionEngine
import java.util.Locale
import kotlin.math.hypot

class WordFlowShapeDecoder(
    dictionary: List<String> = DEFAULT_WORDS
) : ShapeWritingDecoder {

    private val bucketedWords: Map<Pair<Char, Char>, List<String>>

    init {
        val map = HashMap<Pair<Char, Char>, MutableList<String>>()
        for (raw in dictionary) {
            val clean = cleanWord(raw)
            if (clean.length >= 2) {
                val pair = clean.first() to clean.last()
                map.getOrPut(pair) { ArrayList() }.add(raw)
            }
        }
        bucketedWords = map
    }

    override fun decode(path: List<GesturePoint>): List<String> = decode(path, "es")

    fun decode(path: List<GesturePoint>, language: String = "es"): List<String> {
        if (path.size < 3) return emptyList()

        val normalizedPath = normalizeCoordinates(path)
        val resampled = GestureResampler.resample(normalizedPath, 32)
        if (resampled.size < 32) return emptyList()

        val startPoint = resampled.first()
        val endPoint = resampled.last()
        val centers = KeyboardGeometry.centers(language)

        val startKeys = KeyboardGeometry.findKeysNear(startPoint.x, startPoint.y, 0.22f, language)
        val endKeys = KeyboardGeometry.findKeysNear(endPoint.x, endPoint.y, 0.22f, language)

        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val candidates = LinkedHashSet<String>()
        for (s in startKeys) {
            for (e in endKeys) {
                bucketedWords[s to e]?.let { candidates.addAll(it) }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val scored = ArrayList<Pair<String, Double>>()

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

            // 1. Average Euclidean curve distance
            var totalDistance = 0.0
            for (i in 0 until 32) {
                val p1 = resampled[i]
                val p2 = idealResampled[i]
                totalDistance += hypot(p1.x - p2.x, p1.y - p2.y)
            }
            val avgDist = totalDistance / 32.0

            // 2. Monotonic letter order alignment
            var matchedLetterIdx = 0
            var lastMatchedPathIdx = 0
            for (charIdx in clean.indices) {
                val targetChar = clean[charIdx]
                val center = centers[targetChar] ?: continue
                var found = false
                for (pIdx in lastMatchedPathIdx until resampled.size) {
                    val p = resampled[pIdx]
                    if (hypot(p.x - center.x, p.y - center.y) <= 0.25f) {
                        found = true
                        lastMatchedPathIdx = pIdx
                        break
                    }
                }
                if (found) matchedLetterIdx++
            }

            val completeness = matchedLetterIdx.toDouble() / clean.length.toDouble()
            // Score formula: high completeness, low average distance
            val score = (completeness * 2.0) / (1.0 + avgDist * 4.0)

            if (completeness >= 0.65 && avgDist <= 0.30) {
                scored.add(candidate to score)
            }
        }

        return scored.sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(3)
    }

    private fun normalizeCoordinates(path: List<GesturePoint>): List<GesturePoint> {
        val maxX = path.maxOf { it.x }
        val maxY = path.maxOf { it.y }

        // If coordinates already appear normalized (<= 1.5), return as is
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
        fun cleanWord(raw: String): String =
            raw.lowercase(Locale.ROOT).mapNotNull { c ->
                when (c) {
                    'ñ' -> 'ñ'
                    'á' -> 'a'
                    'é' -> 'e'
                    'í' -> 'i'
                    'ó' -> 'o'
                    'ú', 'ü' -> 'u'
                    in 'a'..'z' -> c
                    else -> null
                }
            }.joinToString("")

        val DEFAULT_WORDS: List<String> = listOf(
            // Spanish
            "hola", "amigo", "casa", "comer", "como", "cuando", "donde", "gracias",
            "hacer", "hasta", "hombre", "hora", "hoy", "juntos", "luego", "mañana",
            "mejor", "mensaje", "muchas", "mundo", "noche", "nosotros", "nuevo",
            "para", "pero", "podemos", "porque", "pronto", "puedo", "quiero",
            "saber", "saludos", "siempre", "también", "tarde", "teclado", "tenemos",
            "tiempo", "tienes", "todos", "vamos", "verdad", "vida", "bueno", "buenas",
            "gente", "mano", "lugar", "mujer", "padre", "trabajo", "año", "años",
            "niño", "niña", "señor", "pequeño", "bien", "claro", "favor", "mucho",
            "nada", "todo", "está", "estoy", "ahora", "después", "desde", "sobre",
            "entre", "tener", "estar", "decir", "poder", "ir", "ver", "dar",
            "llegar", "pasar", "deber", "poner", "parecer", "quedar", "creer",
            "hablar", "llevar", "dejar", "seguir", "encontrar", "llamar", "venir",
            "pensar", "salir", "volver", "tomar", "conocer", "vivir", "sentir",
            // English
            "about", "after", "again", "also", "back", "because", "been", "before",
            "come", "could", "first", "from", "give", "good", "have", "here",
            "into", "just", "know", "like", "look", "make", "most", "only",
            "other", "over", "people", "some", "take", "than", "that", "the",
            "their", "them", "then", "there", "these", "they", "think", "this",
            "time", "want", "well", "what", "when", "which", "will", "with",
            "work", "would", "year", "your"
        )
    }
}
