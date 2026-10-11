/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.clipboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pinned clips in their own private preferences file. It is separate from the settings file so the
 * settings backup can never include clipboard text, and the app disables cloud backup altogether.
 */
class SharedPreferencesPinnedStore(context: Context) : PinnedClipStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): List<Clip> = try {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Clip(item.getLong("id"), item.getString("text"), pinned = true, copiedAt = item.getLong("at"))
        }
    } catch (_: Exception) {
        emptyList() // a damaged file is treated as empty rather than breaking the keyboard
    }

    override fun save(pinned: List<Clip>) {
        val array = JSONArray()
        pinned.forEach {
            array.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.copiedAt))
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val FILE = "clipboard_pins"
        const val KEY = "pinned"
    }
}

/**
 * The one history of this process. The keyboard service and the Settings screen both use it, so clearing it
 * from Settings empties what the keyboard is showing, not a private copy. Main thread only.
 */
object ClipboardHistories {
    private var shared: ClipboardHistory? = null

    fun shared(context: Context): ClipboardHistory =
        shared ?: ClipboardHistory(SharedPreferencesPinnedStore(context.applicationContext)).also { shared = it }
}
