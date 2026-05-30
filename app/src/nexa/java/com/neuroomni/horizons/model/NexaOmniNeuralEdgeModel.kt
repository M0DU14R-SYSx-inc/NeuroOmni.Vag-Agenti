package com.neuroomni.horizons.model

import android.content.Context
import android.util.Log
import ai.nexa.core.GenerationConfig
import ai.nexa.core.ModelConfig
import ai.nexa.core.NexaSdk
import ai.nexa.core.VlmCreateInput
import ai.nexa.core.VlmWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real on-device [EdgeModel]: OmniNeural-4B (VLM) on the Hexagon NPU via the Nexa
 * Android SDK. Compiled only into the `-PnexaEnabled=true` build (src/nexa/java),
 * so the default/CI build never references the SDK.
 *
 * Constructed reflectively by [EdgeModelFactory] with (context, token, modelPath).
 * The token comes from BuildConfig (sourced from local.properties); it is never
 * hardcoded or committed.
 */
class NexaOmniNeuralEdgeModel(
    private val context: Context,
    @Suppress("unused") private val token: String,
    private val modelPath: String,
) : EdgeModel {

    private var vlm: VlmWrapper? = null

    override suspend fun initialize(): Result<Unit> = runCatching {
        NexaSdk.getInstance().init(context)
        vlm = suspendCancellableCoroutine { cont ->
            VlmWrapper.builder()
                .create(
                    VlmCreateInput(
                        model_name = "omni-neural",
                        model_path = modelPath,
                        plugin_id = "npu",
                        config = ModelConfig(max_tokens = MAX_TOKENS),
                    ),
                    onSuccess = { wrapper -> if (cont.isActive) cont.resume(wrapper) },
                    onError = { err ->
                        if (cont.isActive) {
                            cont.resumeWith(Result.failure(IllegalStateException("Nexa VLM create failed: $err")))
                        }
                    },
                )
        }
        Log.i(TAG, "OmniNeural-4B initialized on NPU")
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        val wrapper = vlm ?: error("NexaOmniNeuralEdgeModel not initialized")
        wrapper.generateStreamFlow(prompt, GenerationConfig()).collect { token -> emit(token) }
    }.flowOn(Dispatchers.Default)

    override fun release() {
        runCatching { vlm?.destroy() }
        vlm = null
    }

    private companion object {
        const val TAG = "NexaOmniNeural"
        const val MAX_TOKENS = 2048
    }
}
