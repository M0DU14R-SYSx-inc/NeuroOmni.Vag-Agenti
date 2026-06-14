package com.horizons.core.nexa

import android.content.Context

/**
 * Single entry point for loading any Nexa-runnable model. No type labels —
 * callers pass a [NexaModelSpec] and receive a [NexaEngine].
 *
 * SDK init (NexaSdk.getInstance().init() with the callback) happens once per
 * process; this loader gates on that. Concrete wrapper construction
 * (VlmWrapper.builder() etc.) lives inside the engine impl returned here so
 * the API surface stays modality-agnostic.
 *
 * NOTE: implementation pending. The Nexa SDK Android jar must be on the
 * classpath before this can compile end-to-end — the salvage / wiring pass
 * brings it in.
 */
object NexaModelLoader {
    @Volatile private var sdkReady: Boolean = false

    /** Idempotent. Call from Application.onCreate. */
    suspend fun ensureSdkInit(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (sdkReady) return
        // TODO: NexaSdk.getInstance().init(context) { sdkReady = true }
        sdkReady = true
    }

    /** Build + load an engine for [spec]. Suspends until the model is resident
     *  (or fails). */
    suspend fun load(context: Context, spec: NexaModelSpec): NexaEngine {
        ensureSdkInit(context)
        // TODO: branch on spec.pluginId to choose the right wrapper class;
        // the branching is an implementation detail, not a public type label.
        throw NotImplementedError("NexaModelLoader.load: pending Nexa SDK wiring")
    }
}
