package com.neuroomni.horizons.provider

import com.neuroomni.horizons.model.EdgeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The routing seam behind the Chat panel (Architecture §5: "the toggle IS the routing
 * decision"). [Edge][ProviderId.Edge] streams from the on-device [EdgeModel]; every
 * other toggle goes out over HTTP via [FrontierChatClient]. The Chat panel doesn't
 * know or care which — it just collects a `Flow<String>` of tokens.
 */
class ChatRouter(
    private val edgeModel: EdgeModel,
    private val frontierClient: FrontierChatClient = FrontierChatClient(),
) {
    fun stream(provider: ProviderId, config: EndpointConfig, prompt: String): Flow<String> = when {
        provider.isEdge -> edgeModel.generateStream(prompt)
        provider.transport == Transport.TermuxShell ->
            flow { emit("[${provider.displayName}] runs through the Termux shell layer (Session 6) — not wired yet.") }
        !provider.implemented ->
            flow { emit("[${provider.displayName}] not implemented yet — pick Edge, Anthropic, AI Studio, or Ollama.") }
        !config.isConfigured ->
            flow { emit("[${provider.displayName}] needs configuration — open the Router panel and add a model${if (provider == ProviderId.OllamaCompatible) "." else " + API key."}") }
        else -> frontierClient.stream(config, prompt)
    }
}
