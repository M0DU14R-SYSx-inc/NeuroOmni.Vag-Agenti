package com.neuroomni.horizons.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Imports a model the user picked from on-device storage (Downloads, etc.) into the
 * app's external models dir, where [EdgeModelFactory] looks for it. This is the
 * phone-only alternative to `adb push`: download the ~1.57 GB `weights-8-8.nexa` on
 * the Razr, tap to import, no computer required.
 *
 * The Nexa runtime needs a real filesystem path (not a content Uri), so the file is
 * copied once. Progress is reported so a multi-GB copy isn't a silent freeze.
 */
object EdgeModelInstaller {

    data class Progress(val bytesCopied: Long, val totalBytes: Long) {
        /** 0f..1f, or null when the source size is unknown. */
        val fraction: Float? get() = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes) else null
    }

    /**
     * Copy [source] into the external models dir under [displayName]. Calls [onProgress]
     * as bytes stream in. Returns the installed [File]. Honors coroutine cancellation
     * and cleans up the partial file if interrupted.
     */
    suspend fun install(
        context: Context,
        source: Uri,
        displayName: String,
        onProgress: (Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val total = querySize(context, source)
            val dir = (context.getExternalFilesDir(EdgeModelFactory.OMNI_NEURAL_DIR)
                ?: File(context.filesDir, EdgeModelFactory.OMNI_NEURAL_DIR))
                .apply { mkdirs() }
            val target = File(dir, sanitize(displayName))
            val tmp = File(dir, target.name + ".part")

            try {
                resolver.openInputStream(source)?.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastReported = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (copied - lastReported >= PROGRESS_STEP) {
                                onProgress(Progress(copied, total))
                                lastReported = copied
                            }
                        }
                        output.flush()
                        onProgress(Progress(copied, total))
                    }
                } ?: error("Could not open the selected file")
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "Could not finalize ${target.name}" }
                target
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            }
        }
    }

    private fun querySize(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getLong(idx) else -1L
                } ?: -1L
        }.getOrDefault(-1L)

    fun queryName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
        }.getOrNull()

    /** Keep the name a plain filename; default a sane name if the picker gave us none. */
    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return base.ifBlank { "weights.${EdgeModelFactory.MODEL_EXTENSION}" }
    }

    private const val PROGRESS_STEP = 4L * 1024 * 1024 // report every ~4 MB
}
