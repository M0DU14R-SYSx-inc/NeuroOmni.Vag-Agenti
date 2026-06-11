package com.horizons.watchdog.crash

import android.content.Context

class CrashLog(private val context: Context) {
    fun install() {
        // Phase 9: Thread.setDefaultUncaughtExceptionHandler chain.
        // Capture JVM exceptions to crash_<ts>.txt; for Horizons native NPU crashes (which abort below the JVM),
        // grep logcat tombstone references via a periodic logcat -d -b crash poll.
    }

    fun recent(limit: Int = 50): List<String> {
        TODO("Phase 9: list of crash file paths/summaries.")
    }
}
