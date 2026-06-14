package com.horizons.core.nexa

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for loading any Nexa-runnable model. Public API takes
 * a [NexaModelSpec] and returns a loaded [NexaEngine]. Callers never see
 * the concrete wrapper or know which plugin is hosting the model — Truman
 * Show.
 *
 * Plugin branching is an internal implementation detail of this loader.
 * Today: NPU + GPU both use [VlmWrapper] under [LiveNexaVlmEngine]. ASR
 * (Parakeet) will add an [AsrWrapper]-backed engine in a follow-up at-bat;
 * the public surface stays unchanged.
 *
 * SDK init (one-shot, idempotent) happens via [LiveNexaVlmEngine.initSdk]
 * the first time [load] is called.
 */
object NexaModelLoader {
    @Volatile private var sdkReady: Boolean = false
    private val initMutex = Mutex()

    /** Idempotent. Safe to call from Application.onCreate. */
    suspend fun ensureSdkInit(context: Context) {
        if (sdkReady) return
        initMutex.withLock {
            if (sdkReady) return
            LiveNexaVlmEngine.initSdk(context)
            sdkReady = true
        }
    }

    /** Build + load an engine for [spec]. Suspends until the model is
     *  resident, or throws on failure. Caller treats the returned engine
     *  as opaque. */
    suspend fun load(context: Context, spec: NexaModelSpec): NexaEngine {
        ensureSdkInit(context)
        val engine: NexaEngine = if (spec.isAsr) {
            LiveNexaAsrEngine(
                spec = spec,
                language = spec.asrLanguage.ifEmpty { LiveNexaAsrEngine.DEFAULT_LANGUAGE },
                tokenizerPath = spec.tokenizerPath,
            )
        } else {
            LiveNexaVlmEngine(spec)
        }
        engine.load()
        return engine
    }
}
