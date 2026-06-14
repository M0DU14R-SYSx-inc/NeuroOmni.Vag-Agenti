package com.horizons.core.nexa

import android.content.Context
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmStreamResult
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile

/**
 * Live Nexa VLM engine — wraps [VlmWrapper] from the Nexa Android SDK.
 *
 * Covers all currently-loadable models on the device:
 *   - OmniNeural-4B on Hexagon NPU (`plugin_id="npu"`)
 *   - Gemma-4-E4B-IT on Adreno GPU (`plugin_id="cpu_gpu"`, `device_id="dev0"`)
 *
 * Public surface ([NexaEngine]) does not expose the wrapper or the
 * underlying plugin — Truman Show. Callers `infer()` or `stream()`; the
 * routing is internal.
 *
 * Bytecode-confirmed signatures (decompiled 2026-06-14 per
 * `rules/AAR_DECOMPILE.md`):
 *   - `VlmCreateInput(name, model_path, mmproj_path, ModelConfig, plugin_id, device_id)`
 *   - `ModelConfig` has `max_tokens` + `enable_thinking` + many `n*` fields.
 *   - `VlmWrapper.Builder.build()` is suspend, returns `Result<VlmWrapper>`.
 *   - `generateStreamFlow(prompt, GenerationConfig)` returns
 *     `Flow<LlmStreamResult>` with subtypes Token / Completed / Error.
 */
internal class LiveNexaVlmEngine(
    override val spec: NexaModelSpec,
) : NexaEngine {

    @Volatile private var wrapper: VlmWrapper? = null

    override val isLoaded: Boolean get() = wrapper != null

    override suspend fun load() {
        if (wrapper != null) return
        val createInput = VlmCreateInput(
            spec.name,
            spec.modelPath,
            spec.mmprojPath,
            buildModelConfig(),
            spec.pluginId,
            spec.deviceId,
        )
        wrapper = VlmWrapper.Companion.builder()
            .vlmCreateInput(createInput)
            .dispatcher(Dispatchers.IO)
            .build()
            .getOrThrow()
    }

    override suspend fun unload() {
        wrapper?.let { runCatching { it.destroy() } }
        wrapper = null
    }

    override suspend fun infer(input: NexaInput): NexaOutput {
        val sb = StringBuilder()
        stream(input).collect { sb.append(it) }
        return NexaOutput(text = sb.toString())
    }

    override fun stream(input: NexaInput): Flow<String> {
        val engine = wrapper ?: error("NexaEngine not loaded: ${spec.name}")
        val genConfig = buildGenerationConfig(input)
        return engine.generateStreamFlow(input.text, genConfig)
            .transformWhile { result ->
                when (result) {
                    is LlmStreamResult.Token -> { emit(result.text); true }
                    else -> false // Completed or Error → terminate
                }
            }
            .map { it } // keep type as Flow<String>
    }

    private fun buildModelConfig(): ModelConfig =
        ModelConfig().apply {
            // Mostly defaults; the loader-relevant knobs ride on the GenerationConfig per-call.
            // max_tokens + enable_thinking are read at load time from this struct.
            // ModelConfig has many fields; the no-arg ctor populates defaults.
        }

    private fun buildGenerationConfig(input: NexaInput): GenerationConfig {
        val imagePaths = input.attachments
            .filterIsInstance<NexaInput.Attachment.Image>()
            .map { it.path }
            .toTypedArray()
        val audioPaths = input.attachments
            .filterIsInstance<NexaInput.Attachment.Audio>()
            .map { it.path }
            .toTypedArray()
        return GenerationConfig().apply {
            maxTokens = spec.maxTokens
            if (imagePaths.isNotEmpty()) {
                this.imagePaths = imagePaths
                this.imageCount = imagePaths.size
            }
            if (audioPaths.isNotEmpty()) {
                this.audioPaths = audioPaths
                this.audioCount = audioPaths.size
            }
        }
    }

    companion object {
        /** Idempotent. Init the Nexa SDK once per process. */
        suspend fun initSdk(context: Context) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                NexaSdk.Companion.getInstance().init(
                    context.applicationContext,
                    object : NexaSdk.InitCallback {
                        override fun onSuccess() {
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                        }
                        override fun onFailure(reason: String) {
                            if (cont.isActive) cont.resumeWith(
                                Result.failure(RuntimeException("NexaSdk init failed: $reason"))
                            )
                        }
                    }
                )
            }
        }
    }
}
