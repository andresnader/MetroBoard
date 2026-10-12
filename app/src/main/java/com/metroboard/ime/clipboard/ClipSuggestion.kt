/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.clipboard

/** What was just copied, ready to paste from the suggestion bar. [copiedAt] identifies this particular copy. */
data class ClipSuggestion(val clipId: Long, val text: String, val preview: String, val isLink: Boolean, val copiedAt: Long)

/**
 * Decides whether the clipboard is worth offering on the suggestion bar right now. Only the newest clip, only for a
 * minute after it was copied (an old copy is not pushed on the user), and never one that was already pasted. The
 * history it reads from already excludes sensitive and over-long clips.
 */
object ClipSuggestions {
    const val MAX_AGE_MS = 60_000L
    const val PREVIEW_CHARS = 26

    /** [usedAt]: the [Clip.copiedAt] of the copy that was pasted last; that copy is not offered again. */
    fun fresh(clips: List<Clip>, now: Long, usedAt: Long): ClipSuggestion? {
        val newest = clips.maxByOrNull { it.copiedAt } ?: return null
        val age = now - newest.copiedAt
        if (age < 0 || age > MAX_AGE_MS || newest.copiedAt <= usedAt) return null
        return ClipSuggestion(newest.id, newest.text, preview(newest.text), isLink(newest.text), newest.copiedAt)
    }

    /** A single short line: whitespace collapsed, a link without its scheme and "www.", cut with an ellipsis. */
    fun preview(text: String): String {
        var line = text.trim().replace(WHITESPACE, " ")
        if (isLink(text)) line = line.replace(SCHEME, "")
        return if (line.length <= PREVIEW_CHARS) line else line.take(PREVIEW_CHARS - 1) + "\u2026"
    }

    /** True when the whole text is one web address. */
    fun isLink(text: String): Boolean = LINK.matches(text.trim())

    private val WHITESPACE = Regex("\\s+")
    private val SCHEME = Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE)
    private val LINK = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
}
