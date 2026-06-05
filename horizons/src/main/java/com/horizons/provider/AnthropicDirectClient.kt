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
 * Direct Anthropic Messages API client.
 *
 * Streams tokens via Anthropic's SSE event stream. The API key is fetched
 * lazily on each call from [CredentialStore] under the `anthropic.key`
 * convention (see [CredentialStore]).
 *
 * Only text deltas are emitted — `content_block_delta` events with a
 * `delta.type == "text_delta"`. All other event types (message_start,
 * content_block_start, content_block_stop, message_delta, message_stop,
 * ping, error) are observed but produce no downstream tokens; an `error`
 * event payload is surfaced as an [IllegalStateException].
 */
class AnthropicDirectClient(
    private val credentials: CredentialStore,
    private val model: String = DEFAULT_MODEL,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS
) : Tool {
    override val id: String = "anthropic-direct"
    override val displayName: String = "Anthropic (direct)"

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

            // Anthropic SSE frames look like:
            //   event: content_block_delta
            //   data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
            //
            // We only need the `data:` line — the `type` field inside the JSON is
            // authoritative, so the `event:` line is ignored.
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                if (!line.startsWith("data:")) continue
                val payload = line.substringAfter("data:").trim()
                if (payload.isEmpty()) continue

                val token = runCatching {
                    val obj = JSONObject(payload)
                    when (obj.optString("type")) {
                        "content_block_delta" -> {
                            val delta = obj.optJSONObject("delta")
                            if (delta?.optString("type") == "text_delta") {
                                delta.optString("text").takeIf { it.isNotEmpty() }
                            } else null
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

    companion object {
        const val CRED_KEY = "anthropic.key"
        const val DEFAULT_MODEL = "claude-sonnet-4-6-20250929"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
