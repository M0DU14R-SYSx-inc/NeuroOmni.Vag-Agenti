package com.horizons.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash recorder. Installs as the default UncaughtExceptionHandler
 * and writes a single readable file at <filesDir>/crash.log containing the
 * most recent stack traces (newest on top, 5 kept).
 *
 * Why: when a native crash from sherpa/Nexa takes the app down, the operator
 * has no `adb` access and the Java logcat ring is gone by the time they
 * relaunch. Diagnostics surfaces this file so the next crash leaves a trail
 * the operator can screenshot.
 *
 * It chains to the previous handler at the end so Android's normal process
 * teardown (and any platform crash reporter) still runs.
 */
class CrashRecorder private constructor(private val context: Context) :
    Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching { writeCrash(t, e) }
        runCatching { previous?.uncaughtException(t, e) }
    }

    private fun writeCrash(t: Thread, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("===== $stamp  thread=${t.name} =====\n")
            append("${e.javaClass.name}: ${e.message}\n")
            append(sw.toString())
            append("\n")
        }
        val file = crashFile(context)
        // Prepend, keep last MAX entries. Small file, cheap.
        val prior = if (file.isFile) file.readText() else ""
        val combined = entry + prior
        val capped = if (combined.length > MAX_BYTES) combined.substring(0, MAX_BYTES) else combined
        file.writeText(capped)
        Log.e(TAG, "Recorded crash to ${file.absolutePath}", e)
    }

    companion object {
        private const val TAG = "CrashRecorder"
        private const val MAX_BYTES = 64 * 1024

        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(context.applicationContext))
        }

        fun crashFile(context: Context): File = File(context.filesDir, "crash.log")

        fun read(context: Context): String =
            crashFile(context).takeIf { it.isFile }?.readText().orEmpty()

        fun clear(context: Context) { crashFile(context).delete() }
    }
}
