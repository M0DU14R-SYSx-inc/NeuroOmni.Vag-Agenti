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
 * Google AI Studio Gemini client (the `x-goog-api-key` auth path, not Vertex).
 *
 * Streams tokens via :streamGenerateContent. Google's stream is NOT pure SSE —
 * it's a chunked JSON array (top-level `[ {chunk}, {chunk}, ... ]`) where each
 * element is a partial GenerateContentResponse. We scan for balanced JSON
 * objects at depth 1 inside the array and parse them as they arrive.
 */
class AIStudioGeminiClient(
    private val credentials: CredentialStore,
    private val model: String = DEFAULT_MODEL
) : Tool {
    override val id: String = "aistudio.gemini:$model"
    override val displayName: String = "AI Studio Gemini ($model)"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun run(prompt: String, imagePath: String?): Flow<String> = stream(prompt)

    fun stream(prompt: String): Flow<String> = flow {
        val apiKey = credentials.get("aistudio.key")
            ?: throw IllegalStateException("AIStudioGemini: missing aistudio.key in CredentialStore")

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 2048)
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent")
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IllegalStateException("AIStudioGemini HTTP ${resp.code}: $err")
            }
            val source = resp.body?.source() ?: throw IllegalStateException("AIStudioGemini: empty body")

            // Google streams a top-level JSON array, with chunks appended as the
            // model generates. Scan byte-by-byte, tracking brace depth + string
            // state, and parse each top-level array element as it closes.
            val buf = StringBuilder()
            var depth = 0
            var inString = false
            var escape = false
            var started = false

            while (!source.exhausted()) {
                val cInt = source.readUtf8CodePoint()
                val c = cInt.toChar()

                if (!started) {
                    if (c == '[') { started = true; continue }
                    if (c.isWhitespace()) continue
                    // Not an array — bail.
                    if (c == '{') { started = true; buf.append(c); depth = 1; continue }
                    continue
                }

                if (depth == 0) {
                    if (c == '{') {
                        depth = 1
                        buf.append(c)
                    }
                    // skip commas / whitespace / closing ']'
                    continue
                }

                buf.append(c)
                if (escape) { escape = false; continue }
                if (c == '\\' && inString) { escape = true; continue }
                if (c == '"') { inString = !inString; continue }
                if (inString) continue

                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val chunk = buf.toString()
                            buf.setLength(0)
                            val text = runCatching {
                                JSONObject(chunk)
                                    .optJSONArray("candidates")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("content")
                                    ?.optJSONArray("parts")
                                    ?.let { parts ->
                                        val sb = StringBuilder()
                                        for (i in 0 until parts.length()) {
                                            val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                                            sb.append(t)
                                        }
                                        sb.toString()
                                    }
                                    ?.takeIf { it.isNotEmpty() }
                            }.getOrNull()
                            if (text != null) emit(text)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash-001"
    }
}
