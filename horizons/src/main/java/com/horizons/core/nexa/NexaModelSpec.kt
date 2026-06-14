package com.horizons.core.nexa

/**
 * Opaque description of a Nexa-runnable model. No type labels — the loader
 * does not branch on "is this a VLM / STT / TTS." It just hands the spec to
 * the Nexa SDK and gets a handle back.
 *
 * The 9-boundary stack pins what's expected on this device:
 *
 *  - [PLUGIN_NPU] hosts OmniNeural-4B and Parakeet TDT/ASR (Hexagon).
 *  - [PLUGIN_CPU_GPU] hosts Gemma-4-E4B-IT (Adreno) and any GGUF fallback.
 *
 * Concurrent residency across plugins is an open question — see
 * GREENFIELD_PLAN.md §"Open questions".
 */
data class NexaModelSpec(
    val name: String,
    val modelPath: String,
    val pluginId: String,
    val deviceId: String = "",
    val mmprojPath: String = "",
    val maxTokens: Int = 2048,
    val enableThinking: Boolean = false,
) {
    companion object {
        const val PLUGIN_NPU = "npu"
        const val PLUGIN_CPU_GPU = "cpu_gpu"
        const val DEVICE_GPU = "dev0"
    }
}
