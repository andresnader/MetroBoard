/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

import com.metroboard.ime.prediction.LocalSuggestionEngine
import org.junit.Assert.*
import org.junit.Test

class EditorControllerTest {
    private class MemoryEditor(initial: String = "") : EditorPort {
        var value = initial
        var start = initial.length
        var end = start
        var batches = 0
        var contextReads = 0
        override fun beforeCursor(maxChars: Int): String {
            contextReads++
            return value.substring(0, minOf(start, end)).takeLast(maxChars)
        }
        override fun afterCursor(maxChars: Int): String {
            contextReads++
            return value.substring(maxOf(start, end)).take(maxChars)
        }
        override fun hasSelection() = start != end
        override fun shouldCapitalize() = start == 0 || Regex("[.?!] $").containsMatchIn(value.take(start))
        override fun commit(text: String): Boolean {
            val from = minOf(start, end)
            value = value.replaceRange(from, maxOf(start, end), text)
            start = from + text.length; end = start
            return true
        }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            val from = value.offsetByCodePoints(start, -codePoints)
            value = value.removeRange(from, start)
            start = from; end = start
            return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() { batches++ }
        override fun endBatch() { batches-- }
    }
    private fun controller(
        editor: MemoryEditor,
        policy: EditingPolicy = EditingPolicy(),
        language: String = "es",
    ) = EditorController(editor, LocalSuggestionEngine(activeLanguage = language)).also { it.start(policy) }

    @Test fun pastedTextIsInsertedVerbatimEvenWithAutomaticCapital() {
        val editor = MemoryEditor()
        val controller = controller(editor)
        assertTrue("the empty field starts in automatic capital", controller.shift.isUppercase)
        controller.paste("Segundo texto copiado")
        assertEquals("Segundo texto copiado", editor.value)
    }

    @Test fun pastedTextIsNotRecasedByCapsLockOrOneShotShift() {
        val editor = MemoryEditor("x ")
        val controller = controller(editor)
        controller.tapShift(0)
        controller.paste("mixed Case 123")
        assertEquals("x mixed Case 123", editor.value)
    }

    @Test fun typingAtStartCapitalizesOnlyFirstLetter() {
        val editor = MemoryEditor()
        val controller = controller(editor)
        controller.text("h"); controller.text("o"); controller.text("l"); controller.text("a")
        assertEquals("Hola", editor.value)
        assertEquals(ShiftState.LOWERCASE, controller.shift.state)
    }
    @Test fun doubleSpaceProducesPeriodSpaceAndAutoShift() {
        val editor = MemoryEditor("hola")
        val controller = controller(editor)
        controller.space(1000); controller.space(1100)
        assertEquals("hola. ", editor.value)
        assertEquals(ShiftState.AUTO_SHIFT, controller.shift.state)
        controller.text("m")
        assertEquals("hola. M", editor.value)
        assertEquals(0, editor.batches)
    }
    @Test fun slowDoubleSpacePreservesTwoSpaces() {
        val editor = MemoryEditor("hola")
        val controller = controller(editor)
        controller.space(1000); controller.space(1500)
        assertEquals("hola  ", editor.value)
    }
    @Test fun eachTerminalPunctuationFollowedByASpaceCapitalizesNextLetter() {
        for (punctuation in listOf(".", "!", "?")) {
            val editor = MemoryEditor("hola")
            val controller = controller(editor)
            controller.text(punctuation)
            // Not yet: "hola.com" / "3.14" must stay lowercase; the space is what ends the sentence.
            assertEquals(ShiftState.LOWERCASE, controller.shift.state)
            controller.space(1000)
            assertEquals(ShiftState.AUTO_SHIFT, controller.shift.state)
            controller.text("a")
            assertEquals("hola$punctuation A", editor.value)
        }
    }
    @Test fun capsLockAppliesUntilDisabled() {
        val editor = MemoryEditor()
        val controller = controller(editor)
        controller.tapShift(1000); controller.tapShift(1100)
        controller.text("h"); controller.text("o")
        controller.tapShift(2000); controller.text("l")
        assertEquals("HOl", editor.value)
    }
    @Test fun selectionIsReplacedNotAppended() {
        val editor = MemoryEditor("hola mundo").apply { start = 5; end = 10 }
        val controller = controller(editor)
        controller.text("x")
        assertEquals("hola x", editor.value)
    }
    @Test fun backspaceDeletesWholeSelection() {
        val editor = MemoryEditor("hola mundo").apply { start = 5; end = 10 }
        controller(editor).backspace()
        assertEquals("hola ", editor.value)
    }
    @Test fun backspaceDeletesSupplementaryCodePoint() {
        val editor = MemoryEditor("hola 😀")
        controller(editor).backspace()
        assertEquals("hola ", editor.value)
    }
    @Test fun cursorMovementCancelsDoubleSpace() {
        val editor = MemoryEditor("hola")
        val controller = controller(editor)
        controller.space(1000); controller.cursorMoved(); controller.space(1100)
        assertEquals("hola  ", editor.value)
    }
    @Test fun sensitivePolicyDoesNotReadContextOrTransformText() {
        val editor = MemoryEditor()
        val controller = controller(editor, EditingPolicy(false, false, false))
        controller.text("h"); controller.space(1000); controller.space(1100)
        assertTrue(controller.suggestions().isEmpty())
        assertEquals("h  ", editor.value)
        assertEquals(0, editor.contextReads)
    }
    @Test fun sensitiveFieldStillAllowsManualShift() {
        val editor = MemoryEditor()
        val controller = controller(editor, EditingPolicy(false, false, false))
        controller.tapShift(1000); controller.text("h"); controller.text("a")
        assertEquals("Ha", editor.value)
    }
    @Test fun suggestionReplacesPrefixAndPreservesCase() {
        val editor = MemoryEditor("Ho")
        val controller = controller(editor)
        assertTrue(controller.suggestions().contains("Hola"))
        controller.chooseSuggestion("Hola")
        assertEquals("Hola ", editor.value) // a chosen suggestion adds its trailing space
        assertEquals(0, editor.batches)
    }
    @Test fun doesNotSuggestInMiddleOfWord() {
        val editor = MemoryEditor("hola").apply { start = 2; end = 2 }
        assertTrue(controller(editor).suggestions().isEmpty())
    }
    @Test fun staleSuggestionDoesNotDeleteNewWord() {
        val editor = MemoryEditor("ho")
        val controller = controller(editor)
        controller.text("z"); controller.chooseSuggestion("hola")
        assertEquals("hoz", editor.value)
    }
    @Test fun capCharactersPolicyAppliesToEveryLetter() {
        val editor = MemoryEditor()
        val controller = controller(editor, EditingPolicy(forceUppercase = true))
        controller.text("a"); controller.text("b")
        assertEquals("AB", editor.value)
    }
    @Test fun enterCommitsNewline() {
        val editor = MemoryEditor("hola")
        controller(editor).enter()
        assertEquals("hola\n", editor.value)
    }
    @Test fun autoCorrectOnSpaceReplacesMisspelledWord() {
        val editor = MemoryEditor()
        val controller = controller(editor, language = "en")
        controller.text("t"); controller.text("e"); controller.text("h")
        assertEquals("Teh", editor.value)
        controller.space(1000)
        assertEquals("The ", editor.value)
    }
    @Test fun autoCorrectDoesNotApplyEnglishTyposInSpanish() {
        val editor = MemoryEditor()
        val controller = controller(editor, language = "es")
        controller.text("t"); controller.text("e"); controller.text("h")
        controller.space(1000)
        assertEquals("Teh ", editor.value)
    }
    @Test fun nextWordSuggestionsAppearAfterSpace() {
        val editor = MemoryEditor("gracias ")
        val controller = controller(editor)
        val suggestions = controller.suggestions()
        assertTrue(suggestions.contains("por"))
        controller.chooseSuggestion("por")
        assertEquals("gracias por ", editor.value) // a chosen suggestion adds its trailing space
    }
    @Test fun pressingShiftAtEndOfWordCapitalizesFirstLetter() {
        val editor = MemoryEditor("hola")
        val controller = controller(editor)
        controller.tapShift(1000)
        assertEquals("Hola", editor.value)
    }
    @Test fun pressingShiftCyclesCasingOfCurrentWord() {
        val editor = MemoryEditor("hola")
        val controller = controller(editor)
        controller.tapShift(1000)
        assertEquals("Hola", editor.value)
        controller.tapShift(1100)
        assertEquals("HOLA", editor.value)
        controller.tapShift(1200)
        assertEquals("hola", editor.value)
    }
    @Test fun pressingShiftAfterSpaceActsNormallyOnShiftState() {
        val editor = MemoryEditor("hola ")
        val controller = controller(editor)
        controller.tapShift(1000)
        assertEquals("hola ", editor.value)
        assertEquals(ShiftState.ONE_SHOT_SHIFT, controller.shift.state)
    }
    @Test fun wordFlowCommitsWordWithTrailingSpace() {
        val editor = MemoryEditor()
        val controller = controller(editor)
        controller.commitWordFlow("gracias")
        assertEquals("Gracias ", editor.value)
    }

    @Test fun swipeToDeleteRemovesSingleWord() {
        val editor = MemoryEditor("Hola mundo maravilloso")
        val controller = controller(editor)
        controller.deleteWords(1)
        assertEquals("Hola mundo ", editor.value)
    }

    @Test fun swipeToDeleteRemovesMultipleWords() {
        val editor = MemoryEditor("Hola mundo maravilloso")
        val controller = controller(editor)
        controller.deleteWords(2)
        assertEquals("Hola ", editor.value)
    }

    @Test fun trigramContextPrioritizedAfterTwoWords() {
        val editor = MemoryEditor("muchas gracias ")
        val controller = controller(editor)
        val suggestions = controller.suggestions()
        assertTrue("Expected trigram prediction 'por' for 'muchas gracias ', got: $suggestions", suggestions.contains("por"))
    }
}
