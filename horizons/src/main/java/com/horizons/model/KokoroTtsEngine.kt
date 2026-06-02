package com.horizons.model

class KokoroTtsEngine {
    @Volatile private var speaking = false

    suspend fun load(voice: String = "am_adam") {
        TODO("Phase 4: load Kokoro ONNX + voice pack (default am_adam) via onnxruntime-android with QNN GPU execution provider.")
    }

    suspend fun speak(text: String, pitch: Float = 1f, rate: Float = 1f) {
        TODO("Phase 4: synthesize, stream PCM to AudioTrack. Honor stopRequested for barge-in.")
    }

    fun stop() {
        TODO("Phase 4: tap-to-interrupt — flush AudioTrack and abort.")
    }
}
