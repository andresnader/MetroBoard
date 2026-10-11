/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.language

/**
 * Language-specific rules that are not word data. Word data (lexicon, frequencies, typos, bigrams)
 * lives in the dictionary assets and is read through LanguageDictionaryRegistry; the default key
 * layout stays in KeyboardLayout.defaultLayoutFor so there is one source of truth for each fact.
 */
data class LanguagePack(
    val code: String,
    /** Default regional variant used for locale-sensitive behaviour. */
    val locale: String,
    /**
     * Elision languages (French: "l'ami", "qu'il", "c'est") write two words joined by an apostrophe.
     * The apostrophe ends the previous token, so prediction applies to what follows it.
     * In English the apostrophe stays inside the word ("don't", "John's").
     */
    val elidesApostrophe: Boolean = false,
)

object LanguagePacks {
    val all: List<LanguagePack> = listOf(
        LanguagePack(code = "es", locale = "es-EC"),
        LanguagePack(code = "en", locale = "en-US"),
        LanguagePack(code = "fr", locale = "fr-FR", elidesApostrophe = true),
        LanguagePack(code = "de", locale = "de-DE"),
    )

    fun of(code: String): LanguagePack = all.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: all.first()
}
