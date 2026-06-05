package com.horizons.orchestrator

import android.content.Context
import android.util.Log
import com.horizons.model.EdgeModel
import com.horizons.model.StubEdgeModel
import com.horizons.provider.CredentialStore
import com.horizons.provider.OpenRouterClient
import com.horizons.provider.ProviderFactory
import com.horizons.provider.ProviderLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Local-first / cloud-fallback dispatcher.
 *
 * Per-request flow:
 *   1. If forcedToolId is set (explicit operator pick), route there
 *   2. Else if on-device engine is real (NexaVlmEngine, loaded) → use it
 *   3. Else if a NamedBackend is marked isFailoverTarget in ProviderLibrary,
 *      build it via ProviderFactory and route there
 *   4. Else if openrouter.key is in CredentialStore (legacy fallback), use OpenRouterClient
 *   5. Else stub error
 */
class Orchestrator(
    private val context: Context,
    private val getEdge: () -> EdgeModel,
    private val credentials: CredentialStore,
    private val library: ProviderLibrary,
    private val systemPromptSupplier: () -> String = { "" },
) {
    fun stream(prompt: String, imagePath: String? = null, forcedToolId: String? = null): Flow<String> = flow {
        val sys = systemPromptSupplier().takeIf { it.isNotBlank() }
        // Explicit operator pick wins.
        if (forcedToolId != null) {
            val backend = library.byId(forcedToolId)
            if (backend != null) {
                val tool = ProviderFactory.build(context, backend, credentials, sys)
                if (tool != null) {
                    emitAll(tool.run(prompt, imagePath).catch { e ->
                        emit("[forced backend $forcedToolId failed: ${e.javaClass.simpleName}: ${e.message}]")
                    })
                    return@flow
                }
                emit("[forced backend $forcedToolId: factory returned null — bad config or missing credential]")
                return@flow
            }
            emit("[forced backend $forcedToolId not found in provider library]")
            return@flow
        }

        // Default: NPU first if real.
        val engine = getEdge()
        if (engine !is StubEdgeModel) {
            var failed = false
            emitAll(
                engine.generateStream(prompt, imagePath).catch {
                    Log.w(TAG, "edge stream failed (${it.javaClass.simpleName}); falling back to cloud", it)
                    failed = true
                }
            )
            if (!failed) return@flow
        }

        // Failover lane: prefer marked NamedBackend, else legacy openrouter.key direct.
        val failover = library.failoverTarget()
        if (failover != null) {
            val tool = ProviderFactory.build(context, failover, credentials, sys)
            if (tool != null) {
                emitAll(tool.run(prompt, imagePath).catch { e ->
                    emit("[failover ${failover.displayName} failed: ${e.javaClass.simpleName}: ${e.message}]")
                })
                return@flow
            }
        }

        val key = credentials.get("openrouter.key")
        if (!key.isNullOrBlank()) {
            emitAll(OpenRouterClient(key).stream(prompt))
            return@flow
        }

        emit("[no engine: NPU unloaded, no failover backend marked, no openrouter.key]")
    }

    private companion object { const val TAG = "Orchestrator" }
}
