package com.horizons.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NexaVlmEngine(private val modelFolder: String) : EdgeModel {
    override val backendTag = "nexa-npu"

    override suspend fun load() {
        TODO("Phase 1: NexaSdk.getInstance().init(ctx); VlmWrapper(VlmCreateInput(plugin_id=NPU, model_name=omni-neural, modelFolder))")
    }

    override suspend fun unload() {
        TODO("Phase 1: release the wrapper.")
    }

    override fun generateStream(prompt: String, imagePath: String?): Flow<String> = flow {
        TODO("Phase 1: call generateStream; emit token deltas. Phase 3: image input for screen-ask.")
    }

    override suspend fun buildMetaPrompt(rawText: String, target: MetaPromptTarget): String {
        TODO("Phase 3: system prompt that converts muddled STT to clean prompt (ChatBox) or bash command (BashCommand).")
    }
}
