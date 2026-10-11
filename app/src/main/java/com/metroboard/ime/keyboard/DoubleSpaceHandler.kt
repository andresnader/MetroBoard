package com.metroboard.ime.keyboard

class DoubleSpaceHandler(private val timeoutMs: Long = DOUBLE_SPACE_TIMEOUT_MS) {
    private var firstSpaceAt: Long? = null

    /** Called before each space commit. Only a preceding word can start a pair. */
    fun shouldReplace(now: Long, beforeCursor: String, hasSelection: Boolean): Boolean {
        val previous = firstSpaceAt
        val replace = !hasSelection && previous != null &&
            now - previous in 0..timeoutMs && beforeCursor.endsWith(" ") &&
            beforeCursor.dropLast(1).lastOrNull()?.isLetterOrDigit() == true
        firstSpaceAt = if (!replace && !hasSelection &&
            beforeCursor.lastOrNull()?.isLetterOrDigit() == true) now else null
        return replace
    }

    fun reset() { firstSpaceAt = null }

    companion object { const val DOUBLE_SPACE_TIMEOUT_MS = 400L }
}
