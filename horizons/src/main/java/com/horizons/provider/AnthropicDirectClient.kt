package com.horizons.provider

import com.horizons.orchestrator.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct Anthropic Messages API client with prompt caching.
 *
 * Streams tokens via Anthropic's SSE event stream. The API key is fetched
 * lazily on each call from [CredentialStore] under the `anthropic.key` convention.
 *
 * Prompt caching is wired via [systemPrompt] — when present, it's sent as a
 * `system` block array with `cache_control` on the last block. Pricing per
 * Anthropic docs:
 *   - Cache write (5 min TTL): 1.25x base input price
 *   - Cache write (1h TTL):    2x base input price
 *   - Cache read:              0.1x base input price (90% discount)
 *
 * Minimum prompt size to cache: 1,024 tokens for Sonnet, 4,096 for Opus/Haiku.
 *
 * Verify cache hits via [lastUsage] — populated from `message_start`'s `usage`:
 *   - cache_creation_input_tokens > 0 → cache write occurred
 *   - cache_read_input_tokens > 0    → cache read occurred (savings active)
 *
 * Only text deltas are emitted (`content_block_delta` with `delta.type ==
 * "text_delta"`); `error` events surface as IllegalStateException.
 */
class AnthropicDirectClient(
    private val credentials: CredentialStore,
    private val model: String = DEFAULT_MODEL,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
    private val systemPrompt: String? = null,
    private val cacheTtl: CacheTtl = CacheTtl.FIVE_MIN
) : Tool {
    override val id: String = "anthropic-direct"
    override val displayName: String = "Anthropic (direct)"

    enum class CacheTtl(val apiValue: String?) {
        NONE(null),
        FIVE_MIN("5m"),
        ONE_HOUR("1h")
    }

    data class Usage(
        val cacheCreationTokens: Int,
        val cacheReadTokens: Int,
        val inputTokens: Int,
        val outputTokens: Int
    ) {
        val isCacheHit: Boolean get() = cacheReadTokens > 0
    }

    @Volatile var lastUsage: Usage? = null
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun run(prompt: String, imagePath: String?): Flow<String> = flow {
        val apiKey = credentials.get(CRED_KEY)
            ?: throw IllegalStateException("Anthropic: no API key — set '$CRED_KEY' in CredentialStore")

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens)
            put("stream", true)
            // System prompt as a structured array with cache_control on the last block.
            // This is the "static prefix" that gets cached. Pre-warm by sending a request
            // with this same systemPrompt + max_tokens:1 before fanning out sub-agents.
            if (!systemPrompt.isNullOrEmpty()) {
                put("system", JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", systemPrompt)
                    if (cacheTtl != CacheTtl.NONE) {
                        put("cache_control", JSONObject().apply {
                            put("type", "ephemeral")
                            cacheTtl.apiValue?.let { put("ttl", it) }
                        })
                    }
                }))
            }
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IllegalStateException("Anthropic HTTP ${resp.code}: $err")
            }
            val source = resp.body?.source() ?: throw IllegalStateException("Anthropic: empty body")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                if (!line.startsWith("data:")) continue
                val payload = line.substringAfter("data:").trim()
                if (payload.isEmpty()) continue

                val token = runCatching {
                    val obj = JSONObject(payload)
                    when (obj.optString("type")) {
                        "message_start" -> {
                            // Capture cache usage for verification.
                            obj.optJSONObject("message")?.optJSONObject("usage")?.let { u ->
                                lastUsage = Usage(
                                    cacheCreationTokens = u.optInt("cache_creation_input_tokens", 0),
                                    cacheReadTokens = u.optInt("cache_read_input_tokens", 0),
                                    inputTokens = u.optInt("input_tokens", 0),
                                    outputTokens = u.optInt("output_tokens", 0)
                                )
                            }
                            null
                        }
                        "content_block_delta" -> {
                            val delta = obj.optJSONObject("delta")
                            if (delta?.optString("type") == "text_delta") {
                                delta.optString("text").takeIf { it.isNotEmpty() }
                            } else null
                        }
                        "message_delta" -> {
                            // Anthropic emits final usage totals here; update output tokens.
                            obj.optJSONObject("usage")?.let { u ->
                                lastUsage = lastUsage?.copy(
                                    outputTokens = u.optInt("output_tokens", lastUsage?.outputTokens ?: 0)
                                )
                            }
                            null
                        }
                        "error" -> {
                            val msg = obj.optJSONObject("error")?.optString("message") ?: payload.take(500)
                            throw IllegalStateException("Anthropic stream error: $msg")
                        }
                        else -> null
                    }
                }.getOrElse { e ->
                    if (e is IllegalStateException) throw e else null
                }
                if (token != null) emit(token)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Pre-warm: send a 1-token request with the system prompt to write the cache
     * BEFORE fanning out concurrent calls. Per Anthropic docs:
     *   "A cache entry only becomes available to be read after the very first
     *    API response begins. If your main CLI spins up all four sub-agents
     *    simultaneously, none of them will find an existing cache."
     */
    suspend fun preWarm(): Usage? {
        // Collect (and discard) the stream; lastUsage will be populated.
        run("Reply 'ok'.", null).collect { /* discard */ }
        return lastUsage
    }

    companion object {
        const val CRED_KEY = "anthropic.key"
        const val DEFAULT_MODEL = "claude-sonnet-4-6-20250929"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
