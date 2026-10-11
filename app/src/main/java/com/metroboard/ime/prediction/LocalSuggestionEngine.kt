package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import com.metroboard.ime.dictionary.PersonalDictionary
import java.util.Locale
import kotlin.math.abs

class LocalSuggestionEngine(
    private val personal: PersonalDictionary = DisabledPersonalDictionary,
    language: String = "es",
) : SuggestionEngine {

    private var pack: LanguagePack = LanguagePacks.forCode(language)

    override fun setLanguage(language: String) {
        pack = LanguagePacks.forCode(language)
    }

    override fun suggest(prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase(Locale.ROOT)
        val result = LinkedHashSet<String>()

        pack.corrections[normalized]?.let { result.add(it) }

        personal.wordsWithPrefix(normalized)
            .filter { it != normalized }
            .forEach { result.add(it) }

        pack.words.asSequence()
            .filter { it.startsWith(normalized) && it != normalized }
            .forEach {
                if (result.size < 3) result.add(it)
            }

        if (result.size < 3 && normalized.length >= 3) {
            val fuzzy = pack.words.asSequence()
                .filter { it !in result && it != normalized && abs(it.length - normalized.length) <= 1 }
                .map { word -> word to proximityDamerauLevenshtein(normalized, word) }
                .filter { (_, distance) -> distance <= 1.4 }
                .sortedBy { (_, distance) -> distance }
                .map { it.first }
            for (word in fuzzy) {
                if (result.size >= 3) break
                result.add(word)
            }
        }
        return result.take(3)
    }

    override fun suggestNext(previousWord: String): List<String> {
        if (previousWord.isBlank()) return emptyList()
        return pack.bigrams[previousWord.lowercase(Locale.ROOT)] ?: emptyList()
    }

    override fun autoCorrect(word: String): String? {
        if (word.isBlank()) return null
        val normalized = word.lowercase(Locale.ROOT)
        pack.corrections[normalized]?.let { return it }

        if (pack.words.contains(normalized) || personal.wordsWithPrefix(normalized).contains(normalized)) {
            return null
        }

        if (normalized.length >= 3) {
            return pack.words.asSequence()
                .filter { abs(it.length - normalized.length) <= 1 }
                .map { candidate -> candidate to proximityDamerauLevenshtein(normalized, candidate) }
                .filter { (_, distance) -> distance <= 0.8 }
                .minByOrNull { it.second }
                ?.first
        }
        return null
    }

    companion object {
        private val ADJACENT_KEYS: Map<Char, Set<Char>> = mapOf(
            'q' to setOf('w', 'a', 's'), 'w' to setOf('q', 'e', 'a', 's', 'd'),
            'e' to setOf('w', 'r', 's', 'd', 'f'), 'r' to setOf('e', 't', 'd', 'f', 'g'),
            't' to setOf('r', 'y', 'f', 'g', 'h'), 'y' to setOf('t', 'u', 'g', 'h', 'j'),
            'u' to setOf('y', 'i', 'h', 'j', 'k'), 'i' to setOf('u', 'o', 'j', 'k', 'l'),
            'o' to setOf('i', 'p', 'k', 'l'), 'p' to setOf('o', 'l', 'ñ'),
            'a' to setOf('q', 'w', 's', 'z'), 's' to setOf('w', 'e', 'a', 'd', 'z', 'x'),
            'd' to setOf('e', 'r', 's', 'f', 'x', 'c'), 'f' to setOf('r', 't', 'd', 'g', 'c', 'v'),
            'g' to setOf('t', 'y', 'f', 'h', 'v', 'b'), 'h' to setOf('y', 'u', 'g', 'j', 'b', 'n'),
            'j' to setOf('u', 'i', 'h', 'k', 'n', 'm'), 'k' to setOf('i', 'o', 'j', 'l', 'm'),
            'l' to setOf('o', 'p', 'k', 'ñ'), 'ñ' to setOf('l', 'p'),
            'z' to setOf('a', 's', 'x'), 'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'), 'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'), 'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

        fun proximityDamerauLevenshtein(s1: String, s2: String): Double {
            val dp = Array(s1.length + 1) { DoubleArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i.toDouble()
            for (j in 0..s2.length) dp[0][j] = j.toDouble()
            for (i in 1..s1.length) {
                val c1 = s1[i - 1]
                for (j in 1..s2.length) {
                    val c2 = s2[j - 1]
                    val cost = when {
                        c1 == c2 -> 0.0
                        ADJACENT_KEYS[c1]?.contains(c2) == true -> 0.6
                        else -> 1.0
                    }
                    var best = minOf(
                        dp[i - 1][j] + 1.0,
                        dp[i][j - 1] + 1.0,
                        dp[i - 1][j - 1] + cost,
                    )
                    if (i > 1 && j > 1 && c1 == s2[j - 2] && s1[i - 2] == c2) {
                        best = minOf(best, dp[i - 2][j - 2] + 0.8)
                    }
                    dp[i][j] = best
                }
            }
            return dp[s1.length][s2.length]
        }
    }
}
