package com.horizons.provider

import android.content.Context
import android.util.Log
import com.horizons.orchestrator.Tool
import com.horizons.termux.TermuxBridge
import kotlinx.coroutines.flow.Flow

/**
 * Builds a [Tool] instance for a given [NamedBackend], dispatching on the
 * backend's [Wire] protocol. Pulls API keys / service-account JSON / project
 * coords from [CredentialStore] under the conventional keys.
 *
 * Returns null (and logs a warning) when:
 *  - the required credential is missing / blank,
 *  - the wire is unsupported (e.g. [Wire.WebSearch]),
 *  - or the backend's baseUrl doesn't match any known shape for that wire.
 */
object ProviderFactory {

    private const val TAG = "ProviderFactory"

    fun build(
        context: Context,
        backend: NamedBackend,
        credentials: CredentialStore,
        systemPrompt: String? = null,
    ): Tool? {
        return when (backend.wire) {
            Wire.OpenAIChatCompletions,
            Wire.OpenAIResponses -> buildOpenAICompat(backend, credentials)

            Wire.AnthropicMessages -> buildAnthropic(backend, credentials, systemPrompt)

            Wire.GoogleGenerateContent -> buildGoogle(backend, credentials, systemPrompt)

            Wire.WebSearch -> {
                Log.w(TAG, "WebSearch wire is not implemented: ${backend.id}")
                null
            }

            Wire.TermuxCli -> buildTermuxCli(context, backend)
        }
    }

    // ---- per-wire builders ----------------------------------------------------

    private fun buildOpenAICompat(backend: NamedBackend, credentials: CredentialStore): Tool? {
        val apiKey = credentials.get(backend.credentialKey)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "OpenAI-compatible: missing credential '${backend.credentialKey}' for ${backend.id}")
            return null
        }
        // OpenRouterClient currently hardcodes the OpenRouter base URL; for any
        // OpenAI-compatible backend we reuse it (acceptable while the surface
        // is OpenRouter-only). baseUrl from NamedBackend is recorded but the
        // client doesn't yet route off it — when a true multi-endpoint client
        // lands this branch will pass backend.baseUrl through.
        val client = OpenRouterClient(apiKey = apiKey, primaryModel = backend.modelId)
        return openRouterToTool(backend, client)
    }

    private fun buildAnthropic(
        backend: NamedBackend,
        credentials: CredentialStore,
        systemPrompt: String?,
    ): Tool? {
        val host = backend.baseUrl.substringAfter("://").substringBefore('/')
        return when {
            host == "api.anthropic.com" || backend.baseUrl.startsWith("https://api.anthropic.com") -> {
                if (!credentials.has(backend.credentialKey)) {
                    Log.w(TAG, "AnthropicDirect: missing credential '${backend.credentialKey}'")
                    return null
                }
                AnthropicDirectClient(
                    credentials = credentials,
                    model = backend.modelId,
                    systemPrompt = systemPrompt?.takeIf { it.isNotBlank() },
                )
            }
            isVertexUrl(backend.baseUrl) -> buildVertex(backend, credentials, VertexPublisher.ANTHROPIC, systemPrompt)
            else -> {
                Log.w(TAG, "AnthropicMessages: unrecognized baseUrl '${backend.baseUrl}' for ${backend.id}")
                null
            }
        }
    }

    private fun buildGoogle(
        backend: NamedBackend,
        credentials: CredentialStore,
        @Suppress("UNUSED_PARAMETER") systemPrompt: String?,
    ): Tool? {
        val host = backend.baseUrl.substringAfter("://").substringBefore('/')
        return when {
            host == "generativelanguage.googleapis.com" ||
                backend.baseUrl.startsWith("https://generativelanguage.googleapis.com") -> {
                if (!credentials.has("aistudio.key")) {
                    Log.w(TAG, "AIStudioGemini: missing credential 'aistudio.key'")
                    return null
                }
                // Gemini uses implicit caching server-side — no cache_control breakpoint
                // to wire from the client. Static-prefix benefit is automatic for repeated
                // prompts above the per-model threshold.
                AIStudioGeminiClient(credentials = credentials, model = backend.modelId)
            }
            isVertexUrl(backend.baseUrl) -> buildVertex(backend, credentials, VertexPublisher.GOOGLE, systemPrompt = null)
            else -> {
                Log.w(TAG, "GoogleGenerateContent: unrecognized baseUrl '${backend.baseUrl}' for ${backend.id}")
                null
            }
        }
    }

    private fun buildVertex(
        backend: NamedBackend,
        credentials: CredentialStore,
        publisher: VertexPublisher,
        systemPrompt: String? = null,
    ): Tool? {
        if (!credentials.has("vertex.service_account_json")) {
            Log.w(TAG, "Vertex: missing credential 'vertex.service_account_json'")
            return null
        }
        val project = credentials.get("vertex.project")
        val location = credentials.get("vertex.location")
        if (project.isNullOrBlank() || location.isNullOrBlank()) {
            Log.w(TAG, "Vertex: missing 'vertex.project' or 'vertex.location' in CredentialStore")
            return null
        }
        return VertexClient(
            publisher = publisher,
            model = backend.modelId,
            project = project,
            location = location,
            credentials = credentials,
            systemPrompt = systemPrompt?.takeIf { it.isNotBlank() && publisher == VertexPublisher.ANTHROPIC },
        )
    }

    private fun buildTermuxCli(context: Context, backend: NamedBackend): Tool {
        // NamedBackend has no dedicated "command template" field, so we
        // repurpose `modelId` as the shell command template. The TermuxCli
        // wire is the documented exception to the "modelId is a model name"
        // rule — see issue #17. Use `{prompt}` as the placeholder; if it's
        // absent the prompt is appended (POSIX-escaped) at the end.
        return TermuxCliClient(
            id = backend.id,
            displayName = backend.displayName,
            commandTemplate = backend.modelId,
            bridge = TermuxBridge(context.applicationContext),
        )
    }

    // ---- helpers --------------------------------------------------------------

    /**
     * A Vertex AI URL has the form
     *   https://<region>-aiplatform.googleapis.com/...
     * The publisher (anthropic / google) lives in the path, not the host.
     */
    private fun isVertexUrl(url: String): Boolean =
        url.contains("-aiplatform.googleapis.com") || url.contains("aiplatform.googleapis.com")

    /**
     * Adapter from [OpenRouterClient] (which exposes a plain `stream(prompt)`
     * flow and does not implement [Tool]) to the [Tool] interface used by the
     * orchestrator / Router panel.
     */
    private fun openRouterToTool(backend: NamedBackend, client: OpenRouterClient): Tool =
        object : Tool {
            override val id: String = backend.id
            override val displayName: String = backend.displayName
            override fun run(prompt: String, imagePath: String?): Flow<String> = client.stream(prompt)
        }
}
