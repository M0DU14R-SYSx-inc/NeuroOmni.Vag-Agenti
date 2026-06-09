package com.horizons.model

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
 * One-tap downloader for the OmniNeural-4B-mobile flat-shard set from Hugging Face.
 *
 * MOBILE variant (`NexaAI/OmniNeural-4B-mobile`) only. The desktop variant
 * (`NexaAI/OmniNeural-4B`) has audio/llm/vit/vlm subfolders and crashes the SDK
 * with -863151000 on the phone. Files land flat under
 * filesDir/models/omni-neural-4b-mobile/, no subdirs — matches the
 * MODEL-FOLDER HYGIENE rule in the v2 kickoff.
 *
 * Repo is CC-BY-4.0 public; no Hugging Face token required.
 *
 * If the file list ever drifts (HF tree updated), only [FILES] below changes.
 */
object EdgeModelDownloader {
    private const val REPO = "NexaAI/OmniNeural-4B-mobile"
    const val MODEL_DIR_NAME = "omni-neural-4b-mobile"

    /** Verified-from-HF flat file list. UPDATE HERE if Nexa publishes a different layout. */
    private val FILES = listOf(
        "nexa.manifest",
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
        "weights-8-8.nexa"
    )

    data class Progress(val fileIndex: Int, val fileCount: Int, val currentFile: String, val fraction: Float?)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun url(file: String) = "https://huggingface.co/$REPO/resolve/main/$file?download=true"

    suspend fun download(
        context: Context,
        hfToken: String? = null,
        onProgress: (Progress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = (context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
                ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR)).apply { mkdirs() }
            val dest = File(root, MODEL_DIR_NAME).apply { mkdirs() }

            FILES.forEachIndexed { index, name ->
                coroutineContext.ensureActive()
                onProgress(Progress(index + 1, FILES.size, name, null))
                val target = File(dest, name)
                val tmp = File(dest, "$name.part")

                val expected = contentLength(name, hfToken)
                if (target.exists() && expected > 0 && target.length() == expected) {
                    onProgress(Progress(index + 1, FILES.size, name, 1f))
                    return@forEachIndexed
                }

                val reqBuilder = Request.Builder().url(url(name))
                if (!hfToken.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $hfToken")
                client.newCall(reqBuilder.build()).execute().use { resp ->
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

    private fun contentLength(name: String, hfToken: String?): Long = runCatching {
        val b = Request.Builder().url(url(name)).head()
        if (!hfToken.isNullOrBlank()) b.header("Authorization", "Bearer $hfToken")
        client.newCall(b.build()).execute().use { resp ->
            resp.header("Content-Length")?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}
