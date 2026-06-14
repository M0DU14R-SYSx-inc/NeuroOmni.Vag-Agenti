package com.horizons.core.nexa

import kotlinx.coroutines.flow.Flow

/**
 * Opaque handle to a loaded Nexa model. Truman Show: callers don't know which
 * device, plugin, or modality is behind it. They send input, get output.
 *
 * Implementations wrap the appropriate Nexa SDK wrapper (VlmWrapper for LLM /
 * MLLM, AsrWrapper for ASR, etc.) but never surface that distinction.
 */
interface NexaEngine {
    val spec: NexaModelSpec
    val isLoaded: Boolean

    suspend fun load()
    suspend fun unload()

    /** One-shot inference. Text-in, text-out covers the common case; richer
     *  inputs (images, audio frames) ride [NexaInput.attachments]. */
    suspend fun infer(input: NexaInput): NexaOutput

    /** Streaming inference for chat-style flows. */
    fun stream(input: NexaInput): Flow<String>
}

data class NexaInput(
    val text: String,
    val attachments: List<Attachment> = emptyList(),
) {
    sealed interface Attachment {
        data class Image(val path: String) : Attachment
        data class Audio(val path: String) : Attachment
    }
}

data class NexaOutput(
    val text: String,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
)
