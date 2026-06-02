package com.horizons.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Copy OmniNeural-4B-mobile files (~6 GB) from on-device storage into the app's
 * private models dir. Two entry points:
 *   - importFromTree: pick a folder (e.g. Downloads), copy any of the 14 expected
 *     files found at depth 1.
 *   - importFiles: pick N individual files (multi-select), copy each one with the
 *     same matching rule.
 * Either way, only the named files land — no junk, no subdirs, flat layout per
 * MODEL-FOLDER HYGIENE.
 */
object EdgeModelImporter {

    val WANTED: Set<String> = setOf(
        "nexa.manifest", "config.json", "files-1-1.nexa",
        "attachments-1-3.nexa", "attachments-2-3.nexa", "attachments-3-3.nexa",
        "weights-1-8.nexa", "weights-2-8.nexa", "weights-3-8.nexa", "weights-4-8.nexa",
        "weights-5-8.nexa", "weights-6-8.nexa", "weights-7-8.nexa", "weights-8-8.nexa"
    )

    data class Progress(
        val currentFile: String,
        val fileIndex: Int,
        val fileCount: Int,
        val bytesCopied: Long,
        val totalBytes: Long
    ) {
        val fraction: Float? get() = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else null
    }

    data class Result(val copied: List<String>, val missing: List<String>, val destDir: File)

    suspend fun importFromTree(
        context: Context,
        treeUri: Uri,
        onProgress: (Progress) -> Unit = {}
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Could not open picked folder")
            val matches = root.listFiles().filter { it.isFile && (it.name ?: "") in WANTED }
            copyAll(context, matches, onProgress)
        }
    }

    suspend fun importFiles(
        context: Context,
        uris: List<Uri>,
        onProgress: (Progress) -> Unit = {}
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val docs = uris.mapNotNull { DocumentFile.fromSingleUri(context, it) }
                .filter { it.isFile && (it.name ?: "") in WANTED }
            copyAll(context, docs, onProgress)
        }
    }

    private suspend fun copyAll(
        context: Context,
        sources: List<DocumentFile>,
        onProgress: (Progress) -> Unit
    ): Result {
        val dest = destDir(context).apply { mkdirs() }
        val totalBytes = sources.sumOf { it.length() }
        var globalCopied = 0L
        val copied = mutableListOf<String>()

        sources.forEachIndexed { idx, doc ->
            coroutineContext.ensureActive()
            val name = doc.name ?: return@forEachIndexed
            val out = File(dest, name)
            val tmp = File(dest, "$name.part")
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var fileCopied = 0L
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        fileCopied += read
                        globalCopied += read
                        if (globalCopied - lastReport >= 4L * 1024 * 1024) {
                            onProgress(Progress(name, idx + 1, sources.size, globalCopied, totalBytes))
                            lastReport = globalCopied
                        }
                    }
                    output.flush()
                }
            } ?: error("Could not read $name")
            if (out.exists()) out.delete()
            check(tmp.renameTo(out)) { "Could not finalize $name" }
            copied += name
            onProgress(Progress(name, idx + 1, sources.size, globalCopied, totalBytes))
        }

        val missing = (WANTED - copied.toSet()).toList().sorted()
        return Result(copied.sorted(), missing, dest)
    }

    private fun destDir(context: Context): File {
        val root = context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
            ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR)
        return File(root, EdgeModelDownloader.MODEL_DIR_NAME)
    }

    /** SAF picker can be pre-pointed here so the user lands on Downloads with one tap. */
    val DOWNLOADS_TREE_URI: Uri =
        Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")

    @Suppress("unused")
    fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                }
        }.getOrNull()
}
