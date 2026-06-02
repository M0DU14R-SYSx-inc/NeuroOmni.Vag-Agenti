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
 * private models dir. Lenient filename matching: strips Chrome-style "(1)" copies
 * and trailing ".bin"/".download" suffixes so re-downloaded files still match.
 */
object EdgeModelImporter {

    val WANTED: Set<String> = setOf(
        "nexa.manifest", "config.json", "files-1-1.nexa",
        "attachments-1-3.nexa", "attachments-2-3.nexa", "attachments-3-3.nexa",
        "weights-1-8.nexa", "weights-2-8.nexa", "weights-3-8.nexa", "weights-4-8.nexa",
        "weights-5-8.nexa", "weights-6-8.nexa", "weights-7-8.nexa", "weights-8-8.nexa"
    )

    /**
     * Strip Chrome's duplicate-download suffixes: " (1)", " (2)", trailing
     * ".bin"/".download"/".crdownload". Return the canonical wanted name if
     * the input matches, else null.
     */
    fun canonicalName(rawName: String?): String? {
        if (rawName == null) return null
        var n = rawName
        // Strip ".bin"/".download"/".crdownload" tail (Chrome partial markers).
        n = n.removeSuffix(".crdownload").removeSuffix(".download").removeSuffix(".bin")
        // Strip " (1)", " (2)", "(1)", "(2)" before extension: "weights-3-8 (1).nexa" -> "weights-3-8.nexa"
        val parenSpace = Regex("""\s*\((\d+)\)(?=\.[^.]+$|$)""")
        n = parenSpace.replace(n, "")
        return if (n in WANTED) n else null
    }

    data class Progress(
        val currentFile: String,
        val fileIndex: Int,
        val fileCount: Int,
        val bytesCopied: Long,
        val totalBytes: Long
    ) { val fraction: Float? get() = if (totalBytes > 0) bytesCopied.toFloat() / totalBytes else null }

    data class Result(val copied: List<String>, val missing: List<String>, val destDir: File, val candidates: Int)

    suspend fun importFromTree(
        context: Context, treeUri: Uri, onProgress: (Progress) -> Unit = {}
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Could not open picked folder")
            val all = root.listFiles().filter { it.isFile }
            val matches = all.mapNotNull { doc ->
                val canon = canonicalName(doc.name) ?: return@mapNotNull null
                canon to doc
            }
            copyAll(context, matches, onProgress, candidatesSeen = all.size)
        }
    }

    suspend fun importFiles(
        context: Context, uris: List<Uri>, onProgress: (Progress) -> Unit = {}
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val docs = uris.mapNotNull { DocumentFile.fromSingleUri(context, it) }.filter { it.isFile }
            val matches = docs.mapNotNull { doc ->
                val canon = canonicalName(doc.name) ?: return@mapNotNull null
                canon to doc
            }
            copyAll(context, matches, onProgress, candidatesSeen = uris.size)
        }
    }

    private suspend fun copyAll(
        context: Context,
        sources: List<Pair<String, DocumentFile>>,
        onProgress: (Progress) -> Unit,
        candidatesSeen: Int
    ): Result {
        val dest = destDir(context).apply { mkdirs() }
        val totalBytes = sources.sumOf { it.second.length() }
        var globalCopied = 0L
        val copied = mutableListOf<String>()

        sources.forEachIndexed { idx, (canon, doc) ->
            coroutineContext.ensureActive()
            val out = File(dest, canon)
            val tmp = File(dest, "$canon.part")
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        globalCopied += read
                        if (globalCopied - lastReport >= 4L * 1024 * 1024) {
                            onProgress(Progress(canon, idx + 1, sources.size, globalCopied, totalBytes))
                            lastReport = globalCopied
                        }
                    }
                    output.flush()
                }
            } ?: error("Could not read ${doc.name}")
            if (out.exists()) out.delete()
            check(tmp.renameTo(out)) { "Could not finalize $canon" }
            copied += canon
            onProgress(Progress(canon, idx + 1, sources.size, globalCopied, totalBytes))
        }

        val missing = (WANTED - copied.toSet()).toList().sorted()
        return Result(copied.sorted(), missing, dest, candidatesSeen)
    }

    private fun destDir(context: Context): File {
        val root = context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
            ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR)
        return File(root, EdgeModelDownloader.MODEL_DIR_NAME)
    }

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
