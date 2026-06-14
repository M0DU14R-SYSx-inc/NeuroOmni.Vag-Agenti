package com.horizons.core.nexa

import com.nexa.sdk.AsrWrapper
import com.nexa.sdk.bean.AsrCreateInput
import com.nexa.sdk.bean.AsrTranscribeInput
import com.nexa.sdk.bean.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Live Nexa ASR engine — wraps [AsrWrapper] from the Nexa Android SDK.
 *
 * Today's resident: **Parakeet TDT/ASR** on Hexagon NPU (`plugin_id="npu"`),
 * co-resident with OmniNeural-4B per boundary 8.
 *
 * Bytecode-confirmed signatures (`rules/AAR_DECOMPILE.md`):
 *   - `AsrCreateInput(name, model_path, tokenizer_path, ModelConfig, language,
 *     plugin_id, device_id, license_id, license_key)` — 9 fields.
 *   - `AsrWrapper.Builder.build()` is suspend, returns `Result<AsrWrapper>`.
 *   - `transcribe(AsrTranscribeInput)` is suspend, returns `Result<AsrTranscribeOutput>`.
 *   - `AsrTranscribeInput(audioPath, language, AsrConfig?)`.
 *   - `AsrTranscribeOutput.result.transcript` is the recognized text.
 *
 * The public [NexaEngine] surface stays Truman Show. ASR input rides on
 * [NexaInput.attachments] as [NexaInput.Attachment.Audio]; the engine
 * picks the first audio path and transcribes it. Text in [NexaInput.text]
 * is ignored for ASR (no prompt prefix concept in this wrapper).
 */
internal class LiveNexaAsrEngine(
    override val spec: NexaModelSpec,
    private val language: String = DEFAULT_LANGUAGE,
    private val tokenizerPath: String = "",
) : NexaEngine {

    @Volatile private var wrapper: AsrWrapper? = null

    override val isLoaded: Boolean get() = wrapper != null

    override suspend fun load() {
        if (wrapper != null) return
        val createInput = AsrCreateInput(
            spec.name,
            spec.modelPath,
            tokenizerPath,
            ModelConfig(),
            language,
            spec.pluginId,
            spec.deviceId,
            "",  // license_id
            "",  // license_key
        )
        wrapper = AsrWrapper.Companion.builder()
            .asrCreateInput(createInput)
            .dispatcher(Dispatchers.IO)
            .build()
            .getOrThrow()
    }

    override suspend fun unload() {
        wrapper?.let { runCatching { it.destroy() } }
        wrapper = null
    }

    override suspend fun infer(input: NexaInput): NexaOutput {
        val engine = wrapper ?: error("NexaEngine not loaded: ${spec.name}")
        val audioPath = input.attachments
            .filterIsInstance<NexaInput.Attachment.Audio>()
            .firstOrNull()?.path
            ?: error("LiveNexaAsrEngine.infer: NexaInput.attachments must contain Audio")
        val out = engine.transcribe(
            AsrTranscribeInput(audioPath, language, com.nexa.sdk.bean.AsrConfig("", null, false))
        ).getOrThrow()
        return NexaOutput(text = out.result.transcript ?: "")
    }

    override fun stream(input: NexaInput): Flow<String> = flow {
        // Parakeet supports streamBegin / streamPushAudio / streamStop; for v1
        // we expose only the one-shot transcribe via [infer]. Stream support is
        // deferred — callers needing it use [infer] for now and we'll add the
        // streaming path when the mic-capture pipeline lands.
        emit(infer(input).text)
    }

    companion object {
        const val DEFAULT_LANGUAGE = "en"
    }
}
