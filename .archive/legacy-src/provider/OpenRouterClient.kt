package com.horizons.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenRouter chat completions client with built-in `models` fallback chain.
 * Streams tokens via SSE. Used as the cloud failover when the NPU stalls or
 * the operator explicitly picks a cloud backend.
 */
class OpenRouterClient(
    private val apiKey: String,
    private val primaryModel: String = DEFAULT_PRIMARY,
    private val fallbackModels: List<String> = DEFAULT_FALLBACKS
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun stream(prompt: String): Flow<String> = flow {
        val body = JSONObject().apply {
            put("model", primaryModel)
            put("models", JSONArray(listOf(primaryModel) + fallbackModels))
            put("stream", true)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti")
            .header("X-Title", "Horizons")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IllegalStateException("OpenRouter HTTP ${resp.code}: $err")
            }
            val source = resp.body?.source() ?: throw IllegalStateException("OpenRouter: empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") break
                val token = runCatching {
                    JSONObject(payload)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                        ?.takeIf { it.isNotEmpty() }
                }.getOrNull()
                if (token != null) emit(token)
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_PRIMARY = "qwen/qwen-2.5-72b-instruct"
        val DEFAULT_FALLBACKS = listOf(
            "anthropic/claude-3.5-sonnet",
            "google/gemini-2.0-flash-001"
        )
    }
}
