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
 * Downloads onnx-community/moonshine-base-ONNX (int8 variant, ~67 MB total).
 * Files land flat in filesDir/models/moonshine-base/.
 */
object MoonshineDownloader {
    private const val REPO = "onnx-community/moonshine-base-ONNX"
    const val MODEL_DIR_NAME = "moonshine-base"

    private val FILES = listOf(
        "config.json",
        "generation_config.json",
        "preprocessor_config.json",
        "special_tokens_map.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "onnx/encoder_model_int8.onnx",
        "onnx/decoder_model_merged_int8.onnx"
    )

    data class Progress(val fileIndex: Int, val fileCount: Int, val currentFile: String, val fraction: Float?)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).build()
    }

    private fun url(file: String) = "https://huggingface.co/$REPO/resolve/main/$file?download=true"

    suspend fun download(
        context: Context, hfToken: String? = null, onProgress: (Progress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = (context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
                ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR)).apply { mkdirs() }
            val dest = File(root, MODEL_DIR_NAME).apply { mkdirs() }

            FILES.forEachIndexed { idx, name ->
                coroutineContext.ensureActive()
                onProgress(Progress(idx + 1, FILES.size, name, null))
                val target = File(dest, name).apply { parentFile?.mkdirs() }
                val tmp = File(dest, "$name.part").apply { parentFile?.mkdirs() }
                if (target.exists() && target.length() > 0) {
                    onProgress(Progress(idx + 1, FILES.size, name, 1f)); return@forEachIndexed
                }
                val req = Request.Builder().url(url(name))
                if (!hfToken.isNullOrBlank()) req.header("Authorization", "Bearer $hfToken")
                client.newCall(req.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $name")
                    val body = resp.body ?: error("Empty body for $name")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(1 shl 16)
                            var copied = 0L; var lastReport = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val r = input.read(buf); if (r < 0) break
                                out.write(buf, 0, r); copied += r
                                if (copied - lastReport >= 1L * 1024 * 1024) {
                                    val f = if (total > 0) copied.toFloat() / total else null
                                    onProgress(Progress(idx + 1, FILES.size, name, f)); lastReport = copied
                                }
                            }; out.flush()
                        }
                    }
                }
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "Could not finalize $name" }
                onProgress(Progress(idx + 1, FILES.size, name, 1f))
            }
            dest
        }
    }

    fun installedDir(context: Context): File? {
        val root = (context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
            ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR))
        val dir = File(root, MODEL_DIR_NAME)
        return if (File(dir, "onnx/encoder_model_int8.onnx").isFile) dir else null
    }
}
