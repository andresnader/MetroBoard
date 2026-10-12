/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import android.os.SystemClock
import android.util.Log

/**
 * Debug builds only: times the phases of handling one key so the slow one is found by measuring, not guessing.
 * Each phase is added up by name and [flush] prints them on one line: `adb logcat -s MB_PHASES`.
 * Does nothing (and costs one boolean check) when [enabled] is false.
 */
object LatencyProbe {
    @Volatile var enabled = false
    private val phases = LinkedHashMap<String, Long>()

    inline fun <T> time(name: String, block: () -> T): T {
        if (!enabled) return block()
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            add(name, SystemClock.elapsedRealtimeNanos() - start)
        }
    }

    fun add(name: String, nanos: Long) {
        synchronized(phases) { phases[name] = (phases[name] ?: 0L) + nanos }
    }

    fun flush(label: String) {
        if (!enabled) return
        val line = synchronized(phases) {
            val text = phases.entries.joinToString(" ") { "${it.key}=${"%.1f".format(it.value / 1_000_000.0)}" }
            phases.clear()
            text
        }
        Log.d("MB_PHASES", "$label $line")
    }
}
