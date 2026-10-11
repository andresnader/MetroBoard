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
import org.junit.Assert.*
import org.junit.Test

class EditorPolicyTest {

    @Test
    fun testNormalTextFieldAllowsSuggestionsAndAutocorrect() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        val policy = EditorPolicy.from(info)
        assertFalse(policy.sensitive)
        assertEquals(FieldKind.TEXT, policy.kind)
        assertTrue(policy.editing.suggestions)
        assertTrue(policy.editing.autoCorrect)
    }

    @Test
    fun testPasswordVariationsAreMarkedSensitiveAndDisableSuggestions() {
        // Standard text password
        val textPw = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val textPwPolicy = EditorPolicy.from(textPw)
        assertTrue(textPwPolicy.sensitive)
        assertFalse(textPwPolicy.editing.suggestions)
        assertFalse(textPwPolicy.editing.autoCorrect)

        // Web password
        val webPw = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        }
        val webPwPolicy = EditorPolicy.from(webPw)
        assertTrue(webPwPolicy.sensitive)
        assertFalse(webPwPolicy.editing.suggestions)
        assertFalse(webPwPolicy.editing.autoCorrect)

        // Visible password
        val visPw = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val visPwPolicy = EditorPolicy.from(visPw)
        assertTrue(visPwPolicy.sensitive)
        assertFalse(visPwPolicy.editing.suggestions)
        assertFalse(visPwPolicy.editing.autoCorrect)

        // Number password (PIN)
        val numPw = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val numPwPolicy = EditorPolicy.from(numPw)
        assertTrue(numPwPolicy.sensitive)
        assertEquals(FieldKind.NUMBER, numPwPolicy.kind)
        assertFalse(numPwPolicy.editing.suggestions)
        assertFalse(numPwPolicy.editing.autoCorrect)
    }

    @Test
    fun testNoPersonalizedLearningFlagDisablesSuggestions() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        val policy = EditorPolicy.from(info)
        assertFalse(policy.editing.suggestions)
        assertFalse(policy.editing.autoCorrect)
    }
}
