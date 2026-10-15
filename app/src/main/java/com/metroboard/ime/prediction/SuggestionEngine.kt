/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.prediction

fun interface SuggestionEngine {
    fun suggest(prefix: String): List<String>
    fun setLanguage(language: String) = Unit
    fun suggestNext(previousWord: String): List<String> = emptyList()
    fun suggestNextTrigram(word1: String, word2: String): List<String> = emptyList()
    fun autoCorrect(word: String): String? = null

    /**
     * The user finished typing [word]; engines may remember it (never in sensitive fields).
     * [explicit] means the user insisted on it, e.g. by undoing an autocorrection.
     */
    fun learn(word: String, explicit: Boolean = false) = Unit

    /** Alternatives for a word the user selected (typo fixes, accent variants, close words), never the word itself. */
    fun suggestReplacements(word: String): List<String> = emptyList()

    /** True when an apostrophe ends the previous word in the active language (French elision). */
    val elidesApostrophe: Boolean get() = false

    /** True when [word] is a valid word of the active language or was kept by the user. */
    fun isKnownWord(word: String): Boolean = false
}
