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
}
