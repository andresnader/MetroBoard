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

/** Experience-suite group F: capitalisation and Shift, driven through the real controller. */
class ShiftBehaviourTest {
    /** Editor with platform-like sentence rules: capitalise after ". ", "? ", "! " and at the start. */
    private class MemoryEditor(initial: String = "") : EditorPort {
        var value = initial
        var start = initial.length
        var end = start
        override fun beforeCursor(maxChars: Int) = value.substring(0, minOf(start, end)).takeLast(maxChars)
        override fun afterCursor(maxChars: Int) = value.substring(maxOf(start, end)).take(maxChars)
        override fun hasSelection() = start != end
        override fun selectedText(): String? = if (hasSelection()) value.substring(minOf(start, end), maxOf(start, end)) else null
        override fun shouldCapitalize(): Boolean {
            val before = value.substring(0, minOf(start, end))
            return before.isEmpty() || before.endsWith("\n") || Regex("[.?!] +$").containsMatchIn(before)
        }
        override fun commit(text: String): Boolean {
            val from = minOf(start, end)
            value = value.replaceRange(from, maxOf(start, end), text)
            start = from + text.length; end = start
            return true
        }
        override fun replaceSelectionKeepingIt(text: String): Boolean {
            val from = minOf(start, end)
            value = value.replaceRange(from, maxOf(start, end), text)
            start = from; end = from + text.length
            return true
        }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            val from = value.offsetByCodePoints(start, -codePoints)
            value = value.removeRange(from, start); start = from; end = start; return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() {}
        override fun endBatch() {}
    }

    private val noEngine = SuggestionEngine { emptyList() }
    private fun controller(editor: MemoryEditor, policy: EditingPolicy = EditingPolicy()) =
        EditorController(editor, noEngine).also { it.start(policy) }

    private fun EditorController.type(s: String) = s.forEach { if (it == ' ') space(1000) else text(it.toString()) }

    @Test fun f01_startOfFieldIsAutoShift() {
        assertEquals(ShiftState.AUTO_SHIFT, controller(MemoryEditor()).shift.state)
    }

    @Test fun f02_f03_f04_afterSentenceEndAndSpaceTheNextLetterIsCapital() {
        for (mark in listOf(".", "?", "!")) {
            val editor = MemoryEditor()
            val c = controller(editor)
            c.type("hola$mark ")
            assertEquals("after \"$mark \"", ShiftState.AUTO_SHIFT, c.shift.state)
            c.type("que")
            assertEquals("Hola$mark Que", editor.value)
            assertEquals(ShiftState.LOWERCASE, c.shift.state)
        }
    }

    @Test fun aPeriodInsideADomainDoesNotCapitaliseTheNextLetter() {
        val editor = MemoryEditor()
        val c = controller(editor)
        c.type("mira www.google.com y")
        assertEquals("Mira www.google.com y", editor.value)
    }

    @Test fun decimalNumbersAndAbbreviationsWithoutSpaceStayLowercase() {
        val editor = MemoryEditor("pi vale 3")
        val c = controller(editor)
        c.type(".14")
        assertEquals("pi vale 3.14", editor.value)
        assertEquals(ShiftState.LOWERCASE, c.shift.state)
    }

    @Test fun f05_oneShotShiftAppliesToTheNextLetterOnly() {
        val editor = MemoryEditor("hola ")
        val c = controller(editor)
        c.tapShift(1000)
        assertEquals(ShiftState.ONE_SHOT_SHIFT, c.shift.state)
        c.type("mundo")
        assertEquals("hola Mundo", editor.value)
    }

    @Test fun f06_doubleTapIsCapsLockAndShiftLeavesIt() {
        val editor = MemoryEditor("hola ")
        val c = controller(editor)
        c.tapShift(1000); c.tapShift(1200)
        assertEquals(ShiftState.CAPS_LOCK, c.shift.state)
        c.type("abc")
        assertEquals("hola ABC", editor.value)
        c.tapShift(5000)
        assertEquals(ShiftState.LOWERCASE, c.shift.state)
        c.type("d")
        assertEquals("hola ABCd", editor.value)
    }

    @Test fun f07_shiftOnTheWordJustTypedCyclesPalabraPalabraPALABRA() {
        val editor = MemoryEditor("")
        val c = controller(editor, EditingPolicy(automaticCapitalization = false))
        c.type("palabra")
        c.tapShift(1000); assertEquals("Palabra", editor.value)
        c.tapShift(2000); assertEquals("PALABRA", editor.value)
        c.tapShift(3000); assertEquals("palabra", editor.value)
    }

    @Test fun f08_shiftOnSelectedTextCyclesItsCasingAndKeepsTheSelection() {
        val editor = MemoryEditor("dije hola ayer")
        editor.start = 5; editor.end = 9
        val c = controller(editor, EditingPolicy(automaticCapitalization = false))
        c.tapShift(1000); assertEquals("dije Hola ayer", editor.value)
        assertEquals("Hola", editor.selectedText())
        c.tapShift(2000); assertEquals("dije HOLA ayer", editor.value)
        c.tapShift(3000); assertEquals("dije hola ayer", editor.value)
        assertEquals("hola", editor.selectedText())
    }

    @Test fun shiftOnASelectionWithoutLettersDoesNotTouchIt() {
        val editor = MemoryEditor("a 123 b")
        editor.start = 2; editor.end = 5
        val c = controller(editor, EditingPolicy(automaticCapitalization = false))
        c.tapShift(1000)
        assertEquals("a 123 b", editor.value)
    }

    @Test fun displayedStateMatchesTheInsertedCharacter() {
        for (state in listOf("auto", "oneshot", "caps", "lower")) {
            val editor = MemoryEditor(if (state == "auto") "" else "x ")
            val c = controller(editor, EditingPolicy(automaticCapitalization = state == "auto"))
            when (state) { "oneshot" -> c.tapShift(1000); "caps" -> { c.tapShift(1000); c.tapShift(1100) } }
            val shown = c.shift.isUppercase
            val before = editor.value
            c.text("a")
            val inserted = editor.value.removePrefix(before)
            assertEquals("state=$state", if (shown) "A" else "a", inserted)
        }
    }
}
