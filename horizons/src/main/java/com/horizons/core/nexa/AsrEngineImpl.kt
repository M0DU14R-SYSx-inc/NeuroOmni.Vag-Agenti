package com.horizons.core.nexa

import com.nexa.sdk.AsrWrapper
import com.nexa.sdk.bean.AsrTranscribeInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class AsrEngineImpl(
    override val spec: NexaModelSpec,
    private val wrapper: AsrWrapper,
) : NexaEngine {

    override val isLoaded: Boolean get() = true

    override suspend fun load() = Unit   // wrapper is already resident after builder.build()

    override suspend fun unload() { wrapper.destroy() }

    override suspend fun infer(input: NexaInput): NexaOutput {
        val audioPath = input.attachments
            .filterIsInstance<NexaInput.Attachment.Audio>()
            .firstOrNull()?.path
            ?: return NexaOutput(text = "")
        val out = wrapper.transcribe(
            AsrTranscribeInput(audioPath = audioPath, language = "en")
        ).getOrThrow()
        return NexaOutput(text = out.result.transcript ?: "")
    }

    // ASR has no token stream — emit the full transcript as a single emission.
    override fun stream(input: NexaInput): Flow<String> = flow {
        val text = infer(input).text
        if (text.isNotEmpty()) emit(text)
    }
}
