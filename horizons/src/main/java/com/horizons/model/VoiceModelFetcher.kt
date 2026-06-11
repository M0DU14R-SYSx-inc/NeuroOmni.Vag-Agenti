package com.horizons.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads + extracts the .tar.bz2 model archives that k2-fsa publishes
 * as sherpa-onnx release assets. Shared by MoonshineDownloader (ASR) and
 * KokoroDownloader (TTS).
 *
 * Layout convention: every k2-fsa archive has a single top-level folder
 * named after the model (e.g. kokoro-multi-lang-v1_0/...). That prefix is
 * stripped so files land directly in the destination dir.
 *
 * Two-phase progress: download is reported with byte-level fractions
 * (archive sizes are 250–350 MB — the user needs a moving bar), then
 * extraction reports per-entry. The .tar.bz2 is kept on disk until
 * extraction completes so an interrupted extract can resume without
 * re-downloading, then deleted to reclaim space.
 */
object VoiceModelFetcher {

    data class Progress(val phase: String, val detail: String, val fraction: Float?)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS).build()
    }

    fun modelsRoot(context: Context): File =
        (context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
            ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR)).apply { mkdirs() }

    suspend fun fetchAndExtract(
        context: Context,
        url: String,
        destDirName: String,
        onProgress: (Progress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val root = modelsRoot(context)
            val dest = File(root, destDirName).apply { mkdirs() }
            val archive = File(root, "$destDirName.tar.bz2")

            if (!archive.isFile || archive.length() == 0L) {
                download(url, archive, onProgress)
            }
            extract(archive, dest, onProgress)
            archive.delete()
            dest
        }
    }

    private suspend fun download(url: String, target: File, onProgress: (Progress) -> Unit) {
        val tmp = File(target.parentFile, "${target.name}.part")
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $url")
            val body = resp.body ?: error("Empty body for $url")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var copied = 0L; var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val r = input.read(buf); if (r < 0) break
                        out.write(buf, 0, r); copied += r
                        if (copied - lastReport >= 4L * 1024 * 1024) {
                            val f = if (total > 0) copied.toFloat() / total else null
                            onProgress(Progress("download", "${copied / (1 shl 20)} MB", f))
                            lastReport = copied
                        }
                    }
                    out.flush()
                }
            }
        }
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Could not finalize $target" }
    }

    private suspend fun extract(archive: File, dest: File, onProgress: (Progress) -> Unit) {
        onProgress(Progress("extract", "opening archive", null))
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16))
        ).use { tar ->
            var count = 0
            while (true) {
                coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                // Strip the single top-level folder: "kokoro-x/voices.bin" -> "voices.bin"
                val rel = entry.name.substringAfter('/', "")
                if (rel.isEmpty()) continue
                val out = File(dest, rel)
                // Zip-slip guard — entry paths must stay inside dest.
                check(out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                    "Archive entry escapes destination: ${entry.name}"
                }
                if (entry.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                count++
                if (count % 20 == 0) onProgress(Progress("extract", "$count files", null))
            }
            onProgress(Progress("extract", "$count files done", 1f))
        }
    }
}
