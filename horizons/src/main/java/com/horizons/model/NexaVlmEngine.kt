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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NexaVlmEngine(
    private val context: Context,
    private val modelFolder: String
) : EdgeModel {
    override val backendTag = "nexa-npu"
    private var vlm: VlmWrapper? = null

    @Volatile var lastFolderListing: String = "(not loaded yet)"
        private set
    @Volatile var lastInitMessage: String = "(not initialized)"
        private set

    override suspend fun load() {
        // Auto-create config.json if missing (HF ships 0 bytes, Chrome may drop).
        runCatching {
            val cfg = java.io.File(modelFolder, "config.json")
            if (!cfg.exists()) cfg.createNewFile()
        }
        // Snapshot folder contents for diagnostics.
        lastFolderListing = runCatching {
            java.io.File(modelFolder).listFiles()
                ?.sortedBy { it.name }
                ?.joinToString("\n") { "  ${it.name} (${it.length()}B)" }
                ?: "no listing"
        }.getOrElse { "listing failed: ${it.message}" }
        Log.i(TAG, "Loading from $modelFolder:\n$lastFolderListing")

        // Initialize with explicit callback so we capture the failure message.
        // Per decompiled SDK: init iterates HTP_ASSET_DIRS + registers all
        // plugins; only the callback overload reports problems.
        val initResult = suspendCoroutine<Result<String>> { cont ->
            NexaSdk.getInstance().init(context, object : NexaSdk.InitCallback {
                override fun onSuccess() {
                    cont.resume(Result.success("ok"))
                }
                override fun onFailure(message: String) {
                    cont.resume(Result.failure(IllegalStateException("NexaSdk.init failed: $message")))
                }
            })
        }
        lastInitMessage = initResult.fold(
            { "init ok" },
            { it.message ?: it.javaClass.simpleName }
        )
        initResult.getOrThrow()
        Log.i(TAG, "NexaSdk.init succeeded")

        val input = VlmCreateInput(
            model_name = "omni-neural",
            model_path = modelFolder,
            mmproj_path = "",
            config = ModelConfig(max_tokens = 2048, enable_thinking = false),
            plugin_id = NexaSdk.PLUGIN_ID_NPU,
            device_id = ""
        )
        vlm = VlmWrapper.builder().vlmCreateInput(input).build().getOrThrow()
        Log.i(TAG, "OmniNeural-4B-mobile loaded on Hexagon NPU; folder=$modelFolder")
    }

    override suspend fun unload() {
        runCatching { vlm?.destroy() }
        vlm = null
    }

    override fun generateStream(prompt: String, imagePath: String?): Flow<String> {
        val wrapper = vlm ?: return flow { throw IllegalStateException("NexaVlmEngine.load() not called") }
        return wrapper.generateStreamFlow(prompt, GenerationConfig())
            .mapNotNull { result ->
                when (result) {
                    is LlmStreamResult.Token -> result.text
                    is LlmStreamResult.Error -> throw result.throwable
                    is LlmStreamResult.Completed -> {
                        val p = result.profile
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

    override suspend fun buildMetaPrompt(rawText: String, target: MetaPromptTarget): String = rawText

    private companion object { const val TAG = "NexaVlmEngine" }
}
