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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Autocorrect safety, reversibility, tokenisation, suggestion spacing and learning. */
class EditorControllerPredictionTest {
    private class MemoryEditor(initial: String = "") : EditorPort {
        var value = initial
        var start = initial.length
        override fun beforeCursor(maxChars: Int) = value.substring(0, start).takeLast(maxChars)
        override fun afterCursor(maxChars: Int) = value.substring(start).take(maxChars)
        override fun hasSelection() = false
        override fun shouldCapitalize() = start == 0 || value.take(start).trimEnd(' ').let { it.isEmpty() || it.last() in ".!?" }
        override fun commit(text: String): Boolean { value = value.substring(0, start) + text + value.substring(start); start += text.length; return true }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            val from = value.offsetByCodePoints(start, -codePoints)
            value = value.removeRange(from, start); start = from; return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() {}
        override fun endBatch() {}
    }

    private class StubEngine(
        val corrections: Map<String, String> = emptyMap(),
        val suggestions: Map<String, List<String>> = emptyMap(),
        val next: Map<String, List<String>> = emptyMap(),
    ) : SuggestionEngine {
        val autoCorrectCalls = mutableListOf<String>()
        val learned = mutableListOf<String>()
        val kept = mutableListOf<String>()
        override fun suggest(prefix: String) = suggestions[prefix.lowercase()] ?: emptyList()
        override fun suggestNext(previousWord: String) = next[previousWord.lowercase()] ?: emptyList()
        override fun autoCorrect(word: String): String? { autoCorrectCalls += word; return corrections[word.lowercase()] }
        override fun learn(word: String, explicit: Boolean) { if (explicit) kept += word else learned += word }
    }

    private fun controller(editor: MemoryEditor, engine: SuggestionEngine, learn: Boolean = false) =
        EditorController(editor, engine).also { it.start(EditingPolicy(learnWords = learn, automaticCapitalization = false)) }

    private fun EditorController.type(s: String) = s.forEach { text(it.toString()) }

    // ---- reversible autocorrect ------------------------------------------

    @Test fun backspaceRightAfterAnAutocorrectRestoresTheOriginalWord() {
        val editor = MemoryEditor()
        val c = controller(editor, StubEngine(corrections = mapOf("tecldao" to "teclado")))
        c.type("tecldao"); c.space(1000)
        assertEquals("teclado ", editor.value)
        c.backspace()
        assertEquals("tecldao", editor.value)
    }

    @Test fun anUndoneCorrectionIsNotImposedAgain() {
        val editor = MemoryEditor()
        val c = controller(editor, StubEngine(corrections = mapOf("tecldao" to "teclado")))
        c.type("tecldao"); c.space(1000); c.backspace()
        c.space(2000)
        assertEquals("tecldao ", editor.value)
    }

    @Test fun backspaceAfterOtherInputOnlyDeletesOneCharacter() {
        val editor = MemoryEditor()
        val c = controller(editor, StubEngine(corrections = mapOf("tecldao" to "teclado")))
        c.type("tecldao"); c.space(1000); c.type("x")
        c.backspace()
        assertEquals("teclado ", editor.value)
        c.backspace()
        assertEquals("teclado", editor.value)
    }

    // ---- what must never be auto-corrected -------------------------------

    @Test fun properNounsAndAcronymsAreNotAutoCorrected() {
        val engine = StubEngine(corrections = mapOf("quitoo" to "quito", "nasaa" to "nasa"))
        val editor = MemoryEditor("Voy a ")
        val c = controller(editor, engine)
        c.type("Quitoo"); c.space(1000)
        assertEquals("Voy a Quitoo ", editor.value)
        c.type("NASAA"); c.space(2000)
        assertEquals("Voy a Quitoo NASAA ", editor.value)
    }

    @Test fun aCapitalisedWordAtTheStartOfASentenceIsStillCorrected() {
        val editor = MemoryEditor("Hola. ")
        val c = controller(editor, StubEngine(corrections = mapOf("tecldao" to "teclado")))
        c.type("Tecldao"); c.space(1000)
        assertEquals("Hola. Teclado ", editor.value)
    }

    // ---- tokenisation ---------------------------------------------------

    @Test fun contractionsAndElisionsAreOneToken() {
        val engine = StubEngine(suggestions = mapOf("don't" to listOf("don't"), "c'est" to listOf("c'est")))
        val editor = MemoryEditor()
        val c = controller(editor, engine)
        c.type("don't")
        assertEquals(listOf("don't"), c.suggestions())
        c.space(1000)
        assertEquals(listOf("don't"), engine.autoCorrectCalls)
        c.type("c'est")
        assertEquals(listOf("c'est"), c.suggestions())
    }

    // ---- suggestion spacing ---------------------------------------------

    @Test fun choosingASuggestionAddsOneTrailingSpace() {
        val editor = MemoryEditor()
        val c = controller(editor, StubEngine(suggestions = mapOf("hol" to listOf("hola"))))
        c.type("hol")
        c.chooseSuggestion("hola")
        assertEquals("hola ", editor.value)
    }

    @Test fun choosingASuggestionNeverDuplicatesAnExistingSpace() {
        val editor = MemoryEditor("hol adiós")
        editor.start = 3
        val c = controller(editor, StubEngine(suggestions = mapOf("hol" to listOf("hola"))))
        c.chooseSuggestion("hola")
        assertEquals("hola adiós", editor.value)
    }

    @Test fun choosingANextWordSuggestionAddsOneTrailingSpace() {
        val editor = MemoryEditor("gracias ")
        val c = controller(editor, StubEngine(next = mapOf("gracias" to listOf("por"))))
        c.chooseSuggestion("por")
        assertEquals("gracias por ", editor.value)
    }

    // ---- learning -------------------------------------------------------

    @Test fun aNewTypedWordIsLearnedWhenTheUserMovesOn() {
        val engine = StubEngine()
        val c = controller(MemoryEditor(), engine, learn = true)
        c.type("Ameizin"); c.space(1000)
        assertEquals(listOf("Ameizin"), engine.learned)
    }

    @Test fun nothingIsLearnedWhenLearningIsDisabledForTheField() {
        val engine = StubEngine()
        val c = controller(MemoryEditor(), engine, learn = false)
        c.type("Ameizin"); c.space(1000)
        assertTrue(engine.learned.isEmpty())
    }

    @Test fun anAutoCorrectedWordIsNotLearnedButAnUndoneOneIs() {
        val engine = StubEngine(corrections = mapOf("tecldao" to "teclado"))
        val editor = MemoryEditor()
        val c = controller(editor, engine, learn = true)
        c.type("tecldao"); c.space(1000)
        assertTrue("the typo must not be learned", engine.learned.isEmpty())
        c.backspace() // the user explicitly insists on what they typed
        assertEquals(listOf("tecldao"), engine.kept)
    }
}
