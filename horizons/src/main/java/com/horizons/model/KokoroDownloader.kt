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
 * Downloads onnx-community/Kokoro-82M-v1.0-ONNX (q8f16 model + all 55 voices).
 * ~108 MB total: model ~80 MB + 55 voices × ~510 KB = ~28 MB. Lands flat in
 * filesDir/models/kokoro/. Voice picker in Router selects which voice
 * KokoroTtsEngine uses; switching is instant (no re-download).
 */
object KokoroDownloader {
    private const val REPO = "onnx-community/Kokoro-82M-v1.0-ONNX"
    const val MODEL_DIR_NAME = "kokoro"
    const val DEFAULT_VOICE = "am_adam"

    /** All 55 voices that ship with Kokoro v1.0. Naming convention:
     *  <lang><gender>_<name>. lang: a=American English, b=British English,
     *  e=Spanish, f=French, h=Hindi, i=Italian, j=Japanese, p=Portuguese,
     *  z=Mandarin. gender: f=female, m=male. (The bare "af" is the
     *  Kokoro authors' default-american-female blend.) */
    val ALL_VOICES = listOf(
        // American English (11 female + 9 male)
        "af", "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica",
        "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam",
        "am_michael", "am_onyx", "am_puck", "am_santa",
        // British English (4 female + 4 male)
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
        "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
        // Spanish / French / Hindi / Italian / Portuguese
        "ef_dora", "em_alex", "em_santa",
        "ff_siwis",
        "hf_alpha", "hf_beta", "hm_omega", "hm_psi",
        "if_sara", "im_nicola",
        "pf_dora", "pm_alex", "pm_santa",
        // Japanese
        "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo",
        // Mandarin
        "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi",
        "zm_yunjian", "zm_yunxi", "zm_yunxia", "zm_yunyang",
    )

    private val FILES = listOf(
        "config.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "onnx/model_q8f16.onnx",
    ) + ALL_VOICES.map { "voices/$it.bin" }

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
        return if (File(dir, "onnx/model_q8f16.onnx").isFile) dir else null
    }
}
