package com.windowsphonekeyboard.ime.prediction

fun interface SuggestionEngine {
    fun suggest(prefix: String): List<String>
    fun suggestNext(previousWord: String): List<String> = emptyList()
    fun autoCorrect(word: String): String? = null
}
