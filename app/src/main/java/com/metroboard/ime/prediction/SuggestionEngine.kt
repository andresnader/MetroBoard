package com.metroboard.ime.prediction

fun interface SuggestionEngine {
    fun suggest(prefix: String): List<String>
    fun setLanguage(language: String) = Unit
    fun suggestNext(previousWord: String): List<String> = emptyList()
    fun autoCorrect(word: String): String? = null
}
