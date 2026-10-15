/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.clipboard

/** One entry of the clipboard history. [id] is stable for the life of the entry. */
data class Clip(val id: Long, val text: String, val pinned: Boolean, val copiedAt: Long)

/** Where pinned clips are kept between runs. Everything else lives in memory only. */
interface PinnedClipStore {
    fun load(): List<Clip>
    fun save(pinned: List<Clip>)
}

/**
 * The live clipboard history behind the keyboard's clipboard panel.
 *
 * Privacy rules, all enforced here so they cannot be forgotten by a caller:
 *  - only text, never more than [maxChars] characters, never anything the source marked as sensitive;
 *  - clips that are not pinned stay in memory, hold at most [maxClips] entries and expire after [ttlMillis];
 *  - only *pinned* clips are written to [PinnedClipStore] (app-private storage, excluded from backups).
 *
 * Not thread-safe: it is meant to be used from the main thread, like the views that show it.
 */
class ClipboardHistory(
    private val store: PinnedClipStore,
    private val maxClips: Int = DEFAULT_MAX_CLIPS,
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val items = ArrayList<Clip>()
    private val listeners = ArrayList<() -> Unit>()
    private var nextId = 1L

    init {
        store.load().forEach { items += it.copy(pinned = true); nextId = maxOf(nextId, it.id + 1) }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    /**
     * Records a copy. Returns whether it was kept. An identical clip is not duplicated: it moves to the
     * top (keeping its pin) and its expiry clock restarts.
     */
    fun record(text: String, now: Long, sensitive: Boolean): Boolean {
        if (sensitive || text.isBlank() || text.length > maxChars) return false
        expire(now)
        val existing = items.indexOfFirst { it.text == text }
        val clip = if (existing >= 0) items.removeAt(existing).copy(copiedAt = now)
        else Clip(nextId++, text, pinned = false, copiedAt = now)
        items.add(0, clip)
        trim()
        persistAndNotify(pinsChanged = clip.pinned)
        return true
    }

    /** Pinned clips first, then the rest newest first. Expired clips are dropped before listing. */
    fun clips(now: Long): List<Clip> {
        if (expire(now)) persistAndNotify(pinsChanged = false)
        return items.filter { it.pinned }.sortedByDescending { it.copiedAt } +
            items.filter { !it.pinned }.sortedByDescending { it.copiedAt }
    }

    /**
     * Changes the text of a saved clip, keeping its pin and its place (its copy time is not reset, so an edited clip
     * is not offered as freshly copied). The same rules as for copying apply: not blank, at most [maxChars]. When the
     * new text equals another clip the two merge into this one and the merged clip is pinned if either was. False,
     * and nothing changes, when the clip does not exist or the text is not acceptable.
     */
    fun edit(id: Long, newText: String, now: Long): Boolean {
        expire(now)
        val index = items.indexOfFirst { it.id == id }
        if (index < 0 || newText.isBlank() || newText.length > maxChars) return false
        val current = items[index]
        if (current.text == newText) return true
        val twin = items.indexOfFirst { it.id != id && it.text == newText }
        var pinned = current.pinned
        if (twin >= 0) {
            pinned = pinned || items[twin].pinned
            items.removeAt(twin)
        }
        val at = items.indexOfFirst { it.id == id }
        items[at] = current.copy(text = newText, pinned = pinned)
        persistAndNotify(pinsChanged = pinned || current.pinned)
        return true
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0 || items[index].pinned == pinned) return
        items[index] = items[index].copy(pinned = pinned)
        persistAndNotify(pinsChanged = true)
    }

    fun delete(id: Long) {
        val removed = items.firstOrNull { it.id == id } ?: return
        items.remove(removed)
        persistAndNotify(pinsChanged = removed.pinned)
    }

    /** Removes every clip that is not pinned; pinned ones are kept, as the user chose to keep them. */
    fun clearUnpinned() {
        if (items.removeAll { !it.pinned }) persistAndNotify(pinsChanged = false)
    }

    /** Removes everything, pinned clips included, and erases what was stored. */
    fun clearAll() {
        if (items.isEmpty()) return
        items.clear()
        persistAndNotify(pinsChanged = true)
    }

    private fun expire(now: Long): Boolean = items.removeAll { !it.pinned && now - it.copiedAt >= ttlMillis }

    private fun trim() {
        while (items.count { !it.pinned } > maxClips) {
            items.removeAt(items.indexOfLast { !it.pinned })
        }
    }

    private fun persistAndNotify(pinsChanged: Boolean) {
        if (pinsChanged) store.save(items.filter { it.pinned })
        listeners.toList().forEach { it() }
    }

    companion object {
        const val DEFAULT_MAX_CLIPS = 20
        const val DEFAULT_MAX_CHARS = 2000
        const val DEFAULT_TTL_MILLIS = 60 * 60 * 1000L
    }
}
