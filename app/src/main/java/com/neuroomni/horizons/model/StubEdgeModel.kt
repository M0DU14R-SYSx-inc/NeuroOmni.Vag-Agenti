package com.neuroomni.horizons.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A dependency-free [EdgeModel] that streams a canned response. Lets the Chat panel,
 * CI, and emulators run with no model weights and no NEXA_TOKEN. The real
 * OmniNeural-4B implementation plugs in behind the same interface.
 */
class StubEdgeModel : EdgeModel {

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun generateStream(prompt: String): Flow<String> = flow {
        val response =
            "[stub edge model] You said: \"$prompt\". " +
                "OmniNeural-4B on the Hexagon NPU plugs in behind this same EdgeModel " +
                "interface — this canned stream just proves the wiring end to end."
        // Emit word-by-word so the UI exercises real token-streaming behavior.
        for (word in response.split(" ")) {
            emit("$word ")
            delay(TOKEN_DELAY_MS)
        }
    }

    override fun release() {
        // Nothing to release for the stub.
    }

    private companion object {
        const val TOKEN_DELAY_MS = 35L
    }
}
