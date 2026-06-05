package com.horizons.orchestrator

import android.content.Context
import android.util.Log
import com.horizons.model.EdgeModel
import com.horizons.model.StubEdgeModel
import com.horizons.provider.CredentialStore
import com.horizons.provider.OpenRouterClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Local-first / cloud-fallback dispatcher.
 *
 * Per-request flow:
 *   1. If on-device engine is NexaVlmEngine and loaded → use it
 *   2. Else if OpenRouter key is present → use cloud (failover lane)
 *   3. Else fall through to Stub so the UI never hangs
 *
 * Phase 5 will let the VLM pick between multiple cloud backends from the
 * provider library when both NPU and cloud are healthy. Today: NPU > cloud > stub.
 */
class Orchestrator(
    private val context: Context,
    private val getEdge: () -> EdgeModel,
    private val credentials: CredentialStore
) {
    fun stream(prompt: String, imagePath: String? = null): Flow<String> = flow {
        val engine = getEdge()
        val isReal = engine !is StubEdgeModel
        if (isReal) {
            // NPU loaded — try it first. On error, fall back to cloud.
            var failed = false
            emitAll(
                engine.generateStream(prompt, imagePath)
                    .catch {
                        Log.w(TAG, "edge stream failed (${it.javaClass.simpleName}); falling back to cloud", it)
                        failed = true
                    }
            )
            if (!failed) return@flow
        }
        // Cloud fallback path
        val key = credentials.get("openrouter.key")
        if (key.isNullOrBlank()) {
            emit("[no engine: NPU not loaded and no openrouter.key in CredentialStore]")
            return@flow
        }
        val cloud = OpenRouterClient(key)
        emitAll(cloud.stream(prompt))
    }

    private companion object { const val TAG = "Orchestrator" }
}
