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

    /** The user finished typing [word] and kept it; engines may remember it (never in sensitive fields). */
    fun learn(word: String) = Unit

    /** True when [word] is a valid word of the active language or was kept by the user. */
    fun isKnownWord(word: String): Boolean = false
}
