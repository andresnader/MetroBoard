/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metroboard.ime.prediction.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The same grapheme cases as the JVM test, but on Android's own (ICU-backed) BreakIterator. */
@RunWith(AndroidJUnit4::class)
class BackspaceGraphemeDeviceTest {
    private class Editor(var value: String) : EditorPort {
        var cursor = value.length
        override fun beforeCursor(maxChars: Int) = value.substring(0, cursor).takeLast(maxChars)
        override fun afterCursor(maxChars: Int) = value.substring(cursor).take(maxChars)
        override fun hasSelection() = false
        override fun shouldCapitalize() = false
        override fun commit(text: String): Boolean { value = value.substring(0, cursor) + text + value.substring(cursor); cursor += text.length; return true }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            if (cursor == 0) return false
            val from = value.offsetByCodePoints(cursor, -codePoints)
            value = value.removeRange(from, cursor); cursor = from; return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() {}
        override fun endBatch() {}
    }

    private fun backspace(text: String): String {
        val editor = Editor(text)
        EditorController(editor, SuggestionEngine { emptyList() })
            .also { it.start(EditingPolicy(automaticCapitalization = false)) }.backspace()
        return editor.value
    }

    @Test fun skinToneEmoji() = assertEquals("hola ", backspace("hola 👍🏽"))
    @Test fun zwjFamily() = assertEquals("hola ", backspace("hola 👨‍👩‍👧"))
    @Test fun flag() = assertEquals("hola ", backspace("hola 🇪🇨"))
    @Test fun variationSelector() = assertEquals("hola ", backspace("hola ❤️"))
    @Test fun combiningMark() = assertEquals("caf", backspace("café"))
    @Test fun plainLetter() = assertEquals("hol", backspace("hola"))
}
