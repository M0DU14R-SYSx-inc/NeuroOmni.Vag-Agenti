package com.horizons.model

import kotlinx.coroutines.flow.Flow

interface EdgeModel {
    suspend fun load()
    suspend fun unload()
    fun generateStream(prompt: String, imagePath: String? = null): Flow<String>
    suspend fun buildMetaPrompt(rawText: String, target: MetaPromptTarget): String
    val backendTag: String
}

enum class MetaPromptTarget { ChatBox, BashCommand }
