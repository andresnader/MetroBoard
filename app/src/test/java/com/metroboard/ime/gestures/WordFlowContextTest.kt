/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.gestures

import com.metroboard.ime.dictionary.LanguageDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The word typed just before the swipe decides between words the finger drew equally well. */
class WordFlowContextTest {
    private val centers = KeyboardGeometry.centers("es")

    /** "tema" and "yema" differ only in T/Y, which are neighbours: a swipe starting between them fits both equally. */
    private fun dictionary(next: Map<String, List<String>>) =
        LanguageDictionary("es", entries = listOf("tema" to 100, "yema" to 100), bigrams = next)

    private fun decoder(next: Map<String, List<String>>) =
        WordFlowShapeDecoder(dictionaryFor = { dictionary(next) })

    private fun through(vararg points: Pair<Float, Float>) =
        points.mapIndexed { i, (x, y) -> GesturePoint(x, y, i * 60L) }

    private fun key(c: Char) = centers.getValue(c)

    /** Starts halfway between T and Y, then E, M, A. */
    private val ambiguous: List<GesturePoint>
        get() {
            val t = key('t'); val y = key('y'); val e = key('e'); val m = key('m'); val a = key('a')
            return through(
                (t.x + y.x) / 2 to (t.y + y.y) / 2, e.x to e.y, (e.x + m.x) / 2 to (e.y + m.y) / 2, m.x to m.y,
                (m.x + a.x) / 2 to (m.y + a.y) / 2, a.x to a.y,
            )
        }

    @Test fun withoutContextBothWordsAreOffered() {
        val words = decoder(emptyMap()).decode(ambiguous, "es", null)
        assertTrue(words.toString(), words.containsAll(listOf("tema", "yema")))
    }

    @Test fun theWordBeforeDecidesBetweenEquallyGoodShapes() {
        val forTema = decoder(mapOf("el" to listOf("tema"))).decode(ambiguous, "es", null, previousWord = "el")
        val forYema = decoder(mapOf("el" to listOf("yema"))).decode(ambiguous, "es", null, previousWord = "el")
        assertEquals("tema", forTema.first())
        assertEquals("yema", forYema.first())
    }

    @Test fun aWordWithNoNextWordsChangesNothing() {
        val plain = decoder(mapOf("el" to listOf("yema"))).decode(ambiguous, "es", null)
        val other = decoder(mapOf("el" to listOf("yema"))).decode(ambiguous, "es", null, previousWord = "unknownword")
        assertEquals(plain, other)
    }

    @Test fun contextCannotOverrideAClearlyBetterShape() {
        // A swipe that really goes through T: even with the context favouring "yema", "tema" stays first.
        val t = key('t'); val e = key('e'); val m = key('m'); val a = key('a')
        val clear = through(t.x to t.y, e.x to e.y, (e.x + m.x) / 2 to (e.y + m.y) / 2, m.x to m.y, a.x to a.y)
        val words = decoder(mapOf("el" to listOf("yema"))).decode(clear, "es", null, previousWord = "el")
        assertEquals("tema", words.first())
    }

    @Test fun accentsAndCaseOfThePreviousWordDoNotMatter() {
        val a = decoder(mapOf("el" to listOf("tema"))).decode(ambiguous, "es", null, previousWord = "El")
        assertEquals("tema", a.first())
    }
}
