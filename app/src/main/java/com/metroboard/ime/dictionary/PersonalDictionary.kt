/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

interface PersonalDictionary {
    fun wordsWithPrefix(prefix: String): List<String>
    fun learn(word: String)
    fun clear()

    /** Words learned for [language] that are frequent enough to be suggested. */
    fun wordsWithPrefix(prefix: String, language: String): List<String> = wordsWithPrefix(prefix)

    /** Records one use of [word] in [language]. */
    fun learn(word: String, language: String) = learn(word)

    /**
     * The user explicitly insisted on [word] (for example by undoing an autocorrection): it
     * counts as already used often enough to be protected and suggested.
     */
    fun keep(word: String, language: String) {
        learn(word, language)
        learn(word, language)
    }

    /**
     * True when [word] is established in [language] (used repeatedly or explicitly kept); such a
     * word is never auto-corrected. A word seen once may just be a typo and stays unprotected.
     */
    fun knows(word: String, language: String): Boolean =
        wordsWithPrefix(word, language).any { it.equals(word, ignoreCase = true) }
}

/** v0.1 deliberately stores nothing. No automatic learning in any field. */
object DisabledPersonalDictionary : PersonalDictionary {
    override fun wordsWithPrefix(prefix: String) = emptyList<String>()
    override fun learn(word: String) = Unit
    override fun clear() = Unit
}

fun interface ContactDictionaryProvider {
    fun words(): List<String>
}

object NoContactDictionaryProvider : ContactDictionaryProvider {
    override fun words() = emptyList<String>()
}
