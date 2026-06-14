package com.horizons.core.nexa

import android.content.Context
import com.nexa.sdk.AsrWrapper
import com.nexa.sdk.NexaSdk
import com.nexa.sdk.VlmWrapper
import com.nexa.sdk.bean.AsrCreateInput
import com.nexa.sdk.bean.ModelConfig
import com.nexa.sdk.bean.VlmCreateInput
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for loading any Nexa-runnable model. Callers receive a
 * [NexaEngine] — they never see VlmWrapper, AsrWrapper, or any SDK type.
 *
 * Branching on [NexaModelSpec.modality] is an internal implementation detail
 * that lives here and nowhere else.
 */
object NexaModelLoader {

    private val sdkStarted = AtomicBoolean(false)
    private val sdkDone = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Idempotent. Safe to call concurrently — only one NexaSdk.init() fires. */
    suspend fun ensureSdkInit(context: Context) {
        if (sdkDone.isCompleted) return
        if (sdkStarted.compareAndSet(false, true)) {
            NexaSdk.getInstance().init(context, object : NexaSdk.InitCallback {
                override fun onSuccess() { sdkDone.complete(Unit) }
                override fun onFailure(reason: String) {
                    sdkDone.completeExceptionally(
                        IllegalStateException("NexaSdk init failed: $reason")
                    )
                }
            })
        }
        sdkDone.await()
    }

    /** Suspends until the model is resident or throws on failure. */
    suspend fun load(context: Context, spec: NexaModelSpec): NexaEngine {
        ensureSdkInit(context)
        return when (spec.modality) {
            NexaModelSpec.Modality.ASR -> loadAsr(context, spec)
            NexaModelSpec.Modality.LLM -> loadVlm(context, spec)
        }
    }

    // ── ASR path — AsrWrapper (Parakeet TDT / Hexagon NPU) ──────────────────

    private suspend fun loadAsr(context: Context, spec: NexaModelSpec): NexaEngine {
        val modelDir = File(spec.modelPath).parent ?: spec.modelPath
        val config = ModelConfig(
            max_tokens = spec.maxTokens,
            npu_lib_folder_path = context.applicationInfo.nativeLibraryDir,
            npu_model_folder_path = modelDir,
        )
        val input = AsrCreateInput(
            model_name = spec.name,
            model_path = spec.modelPath,
            config = config,
            plugin_id = spec.pluginId,
        )
        val wrapper = AsrWrapper.builder()
            .asrCreateInput(input)
            .dispatcher(Dispatchers.IO)
            .build()
            .getOrThrow()
        return AsrEngineImpl(spec, wrapper)
    }

    // ── VLM path — VlmWrapper (OmniNeural NPU / Gemma GPU) ──────────────────

    private suspend fun loadVlm(context: Context, spec: NexaModelSpec): NexaEngine {
        val isNpu = spec.pluginId == NexaModelSpec.PLUGIN_NPU
        val modelDir = File(spec.modelPath).parent ?: spec.modelPath
        val config = ModelConfig(
            max_tokens = spec.maxTokens,
            enable_thinking = spec.enableThinking,
            npu_lib_folder_path = if (isNpu) context.applicationInfo.nativeLibraryDir else "",
            npu_model_folder_path = if (isNpu) modelDir else "",
        )
        val input = VlmCreateInput(
            model_name = spec.name,
            model_path = spec.modelPath,
            mmproj_path = spec.mmprojPath,
            config = config,
            plugin_id = spec.pluginId,
            device_id = spec.deviceId,
        )
        val wrapper = VlmWrapper.builder()
            .vlmCreateInput(input)
            .dispatcher(Dispatchers.IO)
            .build()
            .getOrThrow()
        return VlmEngineImpl(spec, wrapper)
    }
}
