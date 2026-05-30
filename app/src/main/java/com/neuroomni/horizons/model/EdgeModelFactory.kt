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
 *   - a NEXA_TOKEN is present (BuildConfig.NEXA_TOKEN),
 *   - the OmniNeural model file exists under the app's files dir.
 * Otherwise it falls back to [StubEdgeModel] so CI and tokenless devices stay green.
 *
 * The Nexa class is instantiated reflectively so the default (stub-only) build has
 * no compile-time reference to the SDK — that source set isn't even compiled in CI.
 */
object EdgeModelFactory {

    private const val TAG = "EdgeModelFactory"

    /** Sub-directory (under both external- and internal-files dirs) holding the model. */
    const val OMNI_NEURAL_DIR = "models"

    /**
     * The model is matched by extension, not an exact name, so the on-disk filename
     * (e.g. `weights-8-8.nexa`, a W8A8 quant of OmniNeural-4B) doesn't have to be
     * known ahead of time. The first `*.nexa` file found wins.
     */
    const val MODEL_EXTENSION = "nexa"

    /**
     * Where the importer drops the model and where this factory looks first:
     * the app-specific external files dir, which is `adb push`-able AND writable by
     * the in-app picker with no runtime permission. Falls back to internal files dir.
     */
    fun modelsDirs(context: Context): List<File> = buildList {
        context.getExternalFilesDir(OMNI_NEURAL_DIR)?.let(::add)
        add(File(context.filesDir, OMNI_NEURAL_DIR))
    }

    /** The installed model file (first `*.nexa` in any models dir), or null if none. */
    fun installedModel(context: Context): File? = modelsDirs(context)
        .asSequence()
        .filter { it.isDirectory }
        .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
        .firstOrNull { it.isFile && it.extension.equals(MODEL_EXTENSION, ignoreCase = true) }

    /**
     * @param nexaToken the universal Nexa key. Defaults to the in-app encrypted store
     *   value (Architecture §12), falling back to BuildConfig for locally-baked builds.
     */
    fun create(context: Context, nexaToken: String = BuildConfig.NEXA_TOKEN): EdgeModel {
        val reason = unavailableReason(context, nexaToken)
        if (reason != null) {
            Log.i(TAG, "Using StubEdgeModel: $reason")
            return StubEdgeModel()
        }
        return try {
            val modelPath = installedModel(context)!!.absolutePath
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
        if (installedModel(context) == null) {
            return "no *.${MODEL_EXTENSION} model in ${modelsDirs(context).joinToString { it.path }}"
        }
        return null
    }
}
