package com.horizons.provider

import com.horizons.orchestrator.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OpenAICompatibleClient(
    private val backend: NamedBackend,
    private val credentials: CredentialStore
) : Tool {
    override val id: String = backend.id
    override val displayName: String = backend.displayName

    override fun run(prompt: String, imagePath: String?): Flow<String> = flow {
        TODO("Phase 6: build CanonicalRequest -> WireNormalizer.fromCanonicalRequest(backend.wire, ...) -> POST baseUrl with credentials.get(backend.credentialKey). Stream chunks through WireNormalizer.normalizeStreamChunk and emit.")
    }
}
