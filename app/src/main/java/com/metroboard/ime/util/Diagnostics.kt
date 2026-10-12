/*
 * MetroBoard - Open Source Android IME (Windows Phone / Word Flow inspired)
 * Copyright (C) 2026 MetroBoard Contributors
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * See LICENSE and ATTRIBUTION.md for details.
 */

package com.metroboard.ime.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch

/**
 * Response-time diagnostics that work in a release APK. Off by default; the user switches it on in Settings, types
 * for a while and copies the report. It keeps numbers only (see [LatencyLog]), in memory.
 *
 * Settings and the keyboard service live in the same process, so they share this one object.
 */
object Diagnostics {
    val log = LatencyLog()
    private var watchdog: StallWatchdog? = null

    /** Applies the user's choice. Debug builds always record, and also write to logcat (`adb logcat -s MB_LAT MB_PHASES`). */
    fun configure(context: Context, wanted: Boolean) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        LatencyProbe.logToLogcat = debuggable
        LatencyProbe.enabled = wanted || debuggable
        if (wanted) {
            if (watchdog == null) watchdog = StallWatchdog { log.recordStall(it) }
        } else {
            stopWatching()
        }
    }

    /** The keyboard is on screen: watch the main thread for stalls. */
    fun startWatching() = watchdog?.start()
    fun stopWatching() { watchdog?.stop() }

    fun deviceInfo(): LatencyLog.DeviceInfo {
        val runtime = Runtime.getRuntime()
        return LatencyLog.DeviceInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdk = Build.VERSION.SDK_INT,
            cores = runtime.availableProcessors(),
            heapLimitMb = (runtime.maxMemory() / 1048576).toInt(),
            heapUsedMb = ((runtime.totalMemory() - runtime.freeMemory()) / 1048576).toInt(),
        )
    }
}

/**
 * Finds out how long the main thread stays blocked: from a helper thread it posts a message to the main thread every
 * 100 ms and times how long it takes to be handled. A delay over 30 ms is reported. Runs only while the keyboard is
 * on screen.
 */
class StallWatchdog(private val onStall: (Long) -> Unit) {
    @Volatile private var running = false
    private val main = Handler(Looper.getMainLooper())

    @Synchronized fun start() {
        if (running) return
        running = true
        Thread({
            while (running) {
                val sent = SystemClock.uptimeMillis()
                val handled = CountDownLatch(1)
                main.post { handled.countDown() }
                handled.await()
                val delay = SystemClock.uptimeMillis() - sent
                if (delay > MIN_STALL_MS) onStall(delay)
                try { Thread.sleep(INTERVAL_MS) } catch (_: InterruptedException) { return@Thread }
            }
        }, "metroboard-watchdog").apply { isDaemon = true }.start()
    }

    @Synchronized fun stop() {
        running = false
    }

    private companion object {
        const val INTERVAL_MS = 100L
        const val MIN_STALL_MS = 30L
    }
}
