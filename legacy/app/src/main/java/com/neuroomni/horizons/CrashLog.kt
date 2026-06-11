package com.neuroomni.horizons

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last fatal JVM exception to a file so the next launch can show it.
 *
 * The Nexa edge path can fail in ways that escape a local try/catch (e.g. an
 * exception thrown on a background thread), which Android surfaces only as the
 * useless "Horizons keeps stopping" dialog. Writing the stack trace here — and
 * displaying it on relaunch — turns that into something we can actually diagnose
 * on-device, with no adb/logcat required.
 *
 * Caveat: this catches JVM throwables only. A *native* crash inside the Nexa NPU
 * runtime aborts the process below the JVM and will not be recorded here; that
 * still needs a logcat tombstone.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    private fun file(context: Context): File =
        File(context.filesDir, FILE_NAME)

    /** Append-free single-shot write of [throwable] with a timestamp + device info. */
    fun write(context: Context, throwable: Throwable) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val body = buildString {
                appendLine("Horizons crash @ $stamp")
                appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                appendLine()
                append(sw.toString())
            }
            file(context).writeText(body)
        }
    }

    /** The saved crash report, or null if there isn't one. */
    fun read(context: Context): String? =
        file(context).takeIf { it.isFile }?.runCatching { readText() }?.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
