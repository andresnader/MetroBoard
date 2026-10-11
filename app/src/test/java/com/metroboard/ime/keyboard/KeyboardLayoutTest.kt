package com.metroboard.ime.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun testLettersPageStructure() {
        val rows = KeyboardLayout.rows(KeyboardPage.LETTERS)
        assertEquals(4, rows.size)

        // Row 1: QWERTYUIOP (10 keys)
        val row0 = rows[0]
        assertEquals(10, row0.size)
        assertEquals("qwertyuiop", row0.map { it.label }.joinToString(""))

        // Row 2: ASDFGHJKL (9 keys)
        val row1 = rows[1]
        assertEquals(9, row1.size)
        assertEquals("asdfghjkl", row1.map { it.label }.joinToString(""))

        // Row 3: SHIFT + ZXCVBNM + DELETE
        val row2 = rows[2]
        assertEquals(9, row2.size)
        assertEquals(KeyAction.SHIFT, row2.first().action)
        assertEquals(1.5f, row2.first().weight)
        assertEquals(KeyAction.DELETE, row2.last().action)
        assertEquals(1.5f, row2.last().weight)
        assertEquals("zxcvbnm", row2.subList(1, 8).map { it.label }.joinToString(""))

        // Row 4: &123, EMOJI, space, point, enter
        val row3 = rows[3]
        assertEquals(5, row3.size)
        assertEquals("&123", row3[0].label)
        assertEquals(KeyAction.SYMBOLS, row3[0].action)
        assertEquals(KeyAction.EMOJI, row3[1].action)
        assertEquals("space", row3[2].label)
        assertEquals(KeyAction.SPACE, row3[2].action)
        assertEquals(".", row3[3].label)
        assertEquals(KeyboardLayout.punctuation, row3[3].alternatives)
        assertEquals(KeyAction.ENTER, row3[4].action)
    }

    @Test
    fun testSymbolsPageStructure() {
        val rows = KeyboardLayout.rows(KeyboardPage.SYMBOLS)
        assertEquals(4, rows.size)

        // Numbers row
        assertEquals("1234567890", rows[0].map { it.label }.joinToString(""))

        // Symbols row
        assertEquals("@#$%&*()-+", rows[1].map { it.label }.joinToString(""))

        // More symbols switch and delete
        val row2 = rows[2]
        assertEquals("=\\<", row2.first().label)
        assertEquals(KeyAction.MORE_SYMBOLS, row2.first().action)
        assertEquals(KeyAction.DELETE, row2.last().action)

        // Bottom row has "abc" to switch back to letters
        val row3 = rows[3]
        assertEquals("abc", row3.first().label)
        assertEquals(KeyAction.LETTERS, row3.first().action)
    }

    @Test
    fun testMoreSymbolsPageStructure() {
        val rows = KeyboardLayout.rows(KeyboardPage.MORE_SYMBOLS)
        assertEquals(4, rows.size)

        assertEquals("[]{}<>=_\\|", rows[0].map { it.label }.joinToString(""))
        assertEquals("~`^€£¥¢°¿¡", rows[1].map { it.label }.joinToString(""))

        val row2 = rows[2]
        assertEquals("123", row2.first().label)
        assertEquals(KeyAction.SYMBOLS, row2.first().action)
        assertEquals(KeyAction.DELETE, row2.last().action)

        val row3 = rows[3]
        assertEquals("abc", row3.first().label)
        assertEquals(KeyAction.LETTERS, row3.first().action)
    }

    @Test
    fun testAccentsMapping() {
        val rows = KeyboardLayout.rows(KeyboardPage.LETTERS)
        val allLetters = rows.flatten()

        val aKey = allLetters.first { it.label == "a" }
        assertEquals(listOf("á", "à", "ä"), aKey.alternatives)

        val eKey = allLetters.first { it.label == "e" }
        assertEquals(listOf("é", "è", "ë"), eKey.alternatives)

        val iKey = allLetters.first { it.label == "i" }
        assertEquals(listOf("í", "ì", "ï"), iKey.alternatives)

        val oKey = allLetters.first { it.label == "o" }
        assertEquals(listOf("ó", "ò", "ö"), oKey.alternatives)

        val uKey = allLetters.first { it.label == "u" }
        assertEquals(listOf("ú", "ü", "ù"), uKey.alternatives)

        val nKey = allLetters.first { it.label == "n" }
        assertEquals(listOf("ñ"), nKey.alternatives)

        val zKey = allLetters.first { it.label == "z" }
        assertTrue(zKey.alternatives.isEmpty())
    }

    @Test
    fun testNumericLayoutVariants() {
        val standardNumeric = KeyboardLayout.numeric()
        assertEquals(4, standardNumeric.size)
        assertEquals(listOf("1", "2", "3"), standardNumeric[0].take(3).map { it.label })
        assertEquals(KeyAction.DELETE, standardNumeric[0].last().action)

        val phoneLayout = KeyboardLayout.numeric(phone = true)
        assertEquals("+", phoneLayout[1].last().label)
        assertEquals("*", phoneLayout[2].last().label)
        assertEquals("#", phoneLayout[3][0].label)

        val decimalLayout = KeyboardLayout.numeric(decimal = true)
        assertEquals(".", decimalLayout[2].last().label)

        val signedLayout = KeyboardLayout.numeric(signed = true)
        assertEquals("-", signedLayout[1].last().label)
    }

    @Test
    fun testLettersPageWithNumberRow() {
        val rows = KeyboardLayout.rows(KeyboardPage.LETTERS, numberRow = true)
        assertEquals(5, rows.size)
        // Row 0 is the dedicated number row
        assertEquals("1234567890", rows[0].map { it.label }.joinToString(""))
        // Row 1 is QWERTY
        assertEquals("qwertyuiop", rows[1].map { it.label }.joinToString(""))
        // Row 2 is ASDFGHJKL
        assertEquals("asdfghjkl", rows[2].map { it.label }.joinToString(""))
        // Row 3 is Shift + ZXCVBNM + Delete
        assertEquals(KeyAction.SHIFT, rows[3].first().action)
        assertEquals("zxcvbnm", rows[3].subList(1, 8).map { it.label }.joinToString(""))
        assertEquals(KeyAction.DELETE, rows[3].last().action)
        // Row 4 is bottom bar
        assertEquals(5, rows[4].size)
    }

    @Test
    fun testSpanishLettersPageStructureWithEnyeAndEspacio() {
        val rows = KeyboardLayout.rows(KeyboardPage.LETTERS, numberRow = false, language = "es")
        assertEquals(4, rows.size)

        // Row 0: QWERTYUIOP (10 keys)
        assertEquals("qwertyuiop", rows[0].map { it.label }.joinToString(""))

        // Row 1: ASDFGHJKLÑ (10 keys with dedicated ñ!)
        assertEquals(10, rows[1].size)
        assertEquals("asdfghjklñ", rows[1].map { it.label }.joinToString(""))

        // Row 2: Shift + ZXCVBNM + Delete
        assertEquals(KeyAction.SHIFT, rows[2].first().action)
        assertEquals("zxcvbnm", rows[2].subList(1, 8).map { it.label }.joinToString(""))
        assertEquals(KeyAction.DELETE, rows[2].last().action)

        // Row 3: Bottom bar with "espacio"
        assertEquals(5, rows[3].size)
        assertEquals("espacio", rows[3][2].label)
        assertEquals(KeyAction.SPACE, rows[3][2].action)
    }
}
