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

    /** Expected on-device location of the OmniNeural model (Architecture §4 / §7). */
    const val OMNI_NEURAL_DIR = "models"
    const val OMNI_NEURAL_FILE = "omni-neural.nexa"

    fun create(context: Context): EdgeModel {
        val reason = unavailableReason(context)
        if (reason != null) {
            Log.i(TAG, "Using StubEdgeModel: $reason")
            return StubEdgeModel()
        }
        return try {
            val modelPath = File(File(context.filesDir, OMNI_NEURAL_DIR), OMNI_NEURAL_FILE).absolutePath
            val clazz = Class.forName("com.neuroomni.horizons.model.NexaOmniNeuralEdgeModel")
            clazz.getConstructor(Context::class.java, String::class.java, String::class.java)
                .newInstance(context.applicationContext, BuildConfig.NEXA_TOKEN, modelPath) as EdgeModel
        } catch (t: Throwable) {
            Log.w(TAG, "Nexa edge model unavailable, falling back to stub", t)
            StubEdgeModel()
        }
    }

    /** Null when the Nexa model can run; otherwise a human-readable reason for the fallback. */
    private fun unavailableReason(context: Context): String? {
        if (!BuildConfig.NEXA_ENABLED) return "built without nexaEnabled"
        if (BuildConfig.NEXA_TOKEN.isBlank()) return "NEXA_TOKEN absent"
        val modelFile = File(File(context.filesDir, OMNI_NEURAL_DIR), OMNI_NEURAL_FILE)
        if (!modelFile.exists()) return "model file missing at ${modelFile.path}"
        return null
    }
}
