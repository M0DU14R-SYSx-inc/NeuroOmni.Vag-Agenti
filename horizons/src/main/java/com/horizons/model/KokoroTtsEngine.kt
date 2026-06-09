package com.horizons.model

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kokoro on-device TTS using ONNX Runtime Android.
 *
 * Model: onnx-community/Kokoro-82M-v1.0-ONNX, q8f16 variant.
 * Default voice: am_adam (operator pick).
 *
 * Phase 4: load session + voice loading wired. Phonemize + decode loop are
 * best-effort placeholders awaiting on-device tuning.
 */
class KokoroTtsEngine(
    private val context: Context,
    private val modelDir: String,
    private val voiceName: String = KokoroDownloader.DEFAULT_VOICE
) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var voiceEmbedding: FloatArray? = null
    @Volatile private var stopRequested = false
    private var audioTrack: AudioTrack? = null

    val isLoaded: Boolean get() = session != null && voiceEmbedding != null

    suspend fun load() = withContext(Dispatchers.Default) {
        val modelFile = File(modelDir, "onnx/model_q8f16.onnx")
        val voiceFile = File(modelDir, "voices/$voiceName.bin")
        require(modelFile.isFile) { "Kokoro model missing at $modelFile" }
        require(voiceFile.isFile) { "Kokoro voice $voiceName missing at $voiceFile" }

        env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Phase 4: enable QNN EP for Adreno GPU acceleration once ORT-QNN package
            // is in the deps. addExecutionProvider_QNN(...) — until then CPU is fine.
        }
        session = env!!.createSession(modelFile.absolutePath, opts)

        // Voice files are flat float32 arrays of style embeddings.
        val bytes = voiceFile.readBytes()
        val floats = FloatArray(bytes.size / 4)
        java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer().get(floats)
        voiceEmbedding = floats
        Log.i(TAG, "Kokoro loaded: model=${modelFile.length()}B voice=$voiceName (${floats.size} floats)")
    }

    suspend fun speak(text: String, pitch: Float = 1f, rate: Float = 1f) =
        withContext(Dispatchers.Default) {
            require(isLoaded) { "KokoroTtsEngine not loaded" }
            stopRequested = false
            // TODO Phase 4: full pipeline:
            //   1. Phonemize text (espeak-ng wrapper or G2P model)
            //   2. Convert phonemes to token IDs via tokenizer.json
            //   3. session.run(inputs = {input_ids, style: voiceEmbedding, speed})
            //   4. Get float32 audio @ 24kHz, stream chunks to AudioTrack
            //   5. Honor stopRequested between chunks (barge-in)
            Log.i(TAG, "[kokoro: '$text' — synth not yet wired]")
        }

    fun stop() {
        stopRequested = true
        runCatching { audioTrack?.pause(); audioTrack?.flush() }
    }

    fun release() {
        stop()
        runCatching { audioTrack?.release() }; audioTrack = null
        runCatching { session?.close() }; session = null
        voiceEmbedding = null
    }

    @Suppress("unused")
    private fun newAudioTrack(sampleRate: Int = 24_000): AudioTrack {
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(bufSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().also { audioTrack = it; it.play() }
    }

    private companion object { const val TAG = "KokoroTts" }
}
