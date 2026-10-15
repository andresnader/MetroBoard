/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import java.util.Locale

data class DictionaryEntry(
    val word: String,
    val frequency: Int,
    val normalized: String,
    val language: String
)

/** Shape-writing candidates as parallel arrays (word, normalised form, frequency), most frequent first. */
class EndpointCandidates(val words: Array<String>, val normalized: Array<String>, val frequencies: IntArray) {
    val size: Int get() = words.size
}

interface DictionaryProvider {
    val language: String
    val defaultLayout: String
    fun getFrequency(word: String): Int
    fun contains(word: String): Boolean
    fun wordsWithPrefix(prefix: String, limit: Int = 5): List<String>
    fun searchFuzzy(word: String, limit: Int = 3): List<String>
    fun getTopWords(limit: Int = 200): List<String>

    /** True for the tiny built-in sample used while the real dictionary is still loading. */
    val isSample: Boolean get() = false

    /**
     * Words (most frequent first) whose first letter is in [first] and last letter in [last],
     * compared accent-insensitively. Candidate pool for shape writing: it must reach the whole
     * lexicon, not only the most frequent few hundred words.
     */
    fun wordsByEndpoints(first: Set<Char>, last: Set<Char>, minFrequency: Int = 0, limit: Int = 4000): List<String> =
        emptyList()

    /**
     * [wordsByEndpoints] with each word's [CompactWordTrie.normalize]d form and its frequency, same words in the same
     * order. The shape writer looks at thousands of candidates per swipe; working both out again for each one (a
     * lower-cased copy and a map lookup) cost more than some of the geometry.
     */
    fun candidatesByEndpoints(first: Set<Char>, last: Set<Char>, minFrequency: Int = 0, limit: Int = 4000): EndpointCandidates {
        val words = wordsByEndpoints(first, last, minFrequency, limit)
        return EndpointCandidates(
            words.toTypedArray(),
            Array(words.size) { CompactWordTrie.normalize(words[it]) },
            IntArray(words.size) { getFrequency(words[it]) },
        )
    }
    fun getBigramSuggestions(previousWord: String): List<String>
    fun getTrigramSuggestions(word1: String, word2: String): List<String> = emptyList()
    fun autoCorrect(word: String): String?

    /**
     * Like [autoCorrect], but a fuzzy (non-curated) replacement must be at least this frequent.
     * Curated typo-map entries are always accepted. Used to never "correct" into rare words.
     */
    fun autoCorrect(word: String, minFrequency: Int): String? = autoCorrect(word)
    fun normalize(word: String): String
}

/** Entradas cacheadas por nodo para el autocompletado por prefijo. */
private const val MAX_TOP_ENTRIES = 15

class CompactTrieNode {
    var isWord: Boolean = false
    var entry: DictionaryEntry? = null

    // Children as two parallel arrays kept sorted by character. A HashMap per node cost about 200 bytes even for the
    // many nodes with zero or one child; this is a few bytes for a leaf. Lookups scan at most ~30 keys (the root).
    private var keys: CharArray = NO_KEYS
    private var kids: Array<CompactTrieNode?> = NO_KIDS

    val childCount: Int get() = keys.size
    fun childChar(index: Int): Char = keys[index]
    fun childAt(index: Int): CompactTrieNode = kids[index]!!

    fun child(ch: Char): CompactTrieNode? {
        for (i in keys.indices) if (keys[i] == ch) return kids[i]
        return null
    }

    fun childOrCreate(ch: Char): CompactTrieNode {
        var at = 0
        while (at < keys.size && keys[at] < ch) at++
        if (at < keys.size && keys[at] == ch) return kids[at]!!
        val created = CompactTrieNode()
        val newKeys = CharArray(keys.size + 1)
        val newKids = arrayOfNulls<CompactTrieNode>(kids.size + 1)
        System.arraycopy(keys, 0, newKeys, 0, at)
        System.arraycopy(kids, 0, newKids, 0, at)
        newKeys[at] = ch
        newKids[at] = created
        System.arraycopy(keys, at, newKeys, at + 1, keys.size - at)
        System.arraycopy(kids, at, newKids, at + 1, kids.size - at)
        keys = newKeys
        kids = newKids
        return created
    }

    private companion object {
        val NO_KEYS = CharArray(0)
        val NO_KIDS = arrayOfNulls<CompactTrieNode>(0)
    }
    // Top MAX_TOP_ENTRIES entradas ordenadas por frecuencia descendente.
    // El orden se mantiene en todo momento para que insert() sea O(log K) en
    // lugar de reordenar la lista entera en cada nodo tocado.
    // Most nodes (the deep ones) hold one or two words: start small and let it grow up to MAX_TOP_ENTRIES instead of
    // reserving 15 slots in each of the ~250,000 nodes of a language.
    val topEntries = ArrayList<DictionaryEntry>(2)
}

class CompactWordTrie {
    private val root = CompactTrieNode()
    private val allEntries = ArrayList<DictionaryEntry>()
    private val exactWordMap = HashMap<String, DictionaryEntry>()

    fun insert(entry: DictionaryEntry) {
        allEntries.add(entry)
        exactWordMap[entry.word.lowercase(Locale.ROOT)] = entry

        val norm = entry.normalized
        var current = root
        addTopEntry(current, entry)

        for (ch in norm) {
            val child = current.childOrCreate(ch)
            addTopEntry(child, entry)
            current = child
        }
        current.isWord = true
        if (current.entry == null || current.entry!!.frequency < entry.frequency) {
            current.entry = entry
        }
    }

    /**
     * Inserta [entry] en la lista top del nodo manteniendo el orden por
     * frecuencia descendente y el tope de [MAX_TOP_ENTRIES].
     *
     * La versión anterior hacía `sortByDescending` en cada nodo por cada
     * palabra, lo que es O(n · longitud · K log K) con una asignación de array
     * por llamada. Con las 76.440 palabras del corpus español eso son ~690.000
     * ordenaciones y miles de arrays intermedios: varios segundos de CPU en el
     * hilo de UI durante el primer arranque.
     *
     * Aquí la posición se localiza por búsqueda binaria (la lista siempre está
     * ordenada) y solo se hace el desplazamiento de memoria del `ArrayList`,
     * sin asignaciones. Coste O(log K + K) por nodo, con K = 15.
     */
    private fun addTopEntry(node: CompactTrieNode, entry: DictionaryEntry) {
        val list = node.topEntries

        // Primera posición cuyo elemento tiene frecuencia estrictamente menor
        // que la de [entry]; es decir, tras todas las de frecuencia >= entry.
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].frequency >= entry.frequency) lo = mid + 1 else hi = mid
        }

        // La palabra ya puede estar en la lista con otra frecuencia (la misma
        // palabra insertada dos veces). Solo hay K entradas, así que barrer la
        // cola desde `lo` cuesta O(K), el mismo orden que el desplazamiento.
        for (i in lo until list.size) {
            if (list[i].word.equals(entry.word, ignoreCase = true)) {
                if (list[i].frequency < entry.frequency) list[i] = entry
                return
            }
        }
        for (i in 0 until minOf(lo, list.size)) {
            if (list[i].word.equals(entry.word, ignoreCase = true)) {
                if (list[i].frequency < entry.frequency) list[i] = entry
                return
            }
        }

        // Fuera del top-K: se descarta sin tocar la lista.
        if (lo >= MAX_TOP_ENTRIES) return

        list.add(lo, entry)
        if (list.size > MAX_TOP_ENTRIES) {
            list.removeAt(list.size - 1)
        }
    }

    fun contains(word: String): Boolean {
        return exactWordMap.containsKey(word.lowercase(Locale.ROOT))
    }

    fun getFrequency(word: String): Int {
        return exactWordMap[word.lowercase(Locale.ROOT)]?.frequency ?: 0
    }

    /** The entry [getFrequency] reads for [word], or null; its `normalized` String is shared, not a new copy. */
    fun entryFor(word: String): DictionaryEntry? = exactWordMap[word.lowercase(Locale.ROOT)]

    fun wordsWithPrefix(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()
        val norm = normalize(prefix)
        var current = root
        for (ch in norm) {
            val child = current.child(ch) ?: return emptyList()
            current = child
        }
        return current.topEntries
            .take(limit)
            .map { it.word }
    }

    fun getAllWords(): List<String> = allEntries.map { it.word }

    /**
     * Recoge las palabras del trie a distancia de edición <= [maxDistance] de
     * [query], usando los mismos costes de sustitución por adyacencia de tecla
     * que [LanguageDictionary.proximityDamerauLevenshtein].
     *
     * La versión anterior de la búsqueda difusa recorría `getAllWords()`, es
     * decir, las 76.440 palabras del diccionario, y construía una matriz de
     * programación dinámica nueva por candidata. Eso son ~76.000 matrices por
     * pulsación en el hilo de UI: cientos de MB de basura y un ANR garantizado.
     *
     * Aquí se recorre el trie lleva una sola fila de DP por profundidad y se
     * poda cualquier subárbol cuya distancia mínima supere el umbral, así que
     * el coste depende del tamaño del vecindario real (decenas de nodos) y no
     * del tamaño del diccionario. Las filas se reutilizan entre ramas para no
     * asignar por nodo.
     *
     * El barrido no modela transposiciones (requiere el carácter previo del
     * padre), por eso devuelve candidatos y quien llama vuelve a puntuarlos con
     * la distancia completa. Con un margen de 2 el filtro posterior es exacto.
     */
    fun collectWithinDistance(query: String, maxDistance: Int): List<String> {
        if (query.isEmpty()) return emptyList()

        val n = query.length
        // Un candidato a distancia <= maxDistance puede tener como mucho
        // maxDistance caracteres más que la consulta, así que el trie puede
        // tener que descender n + maxDistance niveles. Dimensionar las filas por
        // la longitud de la consulta truncaba la búsqueda en palabras más
        // largas que la tecleada ("casa" desaparecía frente a "csa").
        val maxDepth = (n + maxDistance).coerceAtMost(MAX_WORD_DEPTH)
        val rows = Array(maxDepth + 1) { DoubleArray(n + 1) }
        val collected = ArrayList<String>()

        // Fila de la raíz: insertar j caracteres en la cadena vacía cuesta j.
        rows[0].indices.forEach { rows[0][it] = it.toDouble() }

        walk(root, rows, 0, query, n, maxDistance, collected)
        return collected
    }

    private fun walk(
        node: CompactTrieNode,
        rows: Array<DoubleArray>,
        depth: Int,
        query: String,
        n: Int,
        maxDistance: Int,
        collected: MutableList<String>,
    ) {
        if (depth >= rows.size - 1) return

        for (index in 0 until node.childCount) {
            val ch = node.childChar(index)
            val child = node.childAt(index)
            val prev = rows[depth]
            val cur = rows[depth + 1]

            cur[0] = prev[0] + 1.0
            var rowMin = Double.MAX_VALUE
            for (j in 1..n) {
                val c = query[j - 1]
                val cost = when {
                    c == ch -> 0.0
                    ADJACENT_KEYS[c]?.contains(ch) == true -> ADJACENT_SUBSTITUTION_COST
                    else -> 1.0
                }
                val del = prev[j] + 1.0
                val ins = cur[j - 1] + 1.0
                val sub = prev[j - 1] + cost
                val best = if (del <= ins) {
                    if (del <= sub) del else sub
                } else if (ins <= sub) ins else sub
                cur[j] = best
                if (best < rowMin) rowMin = best
            }

            if (rowMin > maxDistance) continue

            if (child.isWord) {
                val entry = child.entry
                if (entry != null && cur[n] <= maxDistance) {
                    collected.add(entry.word)
                }
            }
            walk(child, rows, depth + 1, query, n, maxDistance, collected)
        }
    }

    companion object {
        /** Profundidad máxima de trie usada para dimensionar las filas de DP. */
        private const val MAX_WORD_DEPTH = 64

        /** Coste de sustituir una tecla por una vecina en el teclado. */
        const val ADJACENT_SUBSTITUTION_COST = 0.6

        /**
         * Teclas vecinas en QWERTY. Vive aquí y no en `LanguageDictionary`
         * porque tanto el ranking exacto como el barrido del trie necesitan
         * aplicar la misma métrica.
         */
        val ADJACENT_KEYS: Map<Char, Set<Char>> = mapOf(
            'q' to setOf('w', 'a', 's'),
            'w' to setOf('q', 'e', 'a', 's', 'd'),
            'e' to setOf('w', 'r', 's', 'd', 'f'),
            'r' to setOf('e', 't', 'd', 'f', 'g'),
            't' to setOf('r', 'y', 'z', 'd', 'f', 'g', 'h'),
            'y' to setOf('t', 'u', 'z', 'g', 'h', 'j'),
            'z' to setOf('a', 's', 'x', 't', 'u'),
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
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

        fun normalize(raw: String): String = raw.lowercase(Locale.ROOT).map { ch ->
            when (ch) {
                'á', 'à', 'ä', 'â', 'ã', 'å', 'æ' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô', 'õ', 'ø' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'ñ'
                'ç' -> 'c'
                'ß' -> 's'
                else -> ch
            }
        }.joinToString("")
    }
}
