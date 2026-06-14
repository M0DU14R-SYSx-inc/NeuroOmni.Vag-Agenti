package com.horizons.core.nexa

/**
 * Opaque description of a Nexa-runnable model. Callers describe what they
 * want (name, path, plugin, modality); [NexaModelLoader] picks the right
 * SDK wrapper internally. No backend awareness leaks out.
 *
 * The 9-boundary stack pins what's expected on this device:
 *  - [PLUGIN_NPU]     → OmniNeural-4B (VLM) + Parakeet TDT/ASR — Hexagon
 *  - [PLUGIN_CPU_GPU] → Gemma-4-E4B-IT (VLM) — Adreno GPU
 */
data class NexaModelSpec(
    val name: String,
    val modelPath: String,
    val pluginId: String,
    val modality: Modality = Modality.LLM,
    val deviceId: String = "",
    val mmprojPath: String = "",
    val maxTokens: Int = 2048,
    val enableThinking: Boolean = false,
) {
    enum class Modality { LLM, ASR }

    companion object {
        const val PLUGIN_NPU = "npu"
        const val PLUGIN_CPU_GPU = "cpu_gpu"
        const val DEVICE_GPU = "dev0"
    }
}
