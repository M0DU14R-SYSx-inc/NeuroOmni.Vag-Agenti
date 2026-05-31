package com.neuroomni.horizons.model

import android.content.Context
import android.util.Log
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.GenerationConfig
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Real on-device [EdgeModel]: OmniNeural-4B on the Hexagon NPU via the Nexa Android
 * SDK (`ai.nexa:core`, package `com.nexa.sdk`). Compiled only into the
 * `-PnexaEnabled=true` build (src/nexa/java), so the default/CI build never references
 * the SDK.
 *
 * Constructed reflectively by [EdgeModelFactory] with (context, token, modelPath). The
 * SDK runtime itself takes no token — the Nexa key is for *downloading* the model, not
 * running it — so [token] is retained only to keep the reflective constructor stable.
 */
class NexaOmniNeuralEdgeModel(
    private val context: Context,
    @Suppress("unused") private val token: String,
    private val modelPath: String,
) : EdgeModel {

    private var vlm: VlmWrapper? = null

    override suspend fun initialize(): Result<Unit> = runCatching {
        // Init the SDK runtime: extracts the QNN/HTP native assets and registers the
        // NPU plugin. The completion callback is optional; init blocks until the
        // assets are staged, so we proceed straight to loading the model.
        NexaSdk.getInstance().init(context)

        val wrapper = VlmWrapper(
            VlmCreateInput(
                model_name = "omni-neural",
                model_path = modelPath,
                mmproj_path = "",
                config = ModelConfig(),
                plugin_id = NexaSdk.PLUGIN_ID_NPU,
                device_id = "",
            ),
        )
        wrapper.create() // suspend: loads weights onto the NPU
        vlm = wrapper
        Log.i(TAG, "OmniNeural-4B initialized on NPU")
    }

    override fun generateStream(prompt: String): Flow<String> {
        val wrapper = vlm ?: error("NexaOmniNeuralEdgeModel not initialized")
        return wrapper.generateStream(prompt, GenerationConfig())
            .map { it.text ?: "" }
            .flowOn(Dispatchers.Default)
    }

    override fun release() {
        runCatching { vlm?.destroy() }
        vlm = null
    }

    private companion object {
        const val TAG = "NexaOmniNeural"
    }
}
