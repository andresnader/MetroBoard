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
    fun testNoPersonalizedLearningFlagOnlyStopsLearning() {
        // Apps such as AI chats set this flag for privacy. It forbids learning from what is typed; the
        // suggestions come from the fixed dictionary and are not personalised, so they must stay on.
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        val policy = EditorPolicy.from(info)
        assertTrue(policy.editing.suggestions)
        assertTrue(policy.editing.autoCorrect)
        assertFalse(policy.editing.learnWords)
    }

    // ---- learning of new words ---------------------------------------------

    private fun textInfo(variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL, imeOptions: Int = 0) = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or variation
        this.imeOptions = imeOptions
    }

    @Test
    fun learningIsOnInNormalTextFields() {
        val policy = EditorPolicy.from(textInfo())
        assertTrue(policy.editing.learnWords)
    }

    @Test
    fun theUserCanTurnLearningOffAndSuggestionsStayOn() {
        val policy = EditorPolicy.from(textInfo(), learningEnabled = false)
        assertFalse(policy.editing.learnWords)
        assertTrue(policy.editing.suggestions)
    }

    @Test
    fun noPersonalizedLearningFlagDisablesLearning() {
        val policy = EditorPolicy.from(textInfo(imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(policy.editing.learnWords)
    }

    @Test
    fun passwordsEmailsAndUrlsNeverLearnAndPasswordsAreNeverRead() {
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )) {
            val policy = EditorPolicy.from(textInfo(variation))
            assertFalse("variation $variation", policy.editing.learnWords)
        }
        assertTrue(EditorPolicy.from(textInfo(InputType.TYPE_TEXT_VARIATION_PASSWORD)).editing.sensitive)
    }

    @Test
    fun learningNeverTurnsOnWhenSuggestionsAreOff() {
        val policy = EditorPolicy.from(textInfo(), suggestionsEnabled = false)
        assertFalse(policy.editing.learnWords)
    }

    // ---- what kind of field it is -------------------------------------------

    private fun textWith(variation: Int, flags: Int = 0) = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or variation or flags
    }

    @Test
    fun emailAndUrlFieldsAreRecognisedSoTheyGetTheirOwnKeys() {
        assertEquals(FieldKind.EMAIL, EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)).kind)
        assertEquals(FieldKind.EMAIL, EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)).kind)
        assertEquals(FieldKind.URL, EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_URI)).kind)
        assertEquals(FieldKind.TEXT, EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_NORMAL)).kind)
        assertEquals(FieldKind.TEXT, EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_PASSWORD)).kind)
    }

    @Test
    fun personNamesCapitaliseEveryWordAndAreNeverAutocorrected() {
        val policy = EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_PERSON_NAME))
        assertTrue(policy.capsModes and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0)
        assertFalse(policy.editing.autoCorrect)
        assertTrue(policy.editing.suggestions)
        assertTrue(policy.editing.learnWords)
    }

    @Test
    fun otherTextFieldsStillAutocorrectAndCapitaliseSentences() {
        val policy = EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_NORMAL))
        assertTrue(policy.editing.autoCorrect)
        assertEquals(0, policy.capsModes and InputType.TYPE_TEXT_FLAG_CAP_WORDS)
    }

    @Test
    fun swipeTypingIsOnlyForPlainTextFields() {
        assertTrue(EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_NORMAL)).swipeTyping)
        assertTrue(EditorPolicy.from(textWith(InputType.TYPE_TEXT_VARIATION_PERSON_NAME)).swipeTyping)
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )) {
            assertFalse("variation $variation", EditorPolicy.from(textWith(variation)).swipeTyping)
        }
        assertFalse(EditorPolicy.from(EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }).swipeTyping)
        assertFalse(EditorPolicy.from(EditorInfo().apply { inputType = InputType.TYPE_CLASS_PHONE }).swipeTyping)
    }
}
