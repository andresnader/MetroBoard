package com.metroboard.ime.prediction

import com.metroboard.ime.dictionary.DisabledPersonalDictionary
import com.metroboard.ime.dictionary.PersonalDictionary
import java.util.Locale

class LocalSuggestionEngine(
    private val personal: PersonalDictionary = DisabledPersonalDictionary,
) : SuggestionEngine {

    override fun suggest(prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase(Locale.ROOT)
        val result = LinkedHashSet<String>()

        // 1. Direct contraction or typo correction (e.g. "dont" -> "don't", "tambien" -> "también")
        CONTRACTIONS_AND_TYPOS[normalized]?.let { result.add(it) }

        // 2. Personal dictionary prefix matches
        personal.wordsWithPrefix(normalized).filter { it != normalized }.forEach { result.add(it) }

        // 3. Built-in words matching prefix
        WORDS.asSequence()
            .filter { it.startsWith(normalized) && it != normalized }
            .forEach {
                if (result.size < 3) result.add(it)
            }

        // 4. If fewer than 3 results and prefix has at least 3 letters, try fuzzy distance with keyboard adjacency
        if (result.size < 3 && normalized.length >= 3) {
            val fuzzyCandidates = WORDS.asSequence()
                .filter { it !in result && it != normalized && Math.abs(it.length - normalized.length) <= 1 }
                .map { word -> word to proximityDamerauLevenshtein(normalized, word) }
                .filter { (_, dist) -> dist <= 1.4 }
                .sortedBy { (_, dist) -> dist }
                .map { (word, _) -> word }

            for (word in fuzzyCandidates) {
                if (result.size >= 3) break
                result.add(word)
            }
        }

        return result.take(3).toList()
    }

    override fun suggestNext(previousWord: String): List<String> {
        if (previousWord.isBlank()) return emptyList()
        val normalized = previousWord.lowercase(Locale.ROOT)
        return BIGRAMS[normalized] ?: emptyList()
    }

    override fun autoCorrect(word: String): String? {
        if (word.isBlank()) return null
        val normalized = word.lowercase(Locale.ROOT)
        // Check direct contraction/typo map
        CONTRACTIONS_AND_TYPOS[normalized]?.let { return it }

        // If word is already in the dictionary or personal dictionary, no correction needed
        if (WORDS.contains(normalized) || personal.wordsWithPrefix(normalized).contains(normalized)) {
            return null
        }

        // Check high-confidence typo match (distance <= 0.8)
        if (normalized.length >= 3) {
            val best = WORDS.asSequence()
                .filter { Math.abs(it.length - normalized.length) <= 1 }
                .map { w -> w to proximityDamerauLevenshtein(normalized, w) }
                .filter { (_, d) -> d <= 0.8 }
                .minByOrNull { (_, d) -> d }
            if (best != null) return best.first
        }
        return null
    }

    companion object {
        val ADJACENT_KEYS: Map<Char, Set<Char>> = mapOf(
            'q' to setOf('w', 'a', 's'),
            'w' to setOf('q', 'e', 'a', 's', 'd'),
            'e' to setOf('w', 'r', 's', 'd', 'f'),
            'r' to setOf('e', 't', 'd', 'f', 'g'),
            't' to setOf('r', 'y', 'f', 'g', 'h'),
            'y' to setOf('t', 'u', 'g', 'h', 'j'),
            'u' to setOf('y', 'i', 'h', 'j', 'k'),
            'i' to setOf('u', 'o', 'j', 'k', 'l'),
            'o' to setOf('i', 'p', 'k', 'l'),
            'p' to setOf('o', 'l', 'ñ'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('w', 'e', 'a', 'd', 'z', 'x'),
            'd' to setOf('e', 'r', 's', 'f', 'x', 'c'),
            'f' to setOf('r', 't', 'd', 'g', 'c', 'v'),
            'g' to setOf('t', 'y', 'f', 'h', 'v', 'b'),
            'h' to setOf('y', 'u', 'g', 'j', 'b', 'n'),
            'j' to setOf('u', 'i', 'h', 'k', 'n', 'm'),
            'k' to setOf('i', 'o', 'j', 'l', 'm'),
            'l' to setOf('o', 'p', 'k', 'ñ'),
            'ñ' to setOf('l', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

        fun proximityDamerauLevenshtein(s1: String, s2: String): Double {
            val len1 = s1.length
            val len2 = s2.length
            val dp = Array(len1 + 1) { DoubleArray(len2 + 1) }

            for (i in 0..len1) dp[i][0] = i.toDouble()
            for (j in 0..len2) dp[0][j] = j.toDouble()

            for (i in 1..len1) {
                val c1 = s1[i - 1]
                for (j in 1..len2) {
                    val c2 = s2[j - 1]
                    val cost = when {
                        c1 == c2 -> 0.0
                        ADJACENT_KEYS[c1]?.contains(c2) == true -> 0.6
                        else -> 1.0
                    }
                    var minCost = minOf(
                        dp[i - 1][j] + 1.0,      // deletion
                        dp[i][j - 1] + 1.0,      // insertion
                        dp[i - 1][j - 1] + cost   // substitution
                    )
                    // transposition
                    if (i > 1 && j > 1 && c1 == s2[j - 2] && s1[i - 2] == c2) {
                        minCost = minOf(minCost, dp[i - 2][j - 2] + 0.8)
                    }
                    dp[i][j] = minCost
                }
            }
            return dp[len1][len2]
        }

        val CONTRACTIONS_AND_TYPOS = mapOf(
            // English
            "dont" to "don't", "cant" to "can't", "wont" to "won't", "im" to "I'm",
            "ive" to "I've", "id" to "I'd", "ill" to "I'll", "youre" to "youre",
            "theyre" to "they're", "weve" to "we've", "youve" to "you've",
            "didnt" to "didn't", "isnt" to "isn't", "arent" to "aren't",
            "wasnt" to "wasn't", "werent" to "weren't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't",
            "havent" to "haven't", "teh" to "the", "adn" to "and", "taht" to "that",
            "waht" to "what", "wiht" to "with", "tou" to "you", "thsi" to "this",
            "peopel" to "people", "becuase" to "because",
            // Spanish
            "tambien" to "también", "mas" to "más", "dia" to "día", "dias" to "días",
            "esta" to "está", "estas" to "estás", "estan" to "están",
            "aqui" to "aquí", "alli" to "allí", "facil" to "fácil", "dificil" to "difícil",
            "quiza" to "quizá", "quizas" to "quizás", "adios" to "adiós",
            "corazon" to "corazón", "cancion" to "canción", "accion" to "acción",
            "entonses" to "entonces"
        )

        val BIGRAMS = mapOf(
            // Spanish
            "gracias" to listOf("por", "a", "todos"),
            "muchas" to listOf("gracias"),
            "buenos" to listOf("días"),
            "buenas" to listOf("tardes", "noches"),
            "cómo" to listOf("estás", "está", "te va"),
            "como" to listOf("estás", "está", "siempre"),
            "por" to listOf("favor", "ejemplo", "eso"),
            "de" to listOf("la", "el", "los"),
            "en" to listOf("el", "la", "casa"),
            "te" to listOf("quiero", "amo", "veo"),
            "vamos" to listOf("a", "bien"),
            "hasta" to listOf("luego", "mañana", "pronto"),
            "hola" to listOf("cómo", "amigo", "buenos"),
            // English
            "how" to listOf("are", "is", "about"),
            "are" to listOf("you", "we", "they"),
            "thank" to listOf("you"),
            "thanks" to listOf("for", "again"),
            "good" to listOf("morning", "night", "afternoon"),
            "see" to listOf("you"),
            "let" to listOf("me", "us"),
            "what" to listOf("is", "are", "about"),
            "where" to listOf("are", "is"),
            "take" to listOf("care"),
            "you" to listOf("are", "can", "know")
        )

        private val WORDS = listOf(
            // Existing Spanish words
            "ahora", "amigo", "amiga", "amor", "antes", "aquí", "bien", "buenas", "bueno",
            "casa", "casi", "comer", "como", "contigo", "cuando", "día", "días", "donde",
            "entonces", "estamos", "estar", "gracias", "hacer", "hasta", "hola", "hombre",
            "hora", "hoy", "juntos", "luego", "mañana", "mejor", "mensaje", "muchas",
            "mundo", "noche", "nosotros", "nuevo", "para", "pero", "podemos", "porque",
            "pronto", "puedo", "quiero", "saber", "saludos", "siempre", "también", "tarde",
            "teclado", "tenemos", "tiempo", "tienes", "todos", "vamos", "verdad", "vida",
            // Additional everyday Spanish words
            "algo", "año", "años", "aquel", "así", "aunque", "buena", "cada", "claro",
            "cosa", "cosas", "creo", "cual", "cuál", "dar", "decir", "después", "dios",
            "donde", "dónde", "ella", "ellos", "esto", "estoy", "favor", "gente", "gran",
            "haber", "hablar", "hace", "hacia", "hijo", "igual", "lado", "llegar", "lugar",
            "mano", "más", "mayor", "medio", "mientras", "mismo", "momento", "mujer", "nada",
            "nadie", "niño", "nunca", "otro", "otra", "padre", "parece", "parte", "pasar",
            "poco", "poder", "poner", "primer", "primero", "pueblo", "quien", "quién",
            "razón", "saber", "salir", "seguir", "segundo", "seguro", "señor", "sentir",
            "ser", "si", "sí", "sido", "sólo", "solo", "somos", "soy", "su", "sus", "tanto",
            "tener", "tipo", "toda", "tomar", "trabajo", "tres", "uno", "unos", "va",
            "veces", "ver", "vez", "visto", "volver", "ya",
            // Common English words
            "about", "after", "again", "all", "also", "and", "any", "are", "back", "because",
            "been", "before", "can", "come", "could", "day", "even", "first", "for", "from",
            "get", "give", "good", "have", "here", "how", "into", "just", "know", "like",
            "look", "make", "most", "new", "not", "now", "one", "only", "other", "our",
            "out", "over", "people", "say", "see", "she", "some", "take", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "think", "this",
            "time", "two", "use", "want", "way", "well", "what", "when", "which", "who",
            "will", "with", "work", "would", "year", "you", "your"
        )
    }
}
