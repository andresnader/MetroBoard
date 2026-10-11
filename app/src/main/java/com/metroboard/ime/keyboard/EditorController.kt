/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.keyboard

import com.metroboard.ime.prediction.SuggestionEngine
import java.util.Locale

class EditorController(
    private val editor: EditorPort,
    private val engine: SuggestionEngine,
    val shift: ShiftStateManager = ShiftStateManager(),
    private val spaces: DoubleSpaceHandler = DoubleSpaceHandler(),
) {
    var policy = EditingPolicy()
        private set

    /** The last automatic correction, kept only until the next unrelated input so Backspace can undo it. */
    private data class AppliedCorrection(val original: String, val applied: String)
    private var lastCorrection: AppliedCorrection? = null

    /** Words whose correction the user undid; never corrected again in this session. */
    private val rejectedCorrections = HashSet<String>()

    fun setLanguage(language: String) {
        engine.setLanguage(language)
    }

    fun start(policy: EditingPolicy) {
        this.policy = policy
        lastCorrection = null
        spaces.reset()
        shift.reset(autoCaps())
    }

    fun finish() {
        spaces.reset()
        shift.reset(false)
    }

    fun tapShift(now: Long) {
        if (shift.state == ShiftState.CAPS_LOCK) {
            spaces.reset()
            shift.tap(now)
            return
        }
        // Selected text: Shift cycles its casing and keeps it selected (corpus B002, confirmed).
        if (policy.shiftWordCasing && editor.hasSelection()) {
            val selected = editor.selectedText()
            if (selected != null) {
                if (selected.any(Char::isLetter)) editor.replaceSelectionKeepingIt(nextCasing(selected))
                return // never mix with the one-shot state of the next letter
            }
        }
        val word = if (policy.shiftWordCasing) prefix() else null
        if (word != null && word.any(Char::isLetter)) {
            cycleWordCasing(word)
            return
        }
        spaces.reset()
        shift.tap(now)
    }

    /** palabra -> Palabra -> PALABRA -> palabra. */
    private fun nextCasing(word: String): String = when {
        word.all { it.isLowerCase() || !it.isLetter() } -> word.replaceFirstChar { it.uppercaseChar() }
        word.drop(1).all { it.isLowerCase() || !it.isLetter() } && word.first().isUpperCase() -> word.uppercase(Locale.ROOT)
        else -> word.lowercase(Locale.ROOT)
    }

    private fun cycleWordCasing(word: String) {
        val next = nextCasing(word)
        editor.beginBatch()
        try {
            editor.deleteBeforeCursor(word.codePointCount(0, word.length))
            editor.commit(next)
        } finally {
            editor.endBatch()
        }
    }

    fun cancelSequence() {
        lastCorrection = null
        spaces.reset()
        shift.breakTapSequence()
    }

    fun cursorMoved() {
        spaces.reset()
        shift.onCursorMoved(autoCaps())
    }

    fun text(value: String) {
        cancelSequence()
        val letter = value.any(Char::isLetter)
        val output = if (letter && (shift.isUppercase || policy.forceUppercase))
            value.uppercase(Locale.ROOT) else value
        if (!editor.commit(output)) return
        if (letter) shift.onLetterCommitted()
        // Only a newline starts a sentence at once. After ".", "?" or "!" the next letter is capital
        // only once a space follows (autoCaps() asks the editor), so "www.google.com" and "3.14" stay intact.
        else if (policy.automaticCapitalization && value.lastOrNull() == '\n') {
            shift.onSentenceBoundary()
        } else shift.updateAutomatic(autoCaps())
    }

    /**
     * Inserts [value] exactly as it was copied. Unlike [text], which types a character under the current
     * Shift state, pasted text is never recased: a clip copied as "Segundo texto" must not become
     * "SEGUNDO TEXTO" because the field happened to start with automatic capital.
     */
    fun paste(value: String) {
        cancelSequence()
        if (value.isEmpty() || !editor.commit(value)) return
        if (value.any(Char::isLetter)) shift.onLetterCommitted() else shift.updateAutomatic(autoCaps())
    }

    fun space(now: Long) {
        shift.breakTapSequence()
        lastCorrection = null
        // Sensitive / no-learning fields must never have their text read.
        val lastWord = (if (policy.suggestions || policy.learnWords) prefix() else null)
            ?.takeUnless { inUrlOrEmailToken() }
        val correction = if (lastWord != null && policy.suggestions && policy.autoCorrect && mayAutoCorrect(lastWord)) {
            engine.autoCorrect(lastWord)
        } else null
        var applied: AppliedCorrection? = null
        if (correction != null && lastWord != null && correction != lastWord) {
            val formattedCorrection = formatCandidate(correction, lastWord.first().isUpperCase())
            editor.beginBatch()
            try {
                if (editor.deleteBeforeCursor(lastWord.codePointCount(0, lastWord.length)) && editor.commit(formattedCorrection)) {
                    shift.onLetterCommitted()
                    applied = AppliedCorrection(lastWord, formattedCorrection)
                }
            } finally {
                editor.endBatch()
            }
        } else if (lastWord != null) {
            learnIfAllowed(lastWord)
        }
        if (!policy.doubleSpace) {
            spaces.reset()
            editor.commit(" ")
            lastCorrection = applied
            return
        }
        val replace = spaces.shouldReplace(now, editor.beforeCursor(3), editor.hasSelection())
        if (replace) {
            editor.beginBatch()
            try {
                if (editor.deleteBeforeCursor(1) && editor.commit(". ")) shift.onSentenceBoundary()
                else spaces.reset()
            } finally { editor.endBatch() }
        } else {
            if (!editor.commit(" ")) spaces.reset()
            shift.updateAutomatic(autoCaps())
            lastCorrection = applied
        }
    }

    /** Proper nouns, acronyms and words the user already rejected are left exactly as typed. */
    private fun mayAutoCorrect(word: String): Boolean {
        if (word.lowercase(Locale.ROOT) in rejectedCorrections) return false
        if (word.drop(1).any(Char::isUpperCase)) return false // acronyms, camelCase, ALL CAPS
        if (word.first().isUpperCase() && !startsSentence(word)) return false // probable proper noun
        return true
    }

    private fun startsSentence(word: String): Boolean {
        val before = editor.beforeCursor(word.length + 6).dropLast(word.length)
        if (before.isEmpty() || before.endsWith("\n")) return true
        val last = before.trimEnd(' ').lastOrNull() ?: return true
        return last in ".!?\u2026\u00bf\u00a1\n"
    }

    /**
     * True when the whitespace-delimited token ending at the cursor is a URL, an e-mail address or an
     * identifier ("www.site.com", "ana@mail.com", "snake_case", "http://x"). Its pieces are not
     * words: they are neither auto-corrected nor learned.
     */
    private fun inUrlOrEmailToken(): Boolean {
        val token = editor.beforeCursor(80).takeLastWhile { !it.isWhitespace() }
        return token.any { it == '@' || it == '/' || it == ':' || it == '_' || it == '\\' } ||
            Regex("\\p{L}\\.\\p{L}").containsMatchIn(token)
    }

    private fun learnIfAllowed(word: String, explicit: Boolean = false) {
        if (policy.learnWords && word.any(Char::isLetter)) engine.learn(word, explicit)
    }

    /** Backspace right after an automatic correction restores what the user typed. */
    private fun undoLastCorrection(): Boolean {
        val correction = lastCorrection ?: return false
        if (editor.hasSelection()) return false
        val expected = correction.applied + " "
        if (editor.beforeCursor(expected.length) != expected) return false
        editor.beginBatch()
        try {
            if (!editor.deleteBeforeCursor(expected.codePointCount(0, expected.length))) return false
            editor.commit(correction.original)
        } finally {
            editor.endBatch()
        }
        rejectedCorrections.add(correction.original.lowercase(Locale.ROOT))
        learnIfAllowed(correction.original, explicit = true) // the user insisted on this exact word
        lastCorrection = null
        return true
    }

    fun backspace() {
        if (undoLastCorrection()) {
            spaces.reset()
            shift.onCursorMoved(autoCaps())
            return
        }
        cancelSequence()
        if (editor.hasSelection()) editor.commit("") else editor.deleteBeforeCursor(lastGraphemeLength())
        shift.onCursorMoved(autoCaps())
    }

    fun enter() {
        if (policy.learnWords) prefix()?.takeUnless { inUrlOrEmailToken() }?.let { learnIfAllowed(it) }
        cancelSequence()
        editor.enter()
        shift.updateAutomatic(autoCaps())
    }

    fun suggestions(): List<String> {
        if (!policy.suggestions) return emptyList()
        // A word the editor has selected (the user double-tapped it in the host app): offer replacements.
        // An IME cannot see taps inside another app's text field, but it does see the resulting selection.
        if (editor.hasSelection()) {
            val selected = editor.selectedText()?.takeIf(::isSingleWord) ?: return emptyList()
            return engine.suggestReplacements(selected).take(3).map { formatCandidate(it, selected.first().isUpperCase()) }
        }
        val prefix = prefix()
        if (prefix == null) {
            val twoWords = previousTwoWords()
            if (twoWords != null) {
                val trigrams = engine.suggestNextTrigram(twoWords.first, twoWords.second)
                if (trigrams.isNotEmpty()) {
                    return trigrams.take(3).map { formatCandidate(it, false) }
                }
            }
            val prev = previousWord() ?: return emptyList()
            return engine.suggestNext(prev).take(3).map { formatCandidate(it, false) }
        }
        return engine.suggest(prefix).take(3).map { formatCandidate(it, prefix.first().isUpperCase()) }
    }

    private fun formatCandidate(candidate: String, capitalizeFirst: Boolean): String = when {
        policy.forceUppercase || shift.state == ShiftState.CAPS_LOCK -> candidate.uppercase(Locale.ROOT)
        capitalizeFirst -> candidate.replaceFirstChar { it.uppercaseChar() }
        else -> candidate
    }

    /** Re-read/revalidate the prefix on tap; do not replace stale or partial words. */
    fun chooseSuggestion(candidate: String) {
        if (!policy.suggestions || candidate !in suggestions()) return
        if (editor.hasSelection()) {
            // Replace exactly the selection: no trailing space, nothing else is touched.
            cancelSequence()
            if (editor.commit(candidate)) shift.onLetterCommitted()
            return
        }
        val prefix = prefix()
        cancelSequence()
        editor.beginBatch()
        try {
            // One trailing space, unless the text already continues with whitespace.
            val trailing = if (editor.afterCursor(1).firstOrNull()?.isWhitespace() == true) "" else " "
            if (prefix != null) {
                if (editor.deleteBeforeCursor(prefix.codePointCount(0, prefix.length)) && editor.commit(candidate + trailing)) {
                    shift.onLetterCommitted()
                }
            } else {
                if (editor.commit(candidate + trailing)) {
                    shift.onLetterCommitted()
                }
            }
        } finally { editor.endBatch() }
    }

    fun commitWordFlow(candidate: String) {
        val word = formatCandidate(candidate, shift.isUppercase)
        val prefix = prefix()
        cancelSequence()
        editor.beginBatch()
        try {
            if (prefix != null) {
                editor.deleteBeforeCursor(prefix.codePointCount(0, prefix.length))
            }
            if (editor.commit("$word ")) {
                shift.onLetterCommitted()
            }
        } finally {
            editor.endBatch()
        }
    }

    fun deleteWords(count: Int = 1) {
        cancelSequence()
        editor.beginBatch()
        try {
            for (i in 0 until count) {
                val context = editor.beforeCursor(64)
                if (context.isEmpty()) break
                val trailingWs = context.takeLastWhile { it.isWhitespace() }.length
                if (trailingWs > 0) {
                    editor.deleteBeforeCursor(trailingWs)
                }
                val remaining = editor.beforeCursor(64)
                val wordLen = remaining.takeLastWhile { !it.isWhitespace() }.length
                if (wordLen > 0) {
                    editor.deleteBeforeCursor(wordLen)
                }
            }
        } finally {
            editor.endBatch()
        }
        shift.onCursorMoved(autoCaps())
    }

    /**
     * Letters, plus an apostrophe between letters ("don't"). In elision languages (French) the
     * apostrophe ends the previous word, so "c'est" is the word "est" after the elided "c'".
     */
    private fun lastWordOf(context: String): String {
        if (engine.elidesApostrophe) return context.takeLastWhile(Char::isLetter)
        val raw = context.takeLastWhile { it.isLetter() || it == '\'' || it == '\u2019' }.trimStart('\'', '\u2019')
        return if (raw.endsWith('\'') || raw.endsWith('\u2019')) "" else raw
    }

    private fun prefix(): String? {
        if (editor.hasSelection() || editor.afterCursor(1).firstOrNull()?.isLetter() == true) return null
        val word = lastWordOf(editor.beforeCursor(49))
        return word.takeIf { it.isNotEmpty() && it.length <= 48 }
    }

    /**
     * Code points in the last user-perceived character before the cursor (emoji with skin tone, flags,
     * ZWJ families, letter + combining mark, CRLF are one each). Password fields are never read: 1.
     */
    private fun lastGraphemeLength(): Int {
        if (policy.sensitive) return 1
        val before = editor.beforeCursor(GRAPHEME_WINDOW)
        if (before.isEmpty()) return 1
        val boundaries = java.text.BreakIterator.getCharacterInstance()
        boundaries.setText(before)
        val end = boundaries.last()
        val start = boundaries.previous() // (preceding(last()) returns DONE on some runtimes)
        if (start == java.text.BreakIterator.DONE) return 1
        return before.codePointCount(start, end).coerceAtLeast(1)
    }

    private companion object {
        /** Longest grapheme cluster we expect (a ZWJ family is 8 code points / 11 UTF-16 units). */
        const val GRAPHEME_WINDOW = 32
    }

    private fun isSingleWord(text: String): Boolean =
        text.isNotEmpty() && text.length <= 48 && text.any(Char::isLetter) &&
            text.all { it.isLetter() || it == '\'' || it == '\u2019' }

    private fun previousWord(): String? {
        if (editor.hasSelection() || editor.afterCursor(1).firstOrNull()?.isLetter() == true) return null
        val context = editor.beforeCursor(49)
        if (context.isEmpty() || !context.last().isWhitespace()) return null
        val word = lastWordOf(context.trimEnd())
        return word.takeIf { it.isNotEmpty() && it.length <= 48 }
    }

    private fun previousTwoWords(): Pair<String, String>? {
        if (editor.hasSelection() || editor.afterCursor(1).firstOrNull()?.isLetter() == true) return null
        val context = editor.beforeCursor(99)
        if (context.isEmpty() || !context.last().isWhitespace()) return null
        val tokens = context.trimEnd().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.all { ch -> ch.isLetter() || (!engine.elidesApostrophe && (ch == '\'' || ch == '\u2019')) } }
        if (tokens.size >= 2) {
            val w1 = tokens[tokens.size - 2]
            val w2 = tokens[tokens.size - 1]
            if (w1.length <= 48 && w2.length <= 48) {
                return w1 to w2
            }
        }
        return null
    }

    private fun autoCaps() = policy.automaticCapitalization && editor.shouldCapitalize()
}
