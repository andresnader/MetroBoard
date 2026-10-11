/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

/** Minimal editing boundary. Implementations must not retain entered text. */
interface EditorPort {
    fun beforeCursor(maxChars: Int): String
    fun afterCursor(maxChars: Int): String
    fun hasSelection(): Boolean

    /** The selected text, or null when nothing is selected or the editor does not expose it. */
    fun selectedText(): String? = null

    /** Replaces the selection with [text] and keeps the new text selected (repeated Shift taps cycle it). */
    fun replaceSelectionKeepingIt(text: String): Boolean = commit(text)
    fun shouldCapitalize(): Boolean
    fun commit(text: String): Boolean
    fun deleteBeforeCursor(codePoints: Int): Boolean
    fun enter(): Boolean
    fun beginBatch()
    fun endBatch()
}

data class EditingPolicy(
    val automaticCapitalization: Boolean = true,
    val doubleSpace: Boolean = true,
    val suggestions: Boolean = true,
    val forceUppercase: Boolean = false,
    val autoCorrect: Boolean = true,
    val shiftWordCasing: Boolean = true,
    /** Remember new words the user types. Off for sensitive fields and no-personalized-learning editors. */
    val learnWords: Boolean = false,
)
