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
import com.metroboard.ime.keyboard.EditingPolicy

import com.metroboard.ime.settings.KeyboardPreferences

enum class FieldKind { TEXT, EMAIL, URL, NUMBER, PHONE, DATETIME }

data class EditorPolicy(
    val editing: EditingPolicy,
    val kind: FieldKind,
    val sensitive: Boolean,
    val capsModes: Int,
    val decimal: Boolean,
    val signed: Boolean,
    /** Swipe typing is for plain prose: a guessed word with an automatic space is wrong in a password, address or number. */
    val swipeTyping: Boolean,
) {
    companion object {
        fun from(
            info: EditorInfo,
            suggestionsEnabled: Boolean = true,
            autoCorrectEnabled: Boolean = true,
            autoCapEnabled: Boolean = true,
            doubleSpaceEnabled: Boolean = true,
            shiftWordCasingEnabled: Boolean = true,
            learningEnabled: Boolean = true,
        ): EditorPolicy {
            val type = info.inputType
            val typeClass = type and InputType.TYPE_MASK_CLASS
            val variation = type and InputType.TYPE_MASK_VARIATION
            val text = typeClass == InputType.TYPE_CLASS_TEXT
            val sensitive = (text && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )) || (typeClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            val address = text && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_FILTER,
            )
            val noSuggestions = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
                type and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0
            // Only forbids learning from what is typed (many apps set it for privacy). Suggestions come
            // from the fixed dictionary and are not personalised, so they stay on.
            val noLearning = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            val normal = text && !sensitive && !address
            val personName = text && variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            // A person's name takes a capital on every word whether or not the app asked for it.
            val modes = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                (type and (InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) or
                (if (personName) InputType.TYPE_TEXT_FLAG_CAP_WORDS else 0)
            return EditorPolicy(
                editing = EditingPolicy(
                    automaticCapitalization = normal && autoCapEnabled,
                    doubleSpace = normal && doubleSpaceEnabled,
                    suggestions = normal && !noSuggestions && suggestionsEnabled,
                    forceUppercase = normal && type and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0,
                    // Names are not in the dictionary, so correcting them would only damage them.
                    autoCorrect = normal && !personName && !noSuggestions && suggestionsEnabled && autoCorrectEnabled,
                    shiftWordCasing = shiftWordCasingEnabled,
                    // Never in sensitive/address fields, nor when the editor opts out of personalised learning.
                    learnWords = normal && !noSuggestions && !noLearning && suggestionsEnabled && learningEnabled,
                    sensitive = sensitive,
                ),
                kind = when {
                    typeClass == InputType.TYPE_CLASS_NUMBER -> FieldKind.NUMBER
                    typeClass == InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
                    typeClass == InputType.TYPE_CLASS_DATETIME -> FieldKind.DATETIME
                    text && variation in setOf(
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    ) -> FieldKind.EMAIL
                    text && variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                    else -> FieldKind.TEXT
                },
                sensitive = sensitive,
                capsModes = modes,
                decimal = type and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0,
                signed = type and InputType.TYPE_NUMBER_FLAG_SIGNED != 0,
                swipeTyping = normal,
            )
        }

        fun from(info: EditorInfo, preferences: KeyboardPreferences): EditorPolicy = from(
            info = info,
            suggestionsEnabled = preferences.suggestions,
            autoCorrectEnabled = preferences.autoCorrect,
            autoCapEnabled = preferences.autoCapitalization,
            doubleSpaceEnabled = preferences.doubleSpacePeriod,
            shiftWordCasingEnabled = preferences.shiftWordCasing,
            learningEnabled = preferences.learnWords,
        )
    }
}
