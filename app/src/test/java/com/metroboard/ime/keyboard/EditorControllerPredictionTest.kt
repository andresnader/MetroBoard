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
        override fun beforeCursor(maxChars: Int) = value.substring(0, minOf(start, end)).takeLast(maxChars)
        override fun afterCursor(maxChars: Int) = value.substring(maxOf(start, end)).take(maxChars)
        var end = initial.length
        override fun hasSelection() = start != end
        override fun selectedText(): String? = if (hasSelection()) value.substring(minOf(start, end), maxOf(start, end)) else null
        override fun shouldCapitalize() = start == 0 || value.take(start).trimEnd(' ').let { it.isEmpty() || it.last() in ".!?" }
        override fun commit(text: String): Boolean {
            val from = minOf(start, end)
            value = value.substring(0, from) + text + value.substring(maxOf(start, end))
            start = from + text.length; end = start; return true
        }
        override fun deleteBeforeCursor(codePoints: Int): Boolean {
            val from = value.offsetByCodePoints(start, -codePoints)
            value = value.removeRange(from, start); start = from; end = from; return true
        }
        override fun enter() = commit("\n")
        override fun beginBatch() {}
        override fun endBatch() {}
    }

    private class StubEngine(
        val corrections: Map<String, String> = emptyMap(),
        val suggestions: Map<String, List<String>> = emptyMap(),
        val next: Map<String, List<String>> = emptyMap(),
        override val elidesApostrophe: Boolean = false,
        val replacements: Map<String, List<String>> = emptyMap(),
    ) : SuggestionEngine {
        override fun suggestReplacements(word: String) = replacements[word.lowercase()] ?: emptyList()
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

    @Test fun englishContractionsAreOneToken() {
        val engine = StubEngine(suggestions = mapOf("don't" to listOf("don't")))
        val editor = MemoryEditor()
        val c = controller(editor, engine)
        c.type("don't")
        assertEquals(listOf("don't"), c.suggestions())
        c.space(1000)
        assertEquals(listOf("don't"), engine.autoCorrectCalls)
    }

    @Test fun frenchElisionEndsThePreviousWord() {
        val engine = StubEngine(suggestions = mapOf("est" to listOf("est")), elidesApostrophe = true)
        val editor = MemoryEditor()
        val c = controller(editor, engine)
        c.type("c'est")
        assertEquals("prediction applies to the word after the elided c'", listOf("est"), c.suggestions())
        c.space(1000)
        assertEquals("autocorrect sees \"est\", not \"c'est\" or \"t\"", listOf("est"), engine.autoCorrectCalls)
        c.type("l'ami")
        c.chooseSuggestion("est") // not offered for "ami": must be ignored, text untouched
        assertEquals("c'est l'ami", editor.value)
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
        editor.start = 3; editor.end = 3
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

    // ---- replacing a word the editor has selected ------------------------

    @Test fun aSelectedWordGetsReplacementCandidates() {
        val editor = MemoryEditor("dije hola ayer"); editor.start = 5; editor.end = 9
        val c = controller(editor, StubEngine(replacements = mapOf("hola" to listOf("hala", "hola"))))
        assertEquals(listOf("hala", "hola"), c.suggestions())
    }

    @Test fun choosingAReplacementSwapsOnlyTheSelectionAndKeepsCase() {
        val editor = MemoryEditor("dije Hola ayer"); editor.start = 5; editor.end = 9
        val c = controller(editor, StubEngine(replacements = mapOf("hola" to listOf("hala"))))
        c.chooseSuggestion("Hala")
        assertEquals("dije Hala ayer", editor.value)
        assertEquals("no extra space is added inside the sentence", false, editor.hasSelection())
    }

    @Test fun aSelectionThatIsNotASingleWordGetsNoReplacements() {
        val editor = MemoryEditor("dos palabras aqui"); editor.start = 0; editor.end = 12
        val c = controller(editor, StubEngine(replacements = mapOf("dos palabras" to listOf("x"))))
        assertTrue(c.suggestions().isEmpty())
    }

    @Test fun nothingIsReadFromASelectionWhenSuggestionsAreOff() {
        val editor = MemoryEditor("dije hola ayer"); editor.start = 5; editor.end = 9
        val c = EditorController(editor, StubEngine(replacements = mapOf("hola" to listOf("hala"))))
            .also { it.start(EditingPolicy(suggestions = false)) }
        assertTrue(c.suggestions().isEmpty())
    }

    // ---- URLs and e-mail addresses ---------------------------------------

    @Test fun urlsAndEmailAddressesAreNeitherCorrectedNorLearned() {
        for (token in listOf("www.googel.com", "ana@gmial.com", "http://exmple.org/abc", "nombre_usuarioo", "a.b")) {
            val engine = StubEngine(corrections = mapOf("com" to "con", "googel" to "google", "gmial" to "gmail", "org" to "ore",
                "abc" to "abd", "usuarioo" to "usuario", "b" to "bb", "nombre_usuarioo" to "x"))
            val editor = MemoryEditor()
            val c = controller(editor, engine, learn = true)
            c.type(token); c.space(1000)
            assertEquals("'$token' must be left exactly as typed", "$token ", editor.value)
            assertTrue("'$token' must not be learned: ${engine.learned}", engine.learned.isEmpty())
            assertTrue("'$token' must not be sent to autocorrect: ${engine.autoCorrectCalls}", engine.autoCorrectCalls.isEmpty())
        }
    }

    @Test fun anOrdinaryWordAfterAUrlIsStillHandledNormally() {
        val engine = StubEngine(corrections = mapOf("tecldao" to "teclado"))
        val editor = MemoryEditor()
        val c = controller(editor, engine, learn = true)
        c.type("www.google.com "); c.type("tecldao"); c.space(2000)
        assertEquals("www.google.com teclado ", editor.value)
    }

    // The dictionary lookup for the suggestion bar runs on another thread, so what has to be known about the
    // editor is captured when the question is asked, not when the answer arrives.
    @Test fun theLookupFormatsWithTheCasingOfTheMomentTheQuestionWasAsked() {
        val editor = MemoryEditor("")
        val controller = controller(editor, StubEngine(suggestions = mapOf("ho" to listOf("hola", "hoy"))))
        controller.tapShift(0L)
        controller.tapShift(100L) // double tap: Caps Lock
        assertEquals(ShiftState.CAPS_LOCK, controller.shift.state)
        editor.commit("ho")
        val question = controller.suggestionQuery()!!
        controller.tapShift(10_000L) // Caps Lock off before the answer arrives
        assertEquals(listOf("HOLA", "HOY"), controller.lookUp(question))
    }

    @Test fun theLookupNeedsNoAccessToTheEditor() {
        val editor = MemoryEditor("")
        val controller = controller(editor, StubEngine(suggestions = mapOf("ho" to listOf("hola"))))
        editor.commit("ho")
        val question = controller.suggestionQuery()!!
        editor.commit("rrible xyz") // the field changes while the lookup is running
        assertEquals(listOf("hola"), controller.lookUp(question)) // still the answer to the question that was asked
    }

    @Test fun nothingToSuggestIsNoQueryAtAll() {
        val controller = controller(MemoryEditor(""), StubEngine())
        assertTrue(controller.suggestionQuery() == null)
        assertEquals(emptyList<String>(), controller.suggestions())
    }

    // Swipe typing uses the word before the swipe as context.
    @Test fun theWordBeforeASwipeIsTheOneBeforeTheSpace() {
        val controller = controller(MemoryEditor("hola el "), StubEngine())
        assertEquals("el", controller.previousWordForSwipe())
    }

    @Test fun noContextWhenTheCursorIsInsideAWordOrAtTheStart() {
        assertEquals(null, controller(MemoryEditor("hola el"), StubEngine()).previousWordForSwipe())
        assertEquals(null, controller(MemoryEditor(""), StubEngine()).previousWordForSwipe())
    }

    @Test fun noContextWhereSuggestionsAreOff() {
        val editor = MemoryEditor("hola el ")
        val controller = EditorController(editor, StubEngine()).also { it.start(EditingPolicy(suggestions = false)) }
        assertEquals(null, controller.previousWordForSwipe())
    }
}
