/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** A fresh install types in the device language when MetroBoard has a dictionary for it. */
class DefaultLanguageTest {
    @Test fun supportedDeviceLanguagesAreKept() {
        for (language in listOf("es", "en", "fr", "de")) {
            assertEquals(language, KeyboardPreferences.languageFor(language))
        }
    }

    @Test fun caseOfTheDeviceLanguageDoesNotMatter() {
        assertEquals("fr", KeyboardPreferences.languageFor("FR"))
    }

    @Test fun unsupportedDeviceLanguageFallsBackToEnglish() {
        assertEquals("en", KeyboardPreferences.languageFor("pt"))
        assertEquals("en", KeyboardPreferences.languageFor(""))
    }
}
