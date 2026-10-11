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
