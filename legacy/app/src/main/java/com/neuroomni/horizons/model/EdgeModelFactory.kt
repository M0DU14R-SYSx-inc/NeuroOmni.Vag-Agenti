package com.neuroomni.horizons.model

import android.content.Context
import android.util.Log
import com.neuroomni.horizons.BuildConfig
import java.io.File

/**
 * Chooses which [EdgeModel] backs the Chat panel.
 *
 * Returns [NexaOmniNeuralEdgeModel] only when ALL of these hold:
 *   - the app was built with `-PnexaEnabled=true` (BuildConfig.NEXA_ENABLED),
 *   - a Nexa key is present (passed in / BuildConfig),
 *   - the OmniNeural model folder (containing nexa.manifest) exists under files dir.
 * Otherwise it falls back to [StubEdgeModel] so CI and tokenless devices stay green.
 *
 * The Nexa class is instantiated reflectively so the default (stub-only) build has
 * no compile-time reference to the SDK — that source set isn't even compiled in CI.
 */
object EdgeModelFactory {

    private const val TAG = "EdgeModelFactory"

    /** Sub-directory (under both external- and internal-files dirs) holding the model. */
    const val OMNI_NEURAL_DIR = "models"

    const val MODEL_EXTENSION = "nexa"

    /**
     * OmniNeural-4B ships as a multi-file set: 8 weight shards
     * (`weights-1-8.nexa` … `weights-8-8.nexa`) plus `attachments-*.nexa`,
     * `files-1-1.nexa`, and a tiny `nexa.manifest` descriptor that ties them together
     * (verified from the HF repo NexaAI/OmniNeural-4B-mobile). The runtime is given the
     * *folder* containing the manifest; it resolves the shards itself.
     */
    const val PRIMARY_MODEL_FILE = "nexa.manifest"

    /**
     * Where the importer drops the model and where this factory looks first:
     * the app-specific external files dir, which is `adb push`-able AND writable by
     * the in-app picker with no runtime permission. Falls back to internal files dir.
     */
    fun modelsDirs(context: Context): List<File> = buildList {
        context.getExternalFilesDir(OMNI_NEURAL_DIR)?.let(::add)
        add(File(context.filesDir, OMNI_NEURAL_DIR))
    }

    /**
     * The installed model directory — the folder containing [PRIMARY_MODEL_FILE]
     * (the `nexa.manifest`). Checks each models dir and its immediate subdirs, so both
     * `models/<modelfolder>/…` and `models/…` layouts resolve. Null if not present.
     */
    fun installedModelDir(context: Context): File? {
        for (root in modelsDirs(context)) {
            if (!root.isDirectory) continue
            // The folder holding nexa.manifest: root itself, or an immediate subdir.
            val candidates = sequenceOf(root) +
                (root.listFiles()?.asSequence()?.filter { it.isDirectory } ?: emptySequence())
            candidates.firstOrNull { File(it, PRIMARY_MODEL_FILE).isFile }?.let { return it }
        }
        return null
    }

    /** Any installed `.nexa` (used for "is something staged?" UI), or null. */
    fun installedModel(context: Context): File? = modelsDirs(context)
        .asSequence()
        .filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().maxDepth(2) }
        .firstOrNull { it.isFile && it.extension.equals(MODEL_EXTENSION, ignoreCase = true) }

    fun create(context: Context, nexaToken: String = BuildConfig.NEXA_TOKEN): EdgeModel {
        val reason = unavailableReason(context, nexaToken)
        if (reason != null) {
            Log.i(TAG, "Using StubEdgeModel: $reason")
            return StubEdgeModel()
        }
        return try {
            // Pass the folder containing nexa.manifest; the SDK resolves the shards.
            // (If the runtime turns out to want the manifest file path itself, that's a
            //  one-line change: File(installedModelDir(context)!!, PRIMARY_MODEL_FILE).)
            val modelPath = installedModelDir(context)!!.absolutePath
            val clazz = Class.forName("com.neuroomni.horizons.model.NexaOmniNeuralEdgeModel")
            clazz.getConstructor(Context::class.java, String::class.java, String::class.java)
                .newInstance(context.applicationContext, nexaToken, modelPath) as EdgeModel
        } catch (t: Throwable) {
            Log.w(TAG, "Nexa edge model unavailable, falling back to stub", t)
            StubEdgeModel()
        }
    }

    /** Null when the Nexa model can run; otherwise a human-readable reason for the fallback. */
    private fun unavailableReason(context: Context, nexaToken: String): String? {
        if (!BuildConfig.NEXA_ENABLED) return "built without nexaEnabled"
        if (nexaToken.isBlank()) return "Nexa key not set"
        if (installedModelDir(context) == null) {
            return "no $PRIMARY_MODEL_FILE (model folder) under ${modelsDirs(context).joinToString { it.path }}"
        }
        return null
    }
}
