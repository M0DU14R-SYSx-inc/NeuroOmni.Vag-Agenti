package com.neuroomni.horizons.model

import android.content.Context
import android.util.Log
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull

/**
 * Real on-device [EdgeModel]: OmniNeural-4B on the Hexagon NPU via the Nexa Android
 * SDK (`ai.nexa:core`, package `com.nexa.sdk`). Compiled only into the
 * `-PnexaEnabled=true` build (src/nexa/java), so the default/CI build never references
 * the SDK.
 *
 * Constructed reflectively by [EdgeModelFactory] with (context, token, modelPath), where
 * modelPath is the model folder (8 weights-*.nexa shards + nexa.manifest, covering the
 * LLM + vision + audio encoders). The SDK runtime takes no token — the Nexa key is for
 * *downloading* the model, not running it — so [token] is kept only to keep the
 * reflective ctor stable.
 */
class NexaOmniNeuralEdgeModel(
    private val context: Context,
    @Suppress("unused") private val token: String,
    private val modelPath: String,
) : EdgeModel {

    private var vlm: VlmWrapper? = null

    override suspend fun initialize(): Result<Unit> = runCatching {
        // Init the SDK runtime: extracts the QNN/HTP native assets and registers the
        // NPU plugin. The completion callback is an optional default arg.
        NexaSdk.getInstance().init(context)

        val input = VlmCreateInput(
            model_name = "omni-neural",
            model_path = modelPath,
            mmproj_path = "",
            config = ModelConfig(),
            plugin_id = NexaSdk.PLUGIN_ID_NPU,
            device_id = "",
        )
        vlm = VlmWrapper.builder()
            .vlmCreateInput(input)
            .build()
            .getOrThrow()
        Log.i(TAG, "OmniNeural-4B initialized on NPU")
    }

    override fun generateStream(prompt: String): Flow<String> {
        val wrapper = vlm ?: error("NexaOmniNeuralEdgeModel not initialized")
        return wrapper.generateStreamFlow(prompt, GenerationConfig())
            .mapNotNull { result ->
                when (result) {
                    is LlmStreamResult.Token -> result.text
                    is LlmStreamResult.Error -> throw result.throwable
                    is LlmStreamResult.Completed -> {
                        // Backend proof: on the Hexagon DSP decode speed is many ×
                        // a CPU/GPU fallback, so this line tells us definitively which
                        // path engaged on the Razr (Architecture §6 / Diagnostics §3).
                        val p = result.profile
                        Log.i(
                            TAG,
                            "decode=%.1f tok/s prefill=%.1f tok/s ttft=%.0fms gen=%d stop=%s"
                                .format(
                                    p.decodingSpeed,
                                    p.prefillSpeed,
                                    p.ttftMs,
                                    p.generatedTokens,
                                    p.stopReason,
                                ),
                        )
                        null // end of stream
                    }
                    else -> null
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override fun release() {
        runCatching { vlm?.destroy() }
        vlm = null
    }

    private companion object {
        const val TAG = "NexaOmniNeural"
    }
}
