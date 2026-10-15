/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class LearnedWord(
    val word: String,
    var frequency: Int = 1,
    val language: String = "es"
)

class LocalPersonalDictionary(
    private val storageDir: File? = null
) : PersonalDictionary {

    constructor(context: Context) : this(context.filesDir)

    private val wordMap = HashMap<String, MutableMap<String, LearnedWord>>() // language -> (lowercaseWord -> LearnedWord)

    companion object {
        /** Uses before a learned word is protected from autocorrect and offered as a suggestion. */
        const val MIN_USES_TO_SUGGEST = 2

        private val WORD = Regex("^\\p{L}[\\p{L}'\u2019-]*$")

        /**
         * The word as it will be stored, or null when it cannot be one: 2 to 40 characters, starting with a
         * letter, made only of letters, apostrophes and hyphens (no spaces, digits or symbols).
         */
        fun clean(raw: String): String? = raw.trim().takeIf { it.length in 2..40 && WORD.matches(it) }

        private val shared = HashMap<String, LocalPersonalDictionary>()

        /**
         * The one dictionary for [storageDir] in this process. Settings and the keyboard service must use the same
         * object: with separate copies the stale one rewrites the file on its next save and undoes the other's edits.
         */
        @Synchronized
        fun shared(storageDir: File): LocalPersonalDictionary =
            shared.getOrPut(storageDir.canonicalPath) { LocalPersonalDictionary(storageDir) }

        fun shared(context: Context): LocalPersonalDictionary = shared(context.filesDir)

        /**
         * The one thread that writes the dictionaries' files. The keyboard learns a word on the space after it, and that
         * used to rewrite the file (and rename it into place) on the UI thread: a rename can stall for tens of
         * milliseconds on a busy phone. One thread keeps the writes of a file in order.
         */
        private val writer = java.util.concurrent.ThreadPoolExecutor(
            1, 1, 30, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.LinkedBlockingQueue(),
        ) { task -> Thread(task, "metroboard-dictionary-io").apply { isDaemon = true } }.apply { allowCoreThreadTimeOut(true) }

        /** Waits until every write queued so far is on disk. */
        fun flushWrites() {
            writer.submit {}.get()
        }

        /** Runs [task] on the writer thread, behind the writes already queued (tests use it to hold the writer). */
        internal fun onWriterThread(task: Runnable) = writer.execute(task)
    }

    /** A write is queued; it takes the words as they are when it runs, so later changes need no other one. */
    private var writeQueued = false

    init {
        loadFromFile()
    }

    @Synchronized
    override fun wordsWithPrefix(prefix: String): List<String> = wordsWithPrefix(prefix, "es")

    @Synchronized
    override fun words(language: String): List<String> =
        (wordMap[language.lowercase(Locale.ROOT)]?.values ?: emptyList())
            .filter { it.frequency >= MIN_USES_TO_SUGGEST }
            .sortedByDescending { it.frequency }
            .map { it.word }

    @Synchronized
    override fun knows(word: String, language: String): Boolean =
        (wordMap[language.lowercase(Locale.ROOT)]?.get(word.trim().lowercase(Locale.ROOT))?.frequency ?: 0) >= MIN_USES_TO_SUGGEST

    @Synchronized
    override fun wordsWithPrefix(prefix: String, language: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        val langMap = wordMap[language.lowercase(Locale.ROOT)] ?: return emptyList()
        val norm = prefix.lowercase(Locale.ROOT)
        return langMap.values
            // A word seen once may be a typo: it is neither suggested nor protected until reused.
            .filter { it.frequency >= MIN_USES_TO_SUGGEST }
            .filter { it.word.lowercase(Locale.ROOT).startsWith(norm) }
            .sortedByDescending { it.frequency }
            .map { it.word }
    }

    @Synchronized
    override fun learn(word: String) = learn(word, "es")

    @Synchronized
    override fun learn(word: String, language: String) {
        val trimmed = word.trim()
        if (trimmed.length < 2 || trimmed.length > 40) return
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap.getOrPut(lang) { HashMap() }
        val key = trimmed.lowercase(Locale.ROOT)

        val existing = langMap[key]
        if (existing != null) {
            existing.frequency++
        } else {
            langMap[key] = LearnedWord(trimmed, frequency = 1, language = lang)
        }
        saveToFile()
    }

    /**
     * Adds a word the user typed by hand. It counts as already used often enough to be suggested and
     * protected from autocorrect, keeps the capitalisation given, and fixes the spelling of an existing
     * entry instead of duplicating it. False (nothing stored) when [raw] cannot be a word, see [clean].
     */
    @Synchronized
    fun addWord(raw: String, language: String): Boolean {
        val word = clean(raw) ?: return false
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap.getOrPut(lang) { HashMap() }
        val key = word.lowercase(Locale.ROOT)
        langMap[key] = LearnedWord(word, maxOf(langMap[key]?.frequency ?: 0, MIN_USES_TO_SUGGEST), lang)
        saveToFile()
        return true
    }

    /**
     * Changes the spelling of [old]. It keeps its count; when [raw] already exists the two merge and the
     * higher count wins. False, and nothing changes, when [old] is missing or [raw] cannot be a word.
     */
    @Synchronized
    fun renameWord(old: String, raw: String, language: String): Boolean {
        val word = clean(raw) ?: return false
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap[lang] ?: return false
        val current = langMap[old.trim().lowercase(Locale.ROOT)] ?: return false
        val key = word.lowercase(Locale.ROOT)
        val count = maxOf(current.frequency, langMap[key]?.frequency ?: 0)
        langMap.remove(old.trim().lowercase(Locale.ROOT))
        langMap[key] = LearnedWord(word, count, lang)
        saveToFile()
        return true
    }

    @Synchronized
    fun remove(word: String, language: String): Boolean {
        val lang = language.lowercase(Locale.ROOT)
        val langMap = wordMap[lang] ?: return false
        val removed = langMap.remove(word.lowercase(Locale.ROOT)) != null
        if (removed) saveToFile()
        return removed
    }

    @Synchronized
    fun getAllWords(language: String): List<LearnedWord> {
        val lang = language.lowercase(Locale.ROOT)
        return (wordMap[lang]?.values ?: emptyList()).sortedByDescending { it.frequency }
    }

    @Synchronized
    override fun clear() {
        wordMap.clear()
        saveToFile()
    }

    @Synchronized
    fun clearLanguage(language: String) {
        wordMap.remove(language.lowercase(Locale.ROOT))
        saveToFile()
    }

    @Synchronized
    fun exportJson(language: String): String {
        val lang = language.lowercase(Locale.ROOT)
        val words = getAllWords(lang)
        val root = JSONObject()
        root.put("version", 1)
        root.put("language", lang)
        val array = JSONArray()
        for (item in words) {
            val obj = JSONObject()
            obj.put("word", item.word)
            obj.put("frequency", item.frequency)
            array.put(obj)
        }
        root.put("words", array)
        return root.toString(2)
    }

    /** Every learned word of every language, for a backup. */
    @Synchronized
    fun allWords(): Map<String, List<LearnedWord>> =
        wordMap.mapValues { (_, words) -> words.values.sortedByDescending { it.frequency } }.filterValues { it.isNotEmpty() }

    /** Merges already validated words (see [BackupFormat]); an existing word keeps its higher count. */
    @Synchronized
    fun importWords(words: Map<String, List<LearnedWord>>): Int {
        var count = 0
        for ((lang, list) in words) {
            val langMap = wordMap.getOrPut(lang.lowercase(Locale.ROOT)) { HashMap() }
            for (w in list) {
                val key = w.word.lowercase(Locale.ROOT)
                val existing = langMap[key]
                if (existing != null) existing.frequency = maxOf(existing.frequency, w.frequency)
                else langMap[key] = LearnedWord(w.word, w.frequency, lang)
                count++
            }
        }
        if (count > 0) saveToFile()
        return count
    }

    /** Validated import of a backup's words. Throws [BackupFormat.InvalidBackup]. */
    @Synchronized
    fun importJson(jsonStr: String): Int = importWords(BackupFormat.parse(jsonStr).words)

    @Synchronized
    fun importFromSystemUserDictionary(context: Context): Int {
        var count = 0
        try {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                android.provider.UserDictionary.Words.CONTENT_URI,
                arrayOf(
                    android.provider.UserDictionary.Words.WORD,
                    android.provider.UserDictionary.Words.FREQUENCY,
                    android.provider.UserDictionary.Words.LOCALE
                ),
                null,
                null,
                null
            )
            cursor?.use { c ->
                val wordCol = c.getColumnIndex(android.provider.UserDictionary.Words.WORD)
                val freqCol = c.getColumnIndex(android.provider.UserDictionary.Words.FREQUENCY)
                val localeCol = c.getColumnIndex(android.provider.UserDictionary.Words.LOCALE)
                while (c.moveToNext()) {
                    val word = if (wordCol >= 0) c.getString(wordCol) else null
                    if (!word.isNullOrBlank()) {
                        val trimmed = word.trim()
                        if (trimmed.length in 2..40) {
                            val freq = if (freqCol >= 0) c.getInt(freqCol).coerceAtLeast(1) else 1
                            val loc = if (localeCol >= 0) c.getString(localeCol)?.take(2)?.lowercase(Locale.ROOT) else null
                            val lang = if (!loc.isNullOrBlank()) loc else "es"
                            val langMap = wordMap.getOrPut(lang) { HashMap() }
                            val key = trimmed.lowercase(Locale.ROOT)
                            val existing = langMap[key]
                            if (existing != null) {
                                existing.frequency = maxOf(existing.frequency, freq)
                            } else {
                                langMap[key] = LearnedWord(trimmed, frequency = freq, language = lang)
                            }
                            count++
                        }
                    }
                }
                if (count > 0) {
                    saveToFile()
                }
            }
        } catch (_: Exception) {}
        return count
    }

    private fun getFile(): File? {
        if (storageDir == null) return null
        return File(storageDir, "personal_dictionary.json")
    }

    /** Queues writing the file on the writer thread; every caller holds this object's lock. */
    private fun saveToFile() {
        if (getFile() == null || writeQueued) return
        writeQueued = true
        writer.execute { writeFile() }
    }

    private fun writeFile() {
        val file = getFile() ?: return
        // A copy of the words taken under the lock (quick, in memory); the JSON and the disk happen outside it.
        val snapshot = synchronized(this) {
            writeQueued = false
            wordMap.mapValues { (_, words) -> words.values.map { it.word to it.frequency } }
        }
        try {
            val root = JSONObject()
            root.put("version", 1)
            val langObj = JSONObject()
            for ((lang, words) in snapshot) {
                val array = JSONArray()
                for ((word, frequency) in words) {
                    val obj = JSONObject()
                    obj.put("word", word)
                    obj.put("frequency", frequency)
                    array.put(obj)
                }
                langObj.put(lang, array)
            }
            root.put("languages", langObj)
            // Write next to the file and move it into place: a crash mid-write can no longer
            // leave a truncated dictionary that silently loses every learned word.
            val temp = java.io.File(file.parentFile, file.name + ".tmp")
            temp.writeText(root.toString(), Charsets.UTF_8)
            java.nio.file.Files.move(
                temp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {}
    }

    private fun loadFromFile() {
        val file = getFile() ?: return
        flushWrites() // another instance may still be writing this file
        if (!file.exists()) return
        try {
            val text = file.readText(Charsets.UTF_8)
            val root = JSONObject(text)
            if (root.has("languages")) {
                val langObj = root.getJSONObject("languages")
                for (lang in langObj.keys()) {
                    val array = langObj.getJSONArray(lang)
                    val map = wordMap.getOrPut(lang) { HashMap() }
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val word = obj.getString("word")
                        val freq = obj.optInt("frequency", 1)
                        map[word.lowercase(Locale.ROOT)] = LearnedWord(word, freq, lang)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
