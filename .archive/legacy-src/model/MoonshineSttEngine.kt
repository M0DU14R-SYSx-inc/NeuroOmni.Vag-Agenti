package com.horizons.model

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moonshine on-device STT via sherpa-onnx OfflineRecognizer.
 *
 * Replaces the hand-rolled ORT seq2seq loop (fake empty past_key_values,
 * input-name guessing, full-sequence re-decode each step) with sherpa's
 * proper cached/uncached Moonshine decode. sherpa-onnx statically packages
 * its own ORT (1.24.3) whose CPU EP implements ConvInteger, so the int8
 * model loads — the old ORT-Android 1.22.0 Java path threw
 * ORT_NOT_IMPLEMENTED on it.
 *
 * EP: CPU only — FORCED EXCLUSION stands. provider="cpu" keeps sherpa off
 * NNAPI/HTP so the NPU stays exclusively Nexa OmniNeural-4B's.
 */
class MoonshineSttEngine(
    private val context: Context,
    private val modelDir: String
) {
    private var recognizer: OfflineRecognizer? = null

    val isLoaded: Boolean get() = recognizer != null

    suspend fun load() = withContext(Dispatchers.Default) {
        val dir = File(modelDir)
        val tokens = File(dir, "tokens.txt")
        require(tokens.isFile) { "Moonshine tokens.txt missing in $dir" }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    preprocessor = MoonshineDownloader.pickOnnx(dir, "preprocess").absolutePath,
                    encoder = MoonshineDownloader.pickOnnx(dir, "encode").absolutePath,
                    uncachedDecoder = MoonshineDownloader.pickOnnx(dir, "uncached_decode").absolutePath,
                    cachedDecoder = MoonshineDownloader.pickOnnx(dir, "cached_decode").absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                provider = "cpu",
            ),
        )
        recognizer = OfflineRecognizer(assetManager = null, config = config)
        Log.i(TAG, "Moonshine (sherpa) loaded from $dir")
    }

    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int = 16000): String =
        withContext(Dispatchers.Default) {
            val rec = recognizer ?: return@withContext "[moonshine error: not loaded]"
            try {
                val samples = FloatArray(pcm16.size) { pcm16[it].toFloat() / 32768f }
                val stream = rec.createStream()
                try {
                    stream.acceptWaveform(samples, sampleRate)
                    rec.decode(stream)
                    rec.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "transcribe failed", e)
                "[moonshine error: ${e.javaClass.simpleName}: ${e.message}]"
            }
        }

    fun release() {
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private companion object { const val TAG = "MoonshineStt" }
}
