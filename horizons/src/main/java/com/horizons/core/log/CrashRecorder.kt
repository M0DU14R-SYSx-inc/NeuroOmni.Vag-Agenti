package com.horizons.core.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Installs a crash file marker so native + JVM crashes leave a readable trace
 * in filesDir/crashes/ even when the process is killed before logs flush.
 */
class CrashRecorder(private val context: Context) {

    private val crashDir: File
        get() = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install() {
        val upstream = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashFile(thread, throwable) }
            upstream?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashFile(thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        File(crashDir, "crash_$ts.txt").bufferedWriter().use { w ->
            w.write("ts=$ts thread=${thread.name}\n")
            w.write(throwable.stackTraceToString())
        }
        pruneOldCrashes(keep = 20)
    }

    private fun pruneOldCrashes(keep: Int) {
        val files = crashDir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: return
        if (files.size <= keep) return
        files.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
    }

    fun listCrashes(): List<File> =
        (crashDir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
}
