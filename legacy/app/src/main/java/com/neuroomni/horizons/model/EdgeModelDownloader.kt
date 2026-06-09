package com.neuroomni.horizons.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads the OmniNeural-4B *mobile* model straight from Hugging Face into the
 * app's own models dir — no Files app, no folder picker, no SAF tree, no nested
 * subfolders to get the layout wrong. This is the one-tap alternative to the
 * import flow.
 *
 * IMPORTANT: the phone NPU build is `NexaAI/OmniNeural-4B-mobile` (flat file
 * layout), NOT `NexaAI/OmniNeural-4B` (which has audio/llm/vit/vlm subfolders and
 * is the Snapdragon-PC layout — loading it on the phone fails at Model create()).
 *
 * The repo is public (CC-BY-4.0, not gated), so no token is needed to download.
 * Files land flat in models/omni-neural-4b-mobile/, exactly the folder the Nexa
 * runtime is handed.
 */
object EdgeModelDownloader {

    /** HF repo whose flat file set runs on the Hexagon NPU. */
    private const val REPO = "NexaAI/OmniNeural-4B-mobile"

    /** Folder name the files are written under (inside the models dir). */
    const val MODEL_DIR_NAME = "omni-neural-4b-mobile"

    /**
     * Exact flat file list for the mobile repo (verified from the HF tree). Kept
     * explicit rather than scraped so a download is deterministic and resumable.
     */
    private val FILES = listOf(
        "nexa.manifest",
        "config.json",
        "files-1-1.nexa",
        "attachments-1-3.nexa",
        "attachments-2-3.nexa",
        "attachments-3-3.nexa",
        "weights-1-8.nexa",
        "weights-2-8.nexa",
        "weights-3-8.nexa",
        "weights-4-8.nexa",
        "weights-5-8.nexa",
        "weights-6-8.nexa",
        "weights-7-8.nexa",
        "weights-8-8.nexa",
    )

    data class Progress(val fileIndex: Int, val fileCount: Int, val currentFile: String, val fraction: Float?)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun url(file: String) = "https://huggingface.co/$REPO/resolve/main/$file?download=true"

    /**
     * Download every model file into models/[MODEL_DIR_NAME]. Skips files already
     * present at the expected size (so an interrupted download resumes per-file on
     * the next tap). Returns the installed folder.
     */
    suspend fun download(
        context: Context,
        onProgress: (Progress) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val modelsDir = (context.getExternalFilesDir(EdgeModelFactory.OMNI_NEURAL_DIR)
                ?: File(context.filesDir, EdgeModelFactory.OMNI_NEURAL_DIR)).apply { mkdirs() }
            val dest = File(modelsDir, MODEL_DIR_NAME).apply { mkdirs() }

            FILES.forEachIndexed { index, name ->
                coroutineContext.ensureActive()
                onProgress(Progress(index + 1, FILES.size, name, null))
                val target = File(dest, name)
                val tmp = File(dest, "$name.part")

                // Probe the expected size; skip the file if it's already complete.
                val expected = contentLength(name)
                if (target.exists() && expected > 0 && target.length() == expected) {
                    onProgress(Progress(index + 1, FILES.size, name, 1f))
                    return@forEachIndexed
                }

                val request = Request.Builder().url(url(name)).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $name")
                    val body = resp.body ?: error("Empty body for $name")
                    val total = body.contentLength().takeIf { it > 0 } ?: expected
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 16)
                            var copied = 0L
                            var lastReport = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (copied - lastReport >= 2L * 1024 * 1024) {
                                    val frac = if (total > 0) copied.toFloat() / total else null
                                    onProgress(Progress(index + 1, FILES.size, name, frac))
                                    lastReport = copied
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "Could not finalize $name" }
                onProgress(Progress(index + 1, FILES.size, name, 1f))
            }
            dest
        }
    }

    /** HEAD request for a file's size (for resume/skip + progress). 0 if unknown. */
    private fun contentLength(name: String): Long = runCatching {
        val request = Request.Builder().url(url(name)).head().build()
        client.newCall(request).execute().use { resp ->
            resp.header("Content-Length")?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}
