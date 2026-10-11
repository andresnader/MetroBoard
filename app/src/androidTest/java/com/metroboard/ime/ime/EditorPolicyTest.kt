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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorPolicyTest {
    private fun policy(type: Int, options: Int = 0) = EditorPolicy.from(EditorInfo().apply {
        inputType = type; imeOptions = options
    }, true)

    @Test fun allPasswordVariantsDisableSuggestionsAndTransforms() {
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )) {
            val policy = policy(type)
            assertTrue(policy.sensitive)
            assertFalse(policy.editing.suggestions)
            assertFalse(policy.editing.automaticCapitalization)
            assertFalse(policy.editing.doubleSpace)
        }
    }

    @Test fun addressesDisableAutomaticTransformations() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)) {
            val policy = policy(InputType.TYPE_CLASS_TEXT or variation)
            assertFalse(policy.editing.suggestions)
            assertFalse(policy.editing.automaticCapitalization)
            assertFalse(policy.editing.doubleSpace)
        }
    }

    @Test fun noPersonalizedLearningIsConservativelySuggestionFree() {
        assertFalse(policy(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).editing.suggestions)
    }
    @Test fun noSuggestionsAndAutocompleteAreRespected() {
        for (flag in listOf(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)) {
            assertFalse(policy(InputType.TYPE_CLASS_TEXT or flag).editing.suggestions)
        }
    }
    @Test fun numericPolicyRespectsDecimalAndSignedFlags() {
        val policy = policy(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        assertEquals(FieldKind.NUMBER, policy.kind)
        assertTrue(policy.decimal)
        assertTrue(policy.signed)
    }
    @Test fun phoneAndDateUseSpecializedLayouts() {
        assertEquals(FieldKind.PHONE, policy(InputType.TYPE_CLASS_PHONE).kind)
        assertEquals(FieldKind.DATETIME, policy(InputType.TYPE_CLASS_DATETIME).kind)
    }
    @Test fun capCharactersFlagIsPreserved() {
        assertTrue(policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).editing.forceUppercase)
    }

    @Test fun editorPolicyRespectsDisabledAutoCorrectAndShiftCasing() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        val disabledPolicy = EditorPolicy.from(
            info = info,
            suggestionsEnabled = true,
            autoCorrectEnabled = false,
            autoCapEnabled = false,
            doubleSpaceEnabled = false,
            shiftWordCasingEnabled = false,
        )
        assertFalse(disabledPolicy.editing.autoCorrect)
        assertFalse(disabledPolicy.editing.automaticCapitalization)
        assertFalse(disabledPolicy.editing.doubleSpace)
        assertFalse(disabledPolicy.editing.shiftWordCasing)
        assertTrue(disabledPolicy.editing.suggestions)
    }
}
