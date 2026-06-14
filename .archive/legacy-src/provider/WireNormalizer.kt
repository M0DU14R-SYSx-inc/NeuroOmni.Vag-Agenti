package com.horizons.provider

object WireNormalizer {
    data class CanonicalRequest(val system: String?, val messages: List<Msg>, val tools: List<String>)
    data class Msg(val role: String, val content: String)

    fun toCanonical(provider: Wire, body: String): CanonicalRequest {
        TODO("Phase 6: detect+normalize. Polar-style gateway shape: Anthropic/OpenAI Chat/OpenAI Responses/Google generateContent -> CanonicalRequest.")
    }

    fun fromCanonicalRequest(provider: Wire, req: CanonicalRequest): String {
        TODO("Phase 6: serialize CanonicalRequest into the provider's expected schema.")
    }

    fun normalizeStreamChunk(provider: Wire, chunk: String): String? {
        TODO("Phase 6: token-level extraction across SSE/NDJSON variants.")
    }
}
