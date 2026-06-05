package com.horizons.provider

import com.horizons.orchestrator.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

enum class VertexPublisher { ANTHROPIC, GOOGLE }

/**
 * Vertex AI client supporting Claude (publisher: anthropic) and Gemini
 * (publisher: google) endpoints via service-account auth.
 *
 * Auth flow:
 *  1. Read service account JSON from CredentialStore key `vertex.service_account_json`.
 *  2. Build a JWT (RS256) asserting iss=client_email, scope=cloud-platform,
 *     aud=oauth2.googleapis.com/token.
 *  3. Exchange the JWT at https://oauth2.googleapis.com/token for an access
 *     token. Cache the token until 60s before expiry.
 *  4. Send streaming prediction requests with `Authorization: Bearer <token>`.
 */
class VertexClient(
    val publisher: VertexPublisher,
    val model: String,
    val project: String,
    val location: String,
    val credentials: CredentialStore
) : Tool {

    override val id: String =
        "vertex-${publisher.name.lowercase()}-$model"

    override val displayName: String = when (publisher) {
        VertexPublisher.ANTHROPIC -> "Vertex Claude ($model)"
        VertexPublisher.GOOGLE -> "Vertex Gemini ($model)"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiresAtMs: Long = 0L

    override fun run(prompt: String, imagePath: String?): Flow<String> = flow {
        val token = getAccessToken()
        when (publisher) {
            VertexPublisher.ANTHROPIC -> streamAnthropic(prompt, token).collect { emit(it) }
            VertexPublisher.GOOGLE -> streamGoogle(prompt, token).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    // ---------- Anthropic on Vertex ----------

    private fun streamAnthropic(prompt: String, token: String): Flow<String> = flow {
        val url = "https://$location-aiplatform.googleapis.com/v1/projects/$project/" +
            "locations/$location/publishers/anthropic/models/$model:streamRawPredict"
        val body = JSONObject().apply {
            put("anthropic_version", "vertex-2023-10-16")
            put("stream", true)
            put("max_tokens", 2048)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IllegalStateException("Vertex/Anthropic HTTP ${resp.code}: $err")
            }
            val source = resp.body?.source()
                ?: throw IllegalStateException("Vertex/Anthropic: empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ").trim()
                if (payload == "[DONE]") break
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                // Anthropic SSE: content_block_delta -> delta.text
                val type = obj.optString("type")
                if (type == "content_block_delta") {
                    val text = obj.optJSONObject("delta")?.optString("text").orEmpty()
                    if (text.isNotEmpty()) emit(text)
                } else if (type == "message_stop") {
                    break
                }
            }
        }
    }

    // ---------- Gemini on Vertex ----------

    private fun streamGoogle(prompt: String, token: String): Flow<String> = flow {
        val url = "https://$location-aiplatform.googleapis.com/v1/projects/$project/" +
            "locations/$location/publishers/google/models/$model:streamGenerateContent"
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IllegalStateException("Vertex/Google HTTP ${resp.code}: $err")
            }
            // streamGenerateContent returns a JSON array streamed as ndjson-ish
            // chunks. The full body is a valid JSON array; many proxies emit
            // chunked text that, when fully read, parses as one JSONArray.
            // We accumulate and try to parse line-by-line objects first, then
            // fall back to whole-body array parse.
            val full = resp.body?.string().orEmpty()
            val emitted = mutableListOf<String>()
            val parsedArray = runCatching { JSONArray(full) }.getOrNull()
            if (parsedArray != null) {
                for (i in 0 until parsedArray.length()) {
                    val obj = parsedArray.optJSONObject(i) ?: continue
                    extractGeminiText(obj)?.let { emitted += it }
                }
            } else {
                // Fall back: scan for top-level JSON objects by brace matching.
                var depth = 0
                val cur = StringBuilder()
                for (ch in full) {
                    if (ch == '{') { depth++; cur.append(ch) }
                    else if (ch == '}') {
                        depth--; cur.append(ch)
                        if (depth == 0 && cur.isNotEmpty()) {
                            runCatching { JSONObject(cur.toString()) }.getOrNull()
                                ?.let { extractGeminiText(it)?.let { t -> emitted += t } }
                            cur.clear()
                        }
                    } else if (depth > 0) cur.append(ch)
                }
            }
            for (t in emitted) if (t.isNotEmpty()) emit(t)
        }
    }

    private fun extractGeminiText(obj: JSONObject): String? {
        val candidates = obj.optJSONArray("candidates") ?: return null
        val sb = StringBuilder()
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)
                ?.optJSONObject("content")
                ?.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val t = parts.optJSONObject(j)?.optString("text").orEmpty()
                if (t.isNotEmpty()) sb.append(t)
            }
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    // ---------- OAuth2 via service-account JWT ----------

    @Synchronized
    private fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now < cachedTokenExpiresAtMs) return cached

        val saJson = credentials.get("vertex.service_account_json")
            ?: throw IllegalStateException("Vertex: missing credential 'vertex.service_account_json'")
        val sa = JSONObject(saJson)
        val clientEmail = sa.optString("client_email").ifEmpty {
            throw IllegalStateException("Vertex: service account JSON missing client_email")
        }
        val privateKeyPem = sa.optString("private_key").ifEmpty {
            throw IllegalStateException("Vertex: service account JSON missing private_key")
        }

        val jwt = buildSignedJwt(clientEmail, privateKeyPem)

        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()
        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form)
            .build()
        val tokenJson = http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Vertex token exchange HTTP ${resp.code}: ${text.take(500)}")
            }
            JSONObject(text)
        }
        val accessToken = tokenJson.optString("access_token").ifEmpty {
            throw IllegalStateException("Vertex token exchange: no access_token in response")
        }
        val expiresInSec = tokenJson.optInt("expires_in", 3600)
        // Refresh 60s before the server says it expires.
        cachedTokenExpiresAtMs = now + (expiresInSec - 60).coerceAtLeast(30) * 1000L
        cachedToken = accessToken
        return accessToken
    }

    private fun buildSignedJwt(clientEmail: String, privateKeyPem: String): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val header = JSONObject().apply {
            put("alg", "RS256"); put("typ", "JWT")
        }.toString()
        val claims = JSONObject().apply {
            put("iss", clientEmail)
            put("scope", "https://www.googleapis.com/auth/cloud-platform")
            put("aud", "https://oauth2.googleapis.com/token")
            put("iat", nowSec)
            put("exp", nowSec + 3600)
        }.toString()

        val b64 = Base64.getUrlEncoder().withoutPadding()
        val signingInput = b64.encodeToString(header.toByteArray(Charsets.UTF_8)) +
            "." + b64.encodeToString(claims.toByteArray(Charsets.UTF_8))

        val privateKey = parsePkcs8PrivateKey(privateKeyPem)
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(signingInput.toByteArray(Charsets.UTF_8))
        val sig = signer.sign()
        return signingInput + "." + b64.encodeToString(sig)
    }

    private fun parsePkcs8PrivateKey(pem: String): java.security.PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(cleaned)
        val spec = PKCS8EncodedKeySpec(der)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
