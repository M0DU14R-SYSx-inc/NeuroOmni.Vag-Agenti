package com.horizons.core.nexa

import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.LlmStreamResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class VlmEngineImpl(
    override val spec: NexaModelSpec,
    private val wrapper: VlmWrapper,
) : NexaEngine {

    override val isLoaded: Boolean get() = true

    override suspend fun load() = Unit   // wrapper is already resident after builder.build()

    override suspend fun unload() { wrapper.destroy() }

    override suspend fun infer(input: NexaInput): NexaOutput {
        val sb = StringBuilder()
        wrapper.generateStreamFlow(input.text, genConfig(input)).collect { result ->
            when (result) {
                is LlmStreamResult.Token -> sb.append(result.text)
                is LlmStreamResult.Error -> throw result.throwable
                else -> Unit
            }
        }
        return NexaOutput(text = sb.toString())
    }

    override fun stream(input: NexaInput): Flow<String> =
        wrapper.generateStreamFlow(input.text, genConfig(input)).mapNotNull { result ->
            when (result) {
                is LlmStreamResult.Token -> result.text
                is LlmStreamResult.Error -> throw result.throwable
                else -> null
            }
        }

    private fun genConfig(input: NexaInput): GenerationConfig {
        val imgs = input.attachments
            .filterIsInstance<NexaInput.Attachment.Image>()
            .map { it.path }
            .toTypedArray()
        return GenerationConfig(
            maxTokens = spec.maxTokens,
            imagePaths = imgs.ifEmpty { null },
            imageCount = imgs.size,
        )
    }
}
