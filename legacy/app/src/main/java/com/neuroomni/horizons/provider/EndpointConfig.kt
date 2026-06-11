package com.neuroomni.horizons.provider

/**
 * The per-provider endpoint configuration Derek edits in the Router panel
 * (Spec §4 "Model string abstraction" / §6 "Endpoint Configuration").
 *
 * The [apiKey] lives in the Keystore-backed [CredentialStore]; [baseUrl] and
 * [modelString] live in the same encrypted store alongside it. Defaults come from
 * [defaultFor] so a fresh install already points at the right host with a sane model.
 */
data class EndpointConfig(
    val provider: ProviderId,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelString: String = "",
    val maxTokens: Int = 1024,
) {
    /** Has enough to attempt a call: a model string, plus a key for providers that need one. */
    val isConfigured: Boolean
        get() = modelString.isNotBlank() &&
            (provider == ProviderId.OllamaCompatible || apiKey.isNotBlank())

    companion object {
        fun defaultFor(provider: ProviderId): EndpointConfig = when (provider) {
            ProviderId.AnthropicDirect -> EndpointConfig(
                provider = provider,
                baseUrl = "https://api.anthropic.com",
                modelString = "claude-opus-4-7",
                maxTokens = 2048,
            )
            ProviderId.AIStudioGemini -> EndpointConfig(
                provider = provider,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                modelString = "gemini-2.5-flash",
                maxTokens = 2048,
            )
            ProviderId.OpenAICompatible -> EndpointConfig(
                provider = provider,
                baseUrl = "https://openrouter.ai/api/v1",
                modelString = "",
                maxTokens = 2048,
            )
            ProviderId.OllamaCompatible -> EndpointConfig(
                provider = provider,
                // Reachable over Tailscale; auth is the tailnet itself (Spec §4).
                baseUrl = "http://jetson:11434",
                modelString = "llama3.1",
                maxTokens = 2048,
            )
            ProviderId.VertexClaude, ProviderId.VertexGemini -> EndpointConfig(
                provider = provider,
                baseUrl = "https://us-central1-aiplatform.googleapis.com",
                modelString = if (provider == ProviderId.VertexClaude) "claude-opus-4-7@20260514" else "gemini-2.5-pro",
                maxTokens = 2048,
            )
            else -> EndpointConfig(provider = provider)
        }
    }
}
