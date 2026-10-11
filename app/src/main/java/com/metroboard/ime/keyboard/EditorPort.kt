package com.metroboard.ime.keyboard

/** Minimal editing boundary. Implementations must not retain entered text. */
interface EditorPort {
    fun beforeCursor(maxChars: Int): String
    fun afterCursor(maxChars: Int): String
    fun hasSelection(): Boolean
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
)
