package com.horizons.model

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moonshine on-device STT using ONNX Runtime Android.
 *
 * Model: onnx-community/moonshine-base-ONNX, int8 variant.
 * Encoder takes a mel-spectrogram of variable length, decoder generates tokens.
 *
 * Phase 2: load sessions + raw transcribe wired. Greedy decode loop + mel
 * preprocessing are best-effort placeholders awaiting on-device tuning.
 */
class MoonshineSttEngine(
    private val context: Context,
    private val modelDir: String
) {
    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null

    val isLoaded: Boolean get() = encoder != null && decoder != null

    suspend fun load() = withContext(Dispatchers.Default) {
        val encoderFile = File(modelDir, "onnx/encoder_model_int8.onnx")
        val decoderFile = File(modelDir, "onnx/decoder_model_merged_int8.onnx")
        require(encoderFile.isFile) { "Moonshine encoder missing at $encoderFile" }
        require(decoderFile.isFile) { "Moonshine decoder missing at $decoderFile" }

        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        encoder = env!!.createSession(encoderFile.absolutePath, opts)
        decoder = env!!.createSession(decoderFile.absolutePath, opts)
        Log.i(TAG, "Moonshine loaded: encoder=${encoderFile.length()}B decoder=${decoderFile.length()}B")
    }

    /**
     * Transcribe a PCM16 mono audio buffer.
     * Phase 2 placeholder — needs proper mel preprocessing + greedy/beam decode.
     */
    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int = 16000): String =
        withContext(Dispatchers.Default) {
            require(isLoaded) { "MoonshineSttEngine not loaded" }
            // TODO Phase 2: full pipeline:
            //   1. Convert PCM16 to float32 normalized -1..1
            //   2. Compute log-mel spectrogram (80 mels, 16k SR, hop 160)
            //   3. encoder.run(inputs = {input_values}) -> last_hidden_state
            //   4. greedy decode: feed start token, loop until EOS or max_new_tokens
            //   5. detokenize via tokenizer.json BPE
            "[moonshine: ${pcm16.size} samples @ ${sampleRate}Hz — decode not yet wired]"
        }

    fun release() {
        runCatching { encoder?.close() }
        runCatching { decoder?.close() }
        encoder = null; decoder = null
    }

    private companion object { const val TAG = "MoonshineStt" }
}
