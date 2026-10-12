/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Every read of the text around the cursor is a synchronous call into the app (it waits for the app's UI thread):
 * measured at 1 to 31 ms each, and the keyboard made two per letter typed. The editor keeps a local copy of the
 * text around the cursor while everything that happens to it is its own doing, and drops it at the first
 * external change.
 */
class InputConnectionEditorCacheTest {
    /** A text field behind an InputConnection that counts how often the keyboard has to ask it something. */
    private class FakeField(var text: String, var cursor: Int = text.length) {
        val calls = HashMap<String, Int>()
        val connection: InputConnection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            calls[method.name] = (calls[method.name] ?: 0) + 1
            when (method.name) {
                "getTextBeforeCursor" -> text.substring(maxOf(0, cursor - args[0] as Int), cursor)
                "getTextAfterCursor" -> text.substring(cursor, minOf(text.length, cursor + args[0] as Int))
                "commitText" -> {
                    val value = args[0].toString()
                    text = text.substring(0, cursor) + value + text.substring(cursor)
                    cursor += value.length
                    true
                }
                "deleteSurroundingTextInCodePoints", "deleteSurroundingText" -> {
                    val before = minOf(args[0] as Int, cursor)
                    text = text.substring(0, cursor - before) + text.substring(cursor)
                    cursor -= before
                    true
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        } as InputConnection

        fun asked(method: String) = calls[method] ?: 0
    }

    private fun editorFor(field: FakeField, sensitive: Boolean = false): InputConnectionEditor {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (sensitive) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL)
            initialSelStart = field.cursor
            initialSelEnd = field.cursor
        }
        return InputConnectionEditor { field.connection }.also { it.start(info, EditorPolicy.from(info)) }
    }

    @Test fun readingTheTextBeforeTheCursorAgainDoesNotAskTheAppAgain() {
        val field = FakeField("hola que ta")
        val editor = editorFor(field)
        assertEquals("ta", editor.beforeCursor(2))
        assertEquals("que ta", editor.beforeCursor(6))
        assertEquals("hola que ta", editor.beforeCursor(49))
        assertEquals(1, field.asked("getTextBeforeCursor"))
    }

    @Test fun whatTheKeyboardTypesShowsUpWithoutAskingTheApp() {
        val field = FakeField("hola ")
        val editor = editorFor(field)
        editor.beforeCursor(49)
        editor.commit("q")
        editor.commit("u")
        assertEquals("hola qu", editor.beforeCursor(49))
        assertEquals("qu", editor.beforeCursor(2))
        assertEquals(1, field.asked("getTextBeforeCursor"))
        assertEquals("hola qu", field.text) // and the app really got it
    }

    @Test fun theTextAfterTheCursorIsAskedOnceAndSurvivesTyping() {
        val field = FakeField("hola mundo", cursor = 4)
        val editor = editorFor(field)
        assertEquals(" ", editor.afterCursor(1))
        editor.commit("!")
        assertEquals(" ", editor.afterCursor(1))
        assertEquals(" mundo", editor.afterCursor(6))
        assertEquals(1, field.asked("getTextAfterCursor"))
    }

    @Test fun aCursorMovedByTheUserForgetsEverythingAndAsksAgain() {
        val field = FakeField("hola mundo", cursor = 10)
        val editor = editorFor(field)
        assertEquals("mundo", editor.beforeCursor(5))
        field.cursor = 4 // the user tapped inside the text
        assertTrue(editor.selectionChanged(4, 4))
        assertEquals("hola", editor.beforeCursor(5))
        assertEquals(" mundo", editor.afterCursor(6))
        assertEquals(2, field.asked("getTextBeforeCursor"))
    }

    @Test fun theAppEchoOfOurOwnTypingDoesNotForgetAnything() {
        val field = FakeField("hola ")
        val editor = editorFor(field)
        editor.beforeCursor(49)
        editor.commit("q")
        assertEquals(false, editor.selectionChanged(6, 6)) // the app reporting where our own commit left the cursor
        editor.beforeCursor(49)
        assertEquals(1, field.asked("getTextBeforeCursor"))
    }

    @Test fun deletingKeepsTheLocalCopyInStep() {
        val field = FakeField("hola que")
        val editor = editorFor(field)
        editor.beforeCursor(49)
        assertTrue(editor.deleteBeforeCursor(1))
        assertEquals("hola qu", editor.beforeCursor(49))
        assertEquals("hola qu", field.text)
        assertEquals(1, field.asked("getTextBeforeCursor"))
    }

    @Test fun askingForMoreThanWasLoadedGoesBackToTheApp() {
        val long = "x".repeat(600)
        val field = FakeField(long)
        val editor = editorFor(field)
        assertEquals(10, editor.beforeCursor(10).length)
        assertEquals(500, editor.beforeCursor(500).length)
        assertEquals(2, field.asked("getTextBeforeCursor"))
    }

    @Test fun aNewFieldStartsWithNothingRemembered() {
        val field = FakeField("hola")
        val editor = editorFor(field)
        editor.beforeCursor(49)
        field.text = "adios"; field.cursor = 5
        editor.start(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, EditorPolicy.from(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }))
        assertEquals("adios", editor.beforeCursor(49))
    }

    @Test fun passwordFieldsAreNeverReadBeyondWhatWasAskedAndNothingIsKept() {
        val field = FakeField("secret-value")
        val editor = editorFor(field, sensitive = true)
        assertEquals("ue", editor.beforeCursor(2))
        assertEquals("ue", editor.beforeCursor(2))
        assertEquals(2, field.asked("getTextBeforeCursor")) // nothing cached
    }
}
