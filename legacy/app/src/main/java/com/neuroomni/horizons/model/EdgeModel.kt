package com.neuroomni.horizons.model

import kotlinx.coroutines.flow.Flow

/**
 * The on-device language model slot. This is the license-proofing seam: the app talks
 * to this interface, and OmniNeural-4B (via the Nexa SDK) is just one implementation.
 * A [StubEdgeModel] keeps CI and emulators running with no model file and no token.
 */
interface EdgeModel {
    /** Load weights / acquire the NPU runtime. Safe to call more than once. */
    suspend fun initialize(): Result<Unit>

    /** Stream the model's response to [prompt] token-by-token. */
    fun generateStream(prompt: String): Flow<String>

    /** Release the runtime and any native resources. */
    fun release()
}
