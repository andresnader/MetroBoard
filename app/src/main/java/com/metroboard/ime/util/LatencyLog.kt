/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

/**
 * In-memory record of how fast the keyboard answered, for the diagnostics report in Settings. Numbers only: the
 * time a touch waited, the time handling a key took, the time of each phase, main-thread stalls, and the flags of
 * the last few text fields (package, inputType, imeOptions). Never anything that was typed. Not persisted.
 */
class LatencyLog(private val capacity: Int = 600) {
    class Stats(val median: Double, val p95: Double, val p99: Double, val max: Double)
    class KeySummary(val count: Int, val queued: Stats, val handled: Stats)
    class StallSummary(val over50: Int, val over200: Int, val over1000: Int, val longest: Long)
    class DeviceInfo(val model: String, val sdk: Int, val cores: Int, val heapLimitMb: Int, val heapUsedMb: Int)

    private val queued = ArrayDeque<Double>()
    private val handled = ArrayDeque<Double>()
    private val phases = LinkedHashMap<String, ArrayDeque<Double>>()
    private val stalls = ArrayDeque<Long>()
    private val fields = ArrayDeque<String>()

    @Synchronized fun recordKey(queuedMs: Long, handledMs: Long) {
        push(queued, queuedMs.toDouble())
        push(handled, handledMs.toDouble())
    }

    @Synchronized fun recordPhase(name: String, ms: Double) {
        push(phases.getOrPut(name) { ArrayDeque() }, ms)
    }

    @Synchronized fun recordStall(ms: Long) {
        stalls.addLast(ms)
        while (stalls.size > capacity) stalls.removeFirst()
    }

    /** Remembers the kind of field the keyboard was opened in, without repeats, keeping the last [MAX_FIELDS]. */
    @Synchronized fun recordField(packageName: String?, inputType: Int, imeOptions: Int) {
        val line = "${packageName ?: "?"} inputType=0x${Integer.toHexString(inputType)} imeOptions=0x${Integer.toHexString(imeOptions)}"
        fields.remove(line)
        fields.addLast(line)
        while (fields.size > MAX_FIELDS) fields.removeFirst()
    }

    @Synchronized fun clear() {
        queued.clear(); handled.clear(); phases.clear(); stalls.clear(); fields.clear()
    }

    @Synchronized fun keySummary() = KeySummary(queued.size, stats(queued), stats(handled))

    @Synchronized fun phaseSummaries(): Map<String, Stats> = phases.mapValues { stats(it.value) }

    @Synchronized fun stallSummary() = StallSummary(
        stalls.count { it > 50 }, stalls.count { it > 200 }, stalls.count { it > 1000 }, stalls.maxOrNull() ?: 0L,
    )

    /** The text the user copies from Settings and sends for support. */
    @Synchronized fun report(version: String, device: DeviceInfo): String {
        val out = StringBuilder()
        out.appendLine("MetroBoard $version diagnostics (numbers only, no typed text)")
        out.appendLine("Device: ${device.model}, Android SDK ${device.sdk}, ${device.cores} cores, heap ${device.heapUsedMb} of ${device.heapLimitMb} MB")
        val keys = keySummary()
        if (keys.count == 0) {
            out.appendLine("No keys recorded yet: turn diagnostics on, type for a while, then copy the report again.")
        } else {
            out.appendLine("Keys recorded: ${keys.count}")
            out.appendLine("Touch waited before being handled (ms): ${keys.queued.line()}")
            out.appendLine("Handling a key (ms): ${keys.handled.line()}")
            phaseSummaries().forEach { (name, s) -> out.appendLine("  $name (ms): ${s.line()}") }
        }
        val s = stallSummary()
        out.appendLine("Main thread stalls while the keyboard was open: over 50 ms ${s.over50}, over 200 ms ${s.over200}, over 1000 ms ${s.over1000}, longest ${s.longest} ms")
        if (fields.isNotEmpty()) {
            out.appendLine("Last fields:")
            fields.forEach { out.appendLine("  $it") }
        }
        return out.toString()
    }

    private fun push(buffer: ArrayDeque<Double>, value: Double) {
        buffer.addLast(value)
        while (buffer.size > capacity) buffer.removeFirst()
    }

    private fun stats(values: Collection<Double>): Stats {
        if (values.isEmpty()) return Stats(0.0, 0.0, 0.0, 0.0)
        val sorted = values.sorted()
        fun at(p: Double) = sorted[minOf(sorted.size - 1, (sorted.size * p).toInt())]
        return Stats(at(0.5), at(0.95), at(0.99), sorted.last())
    }

    private fun Stats.line() = "median %.1f, p95 %.1f, p99 %.1f, max %.1f".format(median, p95, p99, max)

    private companion object {
        const val MAX_FIELDS = 5
    }
}
