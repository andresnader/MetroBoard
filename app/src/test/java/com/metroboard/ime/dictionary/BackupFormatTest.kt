/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupFormatTest {
    private fun expectRejected(text: String, why: String) {
        try { BackupFormat.parse(text); fail("accepted: $why") } catch (_: BackupFormat.InvalidBackup) { /* expected */ }
    }

    @Test fun roundTripKeepsWordsPerLanguageAndSettings() {
        val json = BackupFormat.write(
            settings = mapOf("pref_theme" to "LIGHT", "pref_sound_volume" to 0.5, "pref_autocorrect" to false),
            dictionaries = mapOf("es" to listOf(LearnedWord("Ameizin", 3, "es")), "fr" to listOf(LearnedWord("Quimper", 2, "fr"))),
        )
        val back = BackupFormat.parse(json)
        assertEquals(BackupFormat.VERSION, back.version)
        assertEquals(listOf("Ameizin"), back.words["es"]!!.map { it.word })
        assertEquals(3, back.words["es"]!![0].frequency)
        assertEquals("Quimper", back.words["fr"]!![0].word)
        assertEquals("LIGHT", back.settings["pref_theme"])
        assertEquals(false, back.settings["pref_autocorrect"])
    }

    @Test fun theExportContainsNoTypingHistoryOrExtraMetadata() {
        val json = BackupFormat.write(emptyMap(), mapOf("es" to listOf(LearnedWord("hola", 2, "es"))))
        val keys = org.json.JSONObject(json).keys().asSequence().toSet()
        assertEquals(setOf("version", "app", "settings", "dictionaries"), keys)
    }

    @Test fun theOldSingleLanguageFormatStillImports() {
        val old = """{"version":1,"app":"MetroBoard","language":"en","words":[{"word":"keyboardy","frequency":4}],"settings":{"pref_theme":"DARK"}}"""
        val back = BackupFormat.parse(old)
        assertEquals("keyboardy", back.words["en"]!!.single().word)
        assertEquals("DARK", back.settings["pref_theme"])
    }

    @Test fun theChiFlowFormatWithAPlainWordListStillImports() {
        val back = BackupFormat.parse("""{"version":1,"app":"ChiFlow","custom_words":["hola","mundo",42,"ok ok"],"settings":{"pref_sound":true,"pref_accent_color":-16743185}}""")
        assertEquals(listOf("hola", "mundo", "42"), back.words["es"]!!.map { it.word })
        assertEquals(true, back.settings["pref_sound"])
    }

    @Test fun malformedJsonAndWrongShapesAreRejected() {
        expectRejected("not json at all", "garbage")
        expectRejected("[1,2,3]", "array root")
        expectRejected("""{"version":99}""", "unknown future version")
        expectRejected("""{"version":2,"dictionaries":"x"}""", "dictionaries must be an object")
        expectRejected("""{"version":2,"dictionaries":{"es":5}}""", "language list must be an array")
    }

    @Test fun oversizedInputIsRejectedBeforeItIsFullyRead() {
        val big = ByteArray(BackupFormat.MAX_BYTES + 10) { 'a'.code.toByte() }
        try { BackupFormat.readBounded(ByteArrayInputStream(big)); fail("read more than the limit") }
        catch (_: BackupFormat.InvalidBackup) { }
    }

    @Test fun invalidUtf8IsRejectedNotSilentlyMangled() {
        val bad = byteArrayOf('{'.code.toByte(), 0xC3.toByte(), 0x28, '}'.code.toByte())
        try { BackupFormat.readBounded(ByteArrayInputStream(bad)); fail("accepted invalid UTF-8") }
        catch (_: BackupFormat.InvalidBackup) { }
    }

    @Test fun tooManyWordsAreRejected() {
        fun word(i: Int): String { var n = i; val sb = StringBuilder("pa"); do { sb.append('a' + n % 26); n /= 26 } while (n > 0); return sb.toString() }
        val words = (0..BackupFormat.MAX_WORDS_PER_LANGUAGE).joinToString(",") { """{"word":"${word(it)}","frequency":2}""" }
        expectRejected("""{"version":2,"dictionaries":{"es":[$words]}}""", "too many words")
    }

    @Test fun unsafeWordsAreDroppedAndGoodOnesKept() {
        val back = BackupFormat.parse("""{"version":2,"dictionaries":{"es":[
            {"word":"bueno","frequency":5},
            {"word":"a","frequency":5},
            {"word":"${"x".repeat(41)}","frequency":5},
            {"word":"con espacio","frequency":5},
            {"word":"wifi6","frequency":5},
            {"word":"https://evil.example","frequency":5},
            {"word":"don't","frequency":2},
            {"word":"ñandú","frequency":999999}
        ]}}""")
        val kept = back.words["es"]!!.associate { it.word to it.frequency }
        assertEquals(setOf("bueno", "wifi6", "don't", "ñandú"), kept.keys)
        assertTrue("frequency is clamped", kept["ñandú"]!! <= BackupFormat.MAX_FREQUENCY)
    }

    @Test fun unknownLanguagesAreIgnored() {
        val back = BackupFormat.parse("""{"version":2,"dictionaries":{"zz":[{"word":"palabra","frequency":2}],"es":[{"word":"hola","frequency":2}]}}""")
        assertEquals(setOf("es"), back.words.keys)
    }

    @Test fun settingsAreWhitelistedTypedAndRangeChecked() {
        val back = BackupFormat.parse("""{"version":2,"settings":{
            "pref_theme":"NEON",
            "pref_height_scale":"huge",
            "pref_sound_volume":9.5,
            "pref_autocorrect":"yes",
            "pref_accent_color":-65536,
            "pref_language":"es",
            "totally_unknown":"x",
            "pref_haptic_strength_level":"STRONG"
        }}""")
        assertNull("not one of DARK/LIGHT/SYSTEM", back.settings["pref_theme"])
        assertNull("wrong type", back.settings["pref_height_scale"])
        assertEquals("volume clamped to 0..1", 1.0, (back.settings["pref_sound_volume"] as Number).toDouble(), 0.0)
        assertNull("string where a boolean is required", back.settings["pref_autocorrect"])
        assertEquals(-65536, (back.settings["pref_accent_color"] as Number).toInt())
        assertFalse(back.settings.containsKey("totally_unknown"))
        assertEquals("es", back.settings["pref_language"])
        assertEquals("STRONG", back.settings["pref_haptic_strength_level"])
    }

    @Test fun nanAndInfinityNeverReachThePreferences() {
        val back = BackupFormat.parse("""{"version":2,"settings":{"pref_height_scale":1e999,"pref_sound_volume":-5}}""")
        val h = back.settings["pref_height_scale"]
        assertTrue("height is either dropped or finite and in range", h == null || (h as Number).toDouble() in 0.7..1.4)
        assertEquals(0.0, (back.settings["pref_sound_volume"] as Number).toDouble(), 0.0)
    }
}
