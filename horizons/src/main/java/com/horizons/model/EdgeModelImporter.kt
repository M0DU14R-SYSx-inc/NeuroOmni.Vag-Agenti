package com.horizons.model

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copy a user-picked folder of model files (e.g. phone Downloads/omni-neural-4b-mobile)
 * into the app's private models dir. Used as an alternative to the in-app downloader
 * when the user already has the 14 mobile shards on the phone.
 *
 * The picked tree is walked at depth 1; only the expected file names are copied.
 * No subdirectories are created — flat layout is enforced (MODEL-FOLDER HYGIENE).
 */
object EdgeModelImporter {

    private val WANTED = setOf(
        "nexa.manifest", "config.json", "files-1-1.nexa",
        "attachments-1-3.nexa", "attachments-2-3.nexa", "attachments-3-3.nexa",
        "weights-1-8.nexa", "weights-2-8.nexa", "weights-3-8.nexa", "weights-4-8.nexa",
        "weights-5-8.nexa", "weights-6-8.nexa", "weights-7-8.nexa", "weights-8-8.nexa"
    )

    data class Result(val copied: List<String>, val missing: List<String>, val destDir: File)

    suspend fun importFromTree(context: Context, treeUri: Uri): kotlin.Result<Result> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                    ?: error("Could not open picked folder")

                val dest = (context.getExternalFilesDir(EdgeModelFactory.MODELS_DIR)
                    ?: File(context.filesDir, EdgeModelFactory.MODELS_DIR))
                    .let { File(it, EdgeModelDownloader.MODEL_DIR_NAME) }
                    .apply { mkdirs() }

                val copied = mutableListOf<String>()
                root.listFiles().forEach { doc ->
                    val name = doc.name ?: return@forEach
                    if (name !in WANTED) return@forEach
                    if (!doc.isFile) return@forEach
                    val outFile = File(dest, name)
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        outFile.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("Could not read $name from picked folder")
                    copied += name
                }
                val missing = (WANTED - copied.toSet()).toList().sorted()
                Result(copied.sorted(), missing, dest)
            }
        }
}
