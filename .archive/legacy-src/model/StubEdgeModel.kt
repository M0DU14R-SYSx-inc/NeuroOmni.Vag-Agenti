package com.horizons.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubEdgeModel : EdgeModel {
    override val backendTag = "stub"
    override suspend fun load() {}
    override suspend fun unload() {}
    override fun generateStream(prompt: String, imagePath: String?): Flow<String> = flow {
        emit("[stub] received: $prompt")
    }
    override suspend fun buildMetaPrompt(rawText: String, target: MetaPromptTarget) = rawText
}
