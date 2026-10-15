/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import java.io.File

/** Loads the dictionaries that ship in `app/src/main/assets` so JVM tests exercise real data. */
object RealDictionaries {
    private val assetsDir: File = listOf("src/main/assets", "app/src/main/assets")
        .map(::File).first { File(it, "dictionaries/es.dict").exists() }

    private val cache = HashMap<String, DictionaryProvider>()

    fun get(lang: String): DictionaryProvider = synchronized(cache) {
        cache.getOrPut(lang) {
            LanguageDictionaryRegistry.loadFromAssets(lang) { path -> File(assetsDir, path).inputStream() }
        }
    }
}
