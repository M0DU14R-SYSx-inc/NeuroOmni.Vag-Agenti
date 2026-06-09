package com.horizons.model

import android.content.Context
import android.util.Log
import com.horizons.BuildConfig
import java.io.File

/**
 * Returns NexaVlmEngine when ALL of these hold:
 *   - built with -PnexaEnabled=true (BuildConfig.NEXA_ENABLED)
 *   - a folder containing nexa.manifest is staged under filesDir/models or external files dir
 * Otherwise returns StubEdgeModel so the build is always runnable.
 */
object EdgeModelFactory {
    private const val TAG = "EdgeModelFactory"
    const val MODELS_DIR = "models"
    const val MANIFEST = "nexa.manifest"

    fun modelsDirs(context: Context): List<File> = buildList {
        context.getExternalFilesDir(MODELS_DIR)?.let(::add)
        add(File(context.filesDir, MODELS_DIR))
    }

    /** Folder containing nexa.manifest (mobile flat layout). Null if not staged. */
    fun installedModelDir(context: Context): File? {
        for (root in modelsDirs(context)) {
            if (!root.isDirectory) continue
            val candidates = sequenceOf(root) +
                (root.listFiles()?.asSequence()?.filter { it.isDirectory } ?: emptySequence())
            candidates.firstOrNull { File(it, MANIFEST).isFile }?.let { return it }
        }
        return null
    }

    fun create(context: Context): EdgeModel {
        val reason = unavailableReason(context)
        if (reason != null) {
            Log.i(TAG, "Using StubEdgeModel: $reason")
            return StubEdgeModel()
        }
        val folder = installedModelDir(context)!!.absolutePath
        return NexaVlmEngine(context.applicationContext, folder)
    }

    private fun unavailableReason(context: Context): String? {
        if (!BuildConfig.NEXA_ENABLED) return "built without nexaEnabled"
        if (installedModelDir(context) == null) {
            return "no $MANIFEST under ${modelsDirs(context).joinToString { it.path }}"
        }
        return null
    }
}
