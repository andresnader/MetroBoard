/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object LanguageDictionaryRegistry {
    private val loadedDictionaries = HashMap<String, DictionaryProvider>()

    /**
     * Instancia creada sin `Context`, es decir, el diccionario de respaldo.
     * No se cachea a propósito: si se cacheara, la primera llamada sin
     * `Context` envenenaría el cache de forma permanente y ninguna llamada
     * posterior con `Context` real llegaría a leer los assets.
     */
    private val fallbackDictionaries = HashMap<String, DictionaryProvider>()

    private val lock = Any()

    fun get(language: String, context: Context? = null): DictionaryProvider {
        val lang = language.lowercase()
        // Sin Context no hay forma de leer assets, así que se sirve el
        // respaldo sin tocar la caché principal.
        if (context == null) {
            synchronized(lock) {
                return fallbackDictionaries.getOrPut(lang) { createFallbackDictionary(lang) }
            }
        }

        synchronized(lock) {
            loadedDictionaries[lang]?.let { return it }
        }

        // La carga real (parseo + trie) puede tardar, así que se hace fuera
        // del lock y luego se publica en la caché.
        val loaded = load(lang, context)
        synchronized(lock) {
            // Otra hebra puede haber terminado antes; se respeta la primera.
            return loadedDictionaries.getOrPut(lang) { loaded }
        }
    }

    fun clearCache() {
        synchronized(lock) {
            loadedDictionaries.clear()
            fallbackDictionaries.clear()
        }
    }

    private fun load(lang: String, context: Context?): DictionaryProvider {
        if (context == null) return createFallbackDictionary(lang)
        return loadFromAssets(lang) { path -> context.assets.open(path) }
    }

    /**
     * Builds a dictionary from `dictionaries/<lang>.{dict,typos,bigrams,trigrams}` read through
     * [open]. Android passes `AssetManager.open`; JVM unit tests pass a file reader so they can
     * exercise the real shipped dictionaries instead of the tiny fallback lists.
     */
    internal fun loadFromAssets(lang: String, open: (String) -> java.io.InputStream): DictionaryProvider {
        val words = ArrayList<Pair<String, Int>>()
        val typos = HashMap<String, String>()
        val bigrams = HashMap<String, MutableList<String>>()
        val trigrams = HashMap<String, MutableList<String>>()

        // Mandatory file: without a word list the language falls back to the built-in sample.
        val loadedWords = try {
            readTsv(open, "dictionaries/$lang.dict") { parts ->
                if (parts.size >= 2) {
                    val word = parts[0].trim()
                    val freq = parts[1].trim().toIntOrNull() ?: 100
                    if (word.isNotEmpty()) words.add(word to freq)
                }
            }
            words.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (!loadedWords) return createFallbackDictionary(lang)

        // Optional files: a missing one only disables that feature for the language.
        runCatching {
            readTsv(open, "dictionaries/$lang.typos") { parts ->
                if (parts.size >= 2) typos[CompactWordTrie.normalize(parts[0].trim())] = parts[1].trim()
            }
        }
        runCatching {
            readTsv(open, "dictionaries/$lang.bigrams") { parts ->
                if (parts.size >= 2) {
                    bigrams.getOrPut(CompactWordTrie.normalize(parts[0].trim())) { ArrayList() }.add(parts[1].trim())
                }
            }
        }
        runCatching {
            readTsv(open, "dictionaries/$lang.trigrams") { parts ->
                if (parts.size >= 3) {
                    val key = "${CompactWordTrie.normalize(parts[0].trim())} ${CompactWordTrie.normalize(parts[1].trim())}"
                    trigrams.getOrPut(key) { ArrayList() }.add(parts[2].trim())
                }
            }
        }

        val defaultLayout = when (lang) {
            "fr" -> "AZERTY"
            "de" -> "QWERTZ"
            else -> "QWERTY"
        }

        return LanguageDictionary(
            language = lang,
            entries = words,
            typos = typos,
            bigrams = bigrams,
            trigrams = trigrams,
            defaultLayout = defaultLayout
        )
    }

    private fun readTsv(open: (String) -> java.io.InputStream, path: String, onRow: (List<String>) -> Unit) {
        open(path).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { onRow(it.split("\t")) }
        }
    }

    private fun createFallbackDictionary(lang: String): DictionaryProvider {
        return when (lang) {
            "es" -> LanguageDictionary(
                language = "es",
                entries = FALLBACK_ES_WORDS,
                typos = mapOf(
                    "tambien" to "también", "mas" to "más", "dia" to "día",
                    "esta" to "está", "aqui" to "aquí", "facil" to "fácil"
                ),
                bigrams = mapOf(
                    "gracias" to listOf("por", "a"),
                    "muchas" to listOf("gracias"),
                    "buenos" to listOf("días"),
                    "buenas" to listOf("tardes", "noches"),
                    "cómo" to listOf("estás", "está"),
                    "por" to listOf("favor", "ejemplo")
                ),
                trigrams = mapOf(
                    "muchas gracias" to listOf("por", "de"),
                    "cómo estás" to listOf("hoy"),
                    "buenos días" to listOf("a", "amigo"),
                    "por favor" to listOf("avísame", "envíame"),
                    "nos vemos" to listOf("luego", "pronto")
                ),
                defaultLayout = "QWERTY"
            )
            "en" -> LanguageDictionary(
                language = "en",
                entries = FALLBACK_EN_WORDS,
                typos = mapOf(
                    "dont" to "don't", "cant" to "can't", "wont" to "won't",
                    "im" to "I'm", "teh" to "the", "adn" to "and"
                ),
                bigrams = mapOf(
                    "thank" to listOf("you"),
                    "good" to listOf("morning", "night"),
                    "how" to listOf("are", "is"),
                    "see" to listOf("you")
                ),
                trigrams = mapOf(
                    "thank you" to listOf("very", "so", "for"),
                    "how are" to listOf("you"),
                    "see you" to listOf("later", "soon", "tomorrow"),
                    "have a" to listOf("great", "good", "nice"),
                    "let me" to listOf("know")
                ),
                defaultLayout = "QWERTY"
            )
            "fr" -> LanguageDictionary(
                language = "fr",
                entries = FALLBACK_FR_WORDS,
                typos = mapOf(
                    "cest" to "c'est", "jai" to "j'ai", "tres" to "très",
                    "francais" to "français", "deja" to "déjà"
                ),
                bigrams = mapOf(
                    "merci" to listOf("beaucoup"),
                    "bonjour" to listOf("comment"),
                    "s'il" to listOf("vous"),
                    "au" to listOf("revoir")
                ),
                trigrams = mapOf(
                    "s'il vous" to listOf("plaît"),
                    "merci beaucoup" to listOf("pour"),
                    "comment allez" to listOf("vous")
                ),
                defaultLayout = "AZERTY"
            )
            "de" -> LanguageDictionary(
                language = "de",
                entries = FALLBACK_DE_WORDS,
                typos = mapOf(
                    "uber" to "über", "konnen" to "können", "mussen" to "müssen",
                    "schon" to "schön", "gross" to "groß", "dankee" to "danke"
                ),
                bigrams = mapOf(
                    "vielen" to listOf("dank"),
                    "guten" to listOf("morgen", "tag", "abend"),
                    "wie" to listOf("geht's"),
                    "auf" to listOf("wiedersehen")
                ),
                trigrams = mapOf(
                    "vielen dank" to listOf("für"),
                    "wie geht" to listOf("es"),
                    "bis später" to listOf("mein")
                ),
                defaultLayout = "QWERTZ"
            )
            else -> createFallbackDictionary("es")
        }
    }

    private val FALLBACK_ES_WORDS = listOf(
        "hola" to 220, "gracias" to 215, "amigo" to 200, "casa" to 195, "día" to 190,
        "hoy" to 185, "mañana" to 180, "tiempo" to 175, "trabajo" to 170, "vida" to 165,
        "cómo" to 160, "estoy" to 155, "está" to 150, "bien" to 145, "bueno" to 140,
        "también" to 135, "ahora" to 130, "después" to 125, "donde" to 120, "hacer" to 115,
        "año" to 110, "años" to 105, "niño" to 100, "teclado" to 95, "mensaje" to 90,
        "vamos" to 85, "quiero" to 80, "puedo" to 75, "tenemos" to 70, "para" to 65,
        "pero" to 60, "porque" to 55, "siempre" to 50, "nunca" to 45, "todos" to 40
    )

    private val FALLBACK_EN_WORDS = listOf(
        "the" to 255, "and" to 250, "you" to 245, "that" to 240, "have" to 235,
        "for" to 230, "not" to 225, "with" to 220, "you" to 215, "this" to 210,
        "but" to 205, "from" to 200, "they" to 195, "hello" to 190, "thanks" to 185,
        "good" to 180, "morning" to 175, "night" to 170, "keyboard" to 165, "message" to 160,
        "phone" to 155, "work" to 150, "today" to 145, "tomorrow" to 140, "great" to 135,
        "time" to 130, "about" to 125, "there" to 120, "which" to 115, "would" to 110
    )

    private val FALLBACK_FR_WORDS = listOf(
        "le" to 255, "la" to 250, "et" to 245, "les" to 240, "des" to 235,
        "un" to 230, "une" to 225, "pour" to 220, "dans" to 215, "avec" to 210,
        "bonjour" to 205, "merci" to 200, "oui" to 195, "non" to 190, "ami" to 185,
        "maison" to 180, "temps" to 175, "travail" to 170, "clavier" to 165, "message" to 160,
        "français" to 155, "très" to 150, "toujours" to 145, "jamais" to 140, "faire" to 135,
        "été" to 130, "être" to 125, "avoir" to 120, "aller" to 115, "voir" to 110
    )

    private val FALLBACK_DE_WORDS = listOf(
        "der" to 255, "die" to 250, "das" to 245, "und" to 240, "in" to 235,
        "den" to 230, "von" to 225, "zu" to 220, "mit" to 215, "sich" to 210,
        "hallo" to 205, "danke" to 200, "bitte" to 195, "ja" to 190, "nein" to 185,
        "gut" to 180, "guten" to 175, "morgen" to 170, "tag" to 165, "abend" to 160,
        "tastatur" to 155, "nachricht" to 150, "freund" to 145, "haus" to 140, "zeit" to 135,
        "groß" to 130, "schön" to 125, "über" to 120, "können" to 115, "müssen" to 110
    )
}
