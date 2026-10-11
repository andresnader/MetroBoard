package com.metroboard.ime.keyboard

enum class KeyAction { TEXT, SHIFT, DELETE, SPACE, ENTER, SYMBOLS, LETTERS, MORE_SYMBOLS, EMOJI }
enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS }

data class KeySpec(
    val label: String,
    val action: KeyAction = KeyAction.TEXT,
    val weight: Float = 1f,
    val alternatives: List<String> = emptyList(),
)

object KeyboardLayout {
    val punctuation = listOf(",", "?", "!", ":", ";")
    private val accents = mapOf(
        'a' to listOf("á", "à", "ä"), 'e' to listOf("é", "è", "ë"),
        'i' to listOf("í", "ì", "ï"), 'o' to listOf("ó", "ò", "ö"),
        'u' to listOf("ú", "ü", "ù"), 'n' to listOf("ñ"),
    )
    private fun letters(text: String) = text.map { KeySpec(it.toString(), alternatives = accents[it].orEmpty()) }
    private fun symbols(vararg values: String) = values.map { KeySpec(it) }
    private val delete = KeySpec("", KeyAction.DELETE, 1.5f)
    private val point = KeySpec(".", alternatives = punctuation)

    fun rows(page: KeyboardPage, numberRow: Boolean = false, language: String = "en"): List<List<KeySpec>> {
        val spaceText = if (language == "es") "espacio" else "space"
        val bottom = listOf(
            KeySpec(if (page == KeyboardPage.LETTERS) "&123" else "abc",
                if (page == KeyboardPage.LETTERS) KeyAction.SYMBOLS else KeyAction.LETTERS, 1.5f),
            KeySpec("", KeyAction.EMOJI, 1.2f),
            KeySpec(spaceText, KeyAction.SPACE, 4.6f), point,
            KeySpec("", KeyAction.ENTER, 1.7f),
        )
        return when (page) {
            KeyboardPage.LETTERS -> {
                val numRow = if (numberRow) listOf(symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) else emptyList()
                val alphaRows = if (language == "es") {
                    listOf(
                        letters("qwertyuiop"),
                        letters("asdfghjklñ"),
                        listOf(KeySpec("", KeyAction.SHIFT, 1.2f)) + letters("zxcvbnm") + listOf(KeySpec("", KeyAction.DELETE, 1.2f)),
                        bottom
                    )
                } else {
                    listOf(
                        letters("qwertyuiop"),
                        letters("asdfghjkl"),
                        listOf(KeySpec("", KeyAction.SHIFT, 1.5f)) + letters("zxcvbnm") + delete,
                        bottom
                    )
                }
                numRow + alphaRows
            }
            KeyboardPage.SYMBOLS -> listOf(
                symbols("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                symbols("@", "#", "$", "%", "&", "*", "(", ")", "-", "+"),
                listOf(KeySpec("=\\<", KeyAction.MORE_SYMBOLS, 1.5f)) +
                    symbols(",", "?", "!", "'", "\"", ":", "/") + delete, bottom,
            )
            KeyboardPage.MORE_SYMBOLS -> listOf(
                symbols("[", "]", "{", "}", "<", ">", "=", "_", "\\", "|"),
                symbols("~", "`", "^", "€", "£", "¥", "¢", "°", "¿", "¡"),
                listOf(KeySpec("123", KeyAction.SYMBOLS, 1.5f)) +
                    symbols(";", ",", "?", "!", ":", "/", "…") + delete, bottom,
            )
        }
    }

    /** Android-specific field layouts are an adaptation; historical Q002 is open. */
    fun numeric(phone: Boolean = false, decimal: Boolean = false, signed: Boolean = false,
                datetime: Boolean = false): List<List<KeySpec>> = listOf(
        symbols("1", "2", "3") + KeySpec("", KeyAction.DELETE),
        symbols("4", "5", "6") + KeySpec(if (phone) "+" else if (signed || datetime) "-" else ""),
        symbols("7", "8", "9") + KeySpec(if (phone) "*" else if (datetime) ":" else if (decimal) "." else ""),
        listOf(KeySpec(if (phone) "#" else if (datetime) "/" else ""), KeySpec("0"),
            if (phone || datetime) KeySpec("space", KeyAction.SPACE) else KeySpec(""),
            KeySpec("", KeyAction.ENTER)),
    )
}
