/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

enum class KeyAction { TEXT, SHIFT, DELETE, SPACE, ENTER, SYMBOLS, LETTERS, MORE_SYMBOLS, EMOJI, CLIPBOARD }
enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS }
enum class KeyboardLayoutType { QWERTY, AZERTY, QWERTZ }

data class KeySpec(
    val label: String,
    val action: KeyAction = KeyAction.TEXT,
    val weight: Float = 1f,
    val alternatives: List<String> = emptyList(),
)

object KeyboardLayout {
    val punctuation = listOf(",", "?", "!", ":", ";", "…")

    fun accentsFor(char: Char, language: String): List<String> = when (char.lowercaseChar()) {
        'a' -> when (language) {
            "fr" -> listOf("à", "â", "ä", "é", "è", "æ", "á")
            "de" -> listOf("ä", "á", "à", "â")
            else -> listOf("á", "à", "â", "ä", "ã", "å", "æ")
        }
        'e' -> when (language) {
            "fr" -> listOf("é", "è", "ê", "ë", "€")
            else -> listOf("é", "è", "ê", "ë")
        }
        'i' -> listOf("í", "ì", "î", "ï")
        'o' -> when (language) {
            "fr" -> listOf("ô", "ö", "œ", "ó", "ò")
            "de" -> listOf("ö", "ó", "ò", "ô", "õ")
            else -> listOf("ó", "ò", "ô", "ö", "õ", "ø")
        }
        'u' -> when (language) {
            "de" -> listOf("ü", "ú", "ù", "û")
            "fr" -> listOf("ù", "û", "ü", "ú")
            else -> listOf("ú", "ù", "û", "ü")
        }
        'n' -> listOf("ñ")
        'c' -> listOf("ç")
        's' -> listOf("ß", "ś", "š", "§")
        'd' -> listOf("ð")
        'y' -> listOf("ý", "ÿ")
        '$' -> listOf("€", "£", "¥", "₩", "₹", "¢")
        '+' -> listOf("−", "×", "÷", "=", "≠", "±", "%", "‰")
        '=' -> listOf("≠", "≈", "≡", "≤", "≥", "±")
        '%' -> listOf("‰", "‱")
        '?' -> listOf("¿")
        '!' -> listOf("¡")
        '.' -> punctuation
        '-' -> listOf("—", "–", "_", "•")
        else -> emptyList()
    }

    private fun letters(text: String, language: String): List<KeySpec> = text.map { ch ->
        KeySpec(ch.toString(), alternatives = accentsFor(ch, language))
    }

    private fun symbols(vararg values: String, language: String = "en"): List<KeySpec> = values.map { sym ->
        val alt = if (sym.isNotEmpty()) accentsFor(sym.first(), language) else emptyList()
        KeySpec(sym, alternatives = alt)
    }

    private val delete = KeySpec("", KeyAction.DELETE, 1.5f)

    fun defaultLayoutFor(language: String): KeyboardLayoutType = when (language) {
        "fr" -> KeyboardLayoutType.AZERTY
        "de" -> KeyboardLayoutType.QWERTZ
        else -> KeyboardLayoutType.QWERTY
    }

    fun rows(
        page: KeyboardPage,
        numberRow: Boolean = false,
        language: String = "en",
        layoutType: KeyboardLayoutType = defaultLayoutFor(language)
    ): List<List<KeySpec>> {
        val spaceText = when (language) {
            "es" -> "espacio"
            "fr" -> "espace"
            "de" -> "leerzeichen"
            else -> "space"
        }

        val point = KeySpec(".", alternatives = punctuation)

        val bottom = listOf(
            KeySpec(if (page == KeyboardPage.LETTERS) "&123" else "abc",
                if (page == KeyboardPage.LETTERS) KeyAction.SYMBOLS else KeyAction.LETTERS, 1.5f),
            KeySpec("", KeyAction.EMOJI, 1.2f),
            KeySpec(spaceText, KeyAction.SPACE, 4.6f),
            point,
            KeySpec("", KeyAction.ENTER, 1.7f),
        )

        return when (page) {
            KeyboardPage.LETTERS -> {
                val numRow = if (numberRow) listOf(symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) else emptyList()
                val alphaRows = when (layoutType) {
                    KeyboardLayoutType.AZERTY -> {
                        listOf(
                            letters("azertyuiop", language),
                            letters("qsdfghjklm", language),
                            listOf(KeySpec("", KeyAction.SHIFT, 1.3f)) + letters("wxcvbn", language) + listOf(KeySpec("", KeyAction.DELETE, 1.3f)),
                            bottom
                        )
                    }
                    KeyboardLayoutType.QWERTZ -> {
                        listOf(
                            letters("qwertzuiop", language),
                            letters("asdfghjkl", language),
                            listOf(KeySpec("", KeyAction.SHIFT, 1.5f)) + letters("yxcvbnm", language) + delete,
                            bottom
                        )
                    }
                    KeyboardLayoutType.QWERTY -> {
                        if (language == "es") {
                            listOf(
                                letters("qwertyuiop", language),
                                letters("asdfghjklñ", language),
                                listOf(KeySpec("", KeyAction.SHIFT, 1.2f)) + letters("zxcvbnm", language) + listOf(KeySpec("", KeyAction.DELETE, 1.2f)),
                                bottom
                            )
                        } else {
                            listOf(
                                letters("qwertyuiop", language),
                                letters("asdfghjkl", language),
                                listOf(KeySpec("", KeyAction.SHIFT, 1.5f)) + letters("zxcvbnm", language) + delete,
                                bottom
                            )
                        }
                    }
                }
                numRow + alphaRows
            }
            KeyboardPage.SYMBOLS -> listOf(
                symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                symbols("@", "#", "$", "%", "&", "*", "(", ")", "-", "+", language = language),
                listOf(KeySpec("=\\<", KeyAction.MORE_SYMBOLS, 1.5f)) +
                    symbols(",", "?", "!", "'", "\"", ":", "/", language = language) + delete,
                bottom
            )
            KeyboardPage.MORE_SYMBOLS -> listOf(
                symbols("[", "]", "{", "}", "<", ">", "=", "_", "\\", "|", language = language),
                symbols("~", "`", "^", "€", "£", "¥", "¢", "°", "¿", "¡", language = language),
                listOf(KeySpec("123", KeyAction.SYMBOLS, 1.5f)) +
                    symbols(";", ",", "?", "!", ":", "/", "…", language = language) + delete,
                bottom
            )
        }
    }

    fun numeric(
        phone: Boolean = false,
        decimal: Boolean = false,
        signed: Boolean = false,
        datetime: Boolean = false
    ): List<List<KeySpec>> = listOf(
        symbols("1", "2", "3") + KeySpec("", KeyAction.DELETE),
        symbols("4", "5", "6") + KeySpec(if (phone) "+" else if (signed || datetime) "-" else ""),
        symbols("7", "8", "9") + KeySpec(if (phone) "*" else if (datetime) ":" else if (decimal) "." else ""),
        listOf(
            KeySpec(if (phone) "#" else if (datetime) "/" else ""),
            KeySpec("0"),
            if (phone || datetime) KeySpec("space", KeyAction.SPACE) else KeySpec(""),
            KeySpec("", KeyAction.ENTER)
        )
    )
}
