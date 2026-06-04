package com.horizons.model

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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull

/**
 * OmniNeural-4B-mobile on the Hexagon NPU via the Nexa Android SDK.
 * modelFolder = flat folder of weights-*.nexa shards + attachments-*.nexa + files-1-1.nexa + nexa.manifest.
 * Per the MODEL-FOLDER HYGIENE rule: no subdirectories (otherwise SDK create() returns -863151000).
 */
class NexaVlmEngine(
    private val context: Context,
    private val modelFolder: String
) : EdgeModel {
    override val backendTag = "nexa-npu"
    private var vlm: VlmWrapper? = null

    override suspend fun load() {
        NexaSdk.getInstance().init(context)
        // Per Nexa Android docs: model_path is a FILE PATH pointing at files-1-1.nexa,
        // not the folder. The SDK reads nexa.manifest + the shards alongside it.
        val entry = java.io.File(modelFolder, "files-1-1.nexa").absolutePath
        val input = VlmCreateInput(
            model_name = "omni-neural",
            model_path = entry,
            mmproj_path = "",
            config = ModelConfig(max_tokens = 2048, enable_thinking = false),
            plugin_id = NexaSdk.PLUGIN_ID_NPU,
            device_id = ""
        )
        vlm = VlmWrapper.builder().vlmCreateInput(input).build().getOrThrow()
        Log.i(TAG, "OmniNeural-4B-mobile loaded on Hexagon NPU; entry=$entry")
    }

    override suspend fun unload() {
        runCatching { vlm?.destroy() }
        vlm = null
    }

    override fun generateStream(prompt: String, imagePath: String?): Flow<String> {
        val wrapper = vlm ?: return flow { throw IllegalStateException("NexaVlmEngine.load() not called") }
        // Phase 3: pass imagePath into the VLM image-input call for Screen-ask.
        return wrapper.generateStreamFlow(prompt, GenerationConfig())
            .mapNotNull { result ->
                when (result) {
                    is LlmStreamResult.Token -> result.text
                    is LlmStreamResult.Error -> throw result.throwable
                    is LlmStreamResult.Completed -> {
                        val p = result.profile
                        // Decode speed is the Hexagon proof — CPU/GPU fallback is many x slower.
                        Log.i(TAG, "decode=%.1f tok/s prefill=%.1f tok/s ttft=%.0fms gen=%d stop=%s".format(
                            p.decodingSpeed, p.prefillSpeed, p.ttftMs, p.generatedTokens, p.stopReason
                        ))
                        null
                    }
                    else -> null
                }
            }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun buildMetaPrompt(rawText: String, target: MetaPromptTarget): String {
        // Phase 3: real meta-prompt system prompts (ChatBox vs BashCommand).
        return rawText
    }

    private companion object { const val TAG = "NexaVlmEngine" }
}
