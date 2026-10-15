/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What was just copied is offered on the suggestion bar, ready to paste: the newest clip, only while it is fresh. */
class ClipSuggestionTest {
    private fun clip(id: Long, text: String, copiedAt: Long, pinned: Boolean = false) = Clip(id, text, pinned, copiedAt)
    private val now = 1_000_000L

    @Test fun theNewestRecentClipIsOffered() {
        val offered = ClipSuggestions.fresh(listOf(clip(1, "old", now - 30_000), clip(2, "new", now - 5_000)), now, usedAt = 0)
        assertEquals(2L, offered!!.clipId)
        assertEquals("new", offered.text)
    }

    @Test fun nothingIsOfferedOnceItIsOlderThanAMinute() {
        assertNull(ClipSuggestions.fresh(listOf(clip(1, "x", now - ClipSuggestions.MAX_AGE_MS - 1)), now, 0))
        assertEquals(1L, ClipSuggestions.fresh(listOf(clip(1, "x", now - ClipSuggestions.MAX_AGE_MS)), now, 0)!!.clipId)
    }

    @Test fun aClipAlreadyUsedIsNotOfferedAgain() {
        val copiedAt = now - 20_000
        val offered = ClipSuggestions.fresh(listOf(clip(7, "x", copiedAt)), now, usedAt = 0)!!
        assertNull(ClipSuggestions.fresh(listOf(clip(7, "x", copiedAt)), now, usedAt = offered.copiedAt))
    }

    @Test fun copyingTheSameTextAgainOffersItAgain() {
        // The history reuses the id of a repeated copy and only moves its time forward.
        val used = ClipSuggestions.fresh(listOf(clip(7, "x", now - 40_000)), now, 0)!!.copiedAt
        val again = ClipSuggestions.fresh(listOf(clip(7, "x", now - 1_000)), now, usedAt = used)
        assertEquals("x", again!!.text)
    }

    @Test fun aPinnedClipThatIsOldIsNotPushedOnTheUser() {
        assertNull(ClipSuggestions.fresh(listOf(clip(1, "pinned", now - 3_600_000, pinned = true)), now, 0))
    }

    @Test fun aClockThatWentBackwardsOffersNothing() {
        assertNull(ClipSuggestions.fresh(listOf(clip(1, "x", now + 5_000)), now, 0))
    }

    @Test fun noClipsNoSuggestion() {
        assertNull(ClipSuggestions.fresh(emptyList(), now, 0))
    }

    @Test fun thePreviewIsOneShortLine() {
        assertEquals("hello world", ClipSuggestions.preview("  hello \n\n   world  "))
        val long = ClipSuggestions.preview("a".repeat(100))
        assertEquals(ClipSuggestions.PREVIEW_CHARS, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("short", ClipSuggestions.preview("short"))
    }

    @Test fun aLinkIsShownWithoutTheSchemeAndWww() {
        assertEquals("ameizin.ec/blog", ClipSuggestions.preview("https://www.ameizin.ec/blog"))
        assertEquals("example.com", ClipSuggestions.preview("http://example.com"))
        assertTrue(ClipSuggestions.isLink("https://ameizin.ec"))
        assertTrue(ClipSuggestions.isLink("http://a.b/c?d=e"))
        assertFalse(ClipSuggestions.isLink("see https://ameizin.ec"))
        assertFalse(ClipSuggestions.isLink("ameizin.ec"))
    }
}
