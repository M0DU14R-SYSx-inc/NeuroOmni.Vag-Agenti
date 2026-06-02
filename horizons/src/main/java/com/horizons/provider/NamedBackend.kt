package com.horizons.provider

import kotlinx.serialization.Serializable

@Serializable
data class NamedBackend(
    val id: String,
    val displayName: String,
    val wire: Wire,
    val baseUrl: String,
    val modelId: String,
    val credentialKey: String,
    val isFailoverTarget: Boolean = false,
    val dispatcherEligible: Boolean = true,
    val purposeHint: String? = null
)

@Serializable
enum class Wire { OpenAIChatCompletions, AnthropicMessages, GoogleGenerateContent, OpenAIResponses, WebSearch }
