/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

import com.metroboard.ime.prediction.SuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Experience-suite group G: Backspace over Unicode must remove one user-perceived character. */
class BackspaceGraphemeTest {
    private class MemoryEditor(initial: String) : EditorPort {
        var value = initial
        var start = initial.length
        var end = start
        override fun beforeCursor(maxChars: Int) = value.substring(0, minOf(start, end)).takeLast(maxChars)
        override fun afterCursor(maxChars: Int) = value.substring(maxOf(start, end)).take(maxChars)
        override fun hasSelection() = start != end
        override fun shouldCapitalize() = false
        override fun commit(text: String): Boolean {
            val from = minOf(start, end)
            value = value.replaceRange(from, maxOf(start, end), text); start = from + text.length; end = start; return true
        }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            if (start == 0) return false
            val from = value.offsetByCodePoints(start, -codePoints)
            value = value.removeRange(from, start); start = from; end = from; return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() {}
        override fun endBatch() {}
    }

    private fun after(initial: String, taps: Int = 1): String {
        val editor = MemoryEditor(initial)
        val c = EditorController(editor, SuggestionEngine { emptyList() }).also { it.start(EditingPolicy(automaticCapitalization = false)) }
        repeat(taps) { c.backspace() }
        return editor.value
    }

    @Test fun g07_aSkinToneEmojiIsOneBackspace() = assertEquals("hola ", after("hola 👍🏽"))
    @Test fun aZwjFamilyIsOneBackspace() = assertEquals("hola ", after("hola 👨‍👩‍👧"))
    @Test fun aFlagIsOneBackspace() = assertEquals("hola ", after("hola 🇪🇨"))
    @Test fun anEmojiWithAVariationSelectorIsOneBackspace() = assertEquals("hola ", after("hola ❤️"))
    @Test fun aLetterWithACombiningMarkIsOneBackspace() = assertEquals("caf", after("cafe\u0301")) // decomposed "café"
    @Test fun g01_aPlainLetterIsStillOneCodePoint() = assertEquals("hol", after("hola"))
    @Test fun aPrecomposedAccentIsOneBackspace() = assertEquals("canci", after("canció"))
    @Test fun twoBackspacesRemoveTwoEmojiNotFourCodePoints() = assertEquals("ok ", after("ok 👍🏽😀", taps = 2))
    @Test fun anEmojiNextToTextDoesNotEatTheLetterBefore() = assertEquals("a", after("a😀"))
    @Test fun crLfIsOneBackspace() = assertEquals("a", after("a\r\n"))
    @Test fun anEmptyFieldDoesNothing() = assertEquals("", after(""))
}
