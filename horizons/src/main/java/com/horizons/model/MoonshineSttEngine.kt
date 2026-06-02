package com.horizons.model

class MoonshineSttEngine {
    suspend fun load() {
        TODO("Phase 2: load Moonshine ONNX (encoder + decoder) via onnxruntime-android.")
    }

    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int): String {
        TODO("Phase 2: variable-length encoder, greedy decode; return cleaned text.")
    }
}
