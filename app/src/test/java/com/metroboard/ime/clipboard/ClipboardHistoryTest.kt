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
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    private class MemoryStore(var saved: List<Clip> = emptyList(), var saves: Int = 0) : PinnedClipStore {
        override fun load() = saved
        override fun save(pinned: List<Clip>) { saved = pinned; saves++ }
    }

    private val hour = ClipboardHistory.DEFAULT_TTL_MILLIS
    private fun history(store: MemoryStore = MemoryStore(), maxClips: Int = 20) = ClipboardHistory(store, maxClips = maxClips)
    private fun ClipboardHistory.texts(now: Long) = clips(now).map { it.text }

    @Test fun newestClipComesFirst() {
        val h = history()
        h.record("one", 1, false); h.record("two", 2, false); h.record("three", 3, false)
        assertEquals(listOf("three", "two", "one"), h.texts(4))
    }

    @Test fun repeatedCopyMovesToTheTopInsteadOfDuplicating() {
        val h = history()
        h.record("a", 1, false); h.record("b", 2, false); h.record("a", 3, false)
        assertEquals(listOf("a", "b"), h.texts(4))
    }

    @Test fun sensitiveBlankAndHugeClipsAreNeverKept() {
        val h = history()
        assertFalse(h.record("hunter2", 1, sensitive = true))
        assertFalse(h.record("   \n ", 1, false))
        assertFalse(h.record("x".repeat(ClipboardHistory.DEFAULT_MAX_CHARS + 1), 1, false))
        assertTrue(h.record("x".repeat(ClipboardHistory.DEFAULT_MAX_CHARS), 1, false))
        assertEquals(1, h.clips(2).size)
    }

    @Test fun unpinnedClipsExpireAfterAnHour() {
        val h = history()
        h.record("old", 0, false)
        assertEquals(listOf("old"), h.texts(hour - 1))
        assertEquals(emptyList<String>(), h.texts(hour))
    }

    @Test fun pinnedClipsDoNotExpireAndSortFirst() {
        val h = history()
        h.record("keep", 0, false)
        h.setPinned(h.clips(1).single().id, true)
        h.record("fresh", 10 * hour, false)
        assertEquals(listOf("keep", "fresh"), h.texts(10 * hour + 1))
    }

    @Test fun onlyTheLatestTwentyUnpinnedClipsAreKeptAndPinnedOnesDoNotCount() {
        val h = history(maxClips = 3)
        h.record("p", 0, false); h.setPinned(h.clips(0).single().id, true)
        for (i in 1..5) h.record("c$i", i.toLong(), false)
        assertEquals(listOf("p", "c5", "c4", "c3"), h.texts(6))
    }

    @Test fun onlyPinnedClipsReachTheStore() {
        val store = MemoryStore()
        val h = history(store)
        h.record("secret-ish", 1, false)
        assertEquals("an unpinned clip must not be written anywhere", 0, store.saves)
        h.setPinned(h.clips(2).single().id, true)
        assertEquals(listOf("secret-ish"), store.saved.map { it.text })
        h.setPinned(h.clips(2).single().id, false)
        assertEquals(emptyList<Clip>(), store.saved)
    }

    @Test fun pinnedClipsSurviveARestartButUnpinnedOnesDoNot() {
        val store = MemoryStore()
        val first = history(store)
        first.record("pinned", 1, false); first.record("loose", 2, false)
        first.setPinned(first.clips(3).first { it.text == "pinned" }.id, true)

        val second = history(store)
        assertEquals(listOf("pinned"), second.texts(4))
        second.record("new", 5, false)
        assertTrue("ids must not collide with restored pins", second.clips(6).map { it.id }.distinct().size == 2)
    }

    @Test fun deleteAndClearBehaveAsAdvertised() {
        val store = MemoryStore()
        val h = history(store)
        h.record("a", 1, false); h.record("b", 2, false); h.record("c", 3, false)
        h.setPinned(h.clips(4).first { it.text == "a" }.id, true)
        h.delete(h.clips(4).first { it.text == "b" }.id)
        assertEquals(listOf("a", "c"), h.texts(4))
        h.clearUnpinned()
        assertEquals(listOf("a"), h.texts(4))
        h.clearAll()
        assertEquals(emptyList<String>(), h.texts(4))
        assertEquals("clearing everything also erases the stored pins", emptyList<Clip>(), store.saved)
    }

    @Test fun listenersHearEveryChange() {
        val h = history()
        var calls = 0
        h.addListener { calls++ }
        h.record("a", 1, false)
        h.setPinned(h.clips(2).single().id, true)
        h.delete(h.clips(2).single().id)
        assertEquals(3, calls)
    }

    // ---- editing what was saved ------------------------------------------------

    @Test fun editingChangesTheTextAndKeepsPinAndPlace() {
        val h = history()
        h.record("old", 1, false); h.record("other", 2, false)
        val id = h.clips(3).first { it.text == "old" }.id
        h.setPinned(id, true)
        assertTrue(h.edit(id, "new text", 3))
        val edited = h.clips(4).first { it.id == id }
        assertEquals("new text", edited.text)
        assertTrue(edited.pinned)
        assertEquals("the copy time is not reset, so it is not offered as freshly copied", 1L, edited.copiedAt)
    }

    @Test fun anEditedPinnedClipIsWrittenToTheStore() {
        val store = MemoryStore()
        val h = history(store)
        h.record("keep", 1, false)
        val id = h.clips(2).single().id
        h.setPinned(id, true)
        h.edit(id, "kept and changed", 3)
        assertEquals(listOf("kept and changed"), store.saved.map { it.text })
    }

    @Test fun editingFollowsTheSameRulesAsCopying() {
        val h = history()
        h.record("a", 1, false)
        val id = h.clips(2).single().id
        assertFalse(h.edit(id, "   \n ", 3))
        assertFalse(h.edit(id, "x".repeat(ClipboardHistory.DEFAULT_MAX_CHARS + 1), 3))
        assertFalse(h.edit(999L, "no such clip", 3))
        assertEquals(listOf("a"), h.texts(4))
        assertTrue(h.edit(id, "x".repeat(ClipboardHistory.DEFAULT_MAX_CHARS), 3))
    }

    @Test fun editingATextIntoAnotherClipMergesThemAndKeepsAnyPin() {
        val h = history()
        h.record("first", 1, false); h.record("second", 2, false)
        val first = h.clips(3).first { it.text == "first" }.id
        val second = h.clips(3).first { it.text == "second" }.id
        h.setPinned(first, true)
        assertTrue(h.edit(second, "first", 3))
        val all = h.clips(4)
        assertEquals(1, all.size)
        assertEquals("first", all.single().text)
        assertTrue("the pin of either clip survives the merge", all.single().pinned)
    }

    @Test fun editingNotifiesTheListeners() {
        val h = history()
        h.record("a", 1, false)
        var notified = 0
        h.addListener { notified++ }
        h.edit(h.clips(2).single().id, "b", 3)
        assertEquals(1, notified)
    }

    @Test fun anUnchangedTextIsAValidEditThatChangesNothing() {
        val h = history()
        h.record("same", 1, false)
        assertTrue(h.edit(h.clips(2).single().id, "same", 3))
        assertEquals(listOf("same"), h.texts(4))
    }
}
