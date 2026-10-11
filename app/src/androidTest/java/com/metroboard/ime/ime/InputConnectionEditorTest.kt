/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ime

import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.metroboard.ime.keyboard.EditorController
import com.metroboard.ime.keyboard.ShiftState
import com.metroboard.ime.prediction.LocalSuggestionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputConnectionEditorTest {
    private fun onEditor(text: String, block: (EditText, InputConnectionEditor, EditorController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val field = EditText(instrumentation.targetContext).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setText(text)
                setSelection(text.length)
            }
            val info = EditorInfo()
            val connection = field.onCreateInputConnection(info)!!
            info.initialSelStart = field.selectionStart
            info.initialSelEnd = field.selectionEnd
            val policy = EditorPolicy.from(info, true)
            val editor = InputConnectionEditor { connection }.apply { start(info, policy) }
            val controller = EditorController(editor, LocalSuggestionEngine()).apply { start(policy.editing) }
            block(field, editor, controller)
        }
    }

    @Test fun realConnectionCommitsDoubleSpaceAndNextCapital() = onEditor("hola") { field, _, controller ->
        controller.space(1000); controller.space(1100); controller.text("m")
        assertEquals("hola. M", field.text.toString())
    }

    @Test fun platformCapsRecognizesSentencePunctuation() {
        for (text in listOf("", "Hola. ", "Hola! ", "Hola? ")) {
            onEditor(text) { _, editor, controller ->
                assertTrue(editor.shouldCapitalize())
                assertEquals(ShiftState.AUTO_SHIFT, controller.shift.state)
            }
        }
    }

    @Test fun deletesSupplementaryCharacterWithoutBrokenSurrogate() = onEditor("hola 😀") { field, _, controller ->
        controller.backspace()
        assertEquals("hola ", field.text.toString())
    }

    @Test fun deletesSelectedText() = onEditor("hola mundo") { field, editor, controller ->
        field.setSelection(5, 10)
        editor.selectionChanged(5, 10)
        controller.backspace()
        assertEquals("hola ", field.text.toString())
    }

    @Test fun typedTextReplacesSelection() = onEditor("hola mundo") { field, editor, controller ->
        field.setSelection(5, 10)
        editor.selectionChanged(5, 10)
        controller.text("x")
        assertEquals("hola x", field.text.toString())
    }

    @Test fun ownSelectionAcknowledgementKeepsDoubleSpaceArmed() = onEditor("hola") { field, editor, controller ->
        controller.space(1000)
        assertFalse(editor.selectionChanged(5, 5))
        controller.space(1100)
        assertEquals("hola. ", field.text.toString())
    }

    @Test fun externalCursorMoveIsDetected() = onEditor("hola") { field, editor, controller ->
        controller.text("a")
        assertFalse(editor.selectionChanged(5, 5))
        field.setSelection(1)
        assertTrue(editor.selectionChanged(1, 1))
    }

    @Test fun enterUsesEditorActionAndHonorsNoEnterAction() {
        var receivedAction = -1
        var committed = ""
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val connection = object : BaseInputConnection(EditText(instrumentation.targetContext), true) {
                override fun performEditorAction(actionCode: Int): Boolean { receivedAction = actionCode; return true }
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    committed = text.toString(); return true
                }
            }
            val info = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEARCH
            }
            val adapter = InputConnectionEditor { connection }
            adapter.start(info, EditorPolicy.from(info, true))
            adapter.enter()
            assertEquals(EditorInfo.IME_ACTION_SEARCH, receivedAction)
            info.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            adapter.start(info, EditorPolicy.from(info, true))
            receivedAction = -1
            adapter.enter()
            assertEquals(-1, receivedAction)
            assertEquals("\n", committed)
        }
    }
}
