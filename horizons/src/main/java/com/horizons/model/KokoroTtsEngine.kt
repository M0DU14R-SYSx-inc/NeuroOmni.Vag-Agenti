package com.horizons.model

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kokoro on-device TTS via sherpa-onnx OfflineTts.
 *
 * The blocker the previous engine died on — phonemization — is handled
 * inside sherpa: the model archive ships espeak-ng-data and the native
 * layer runs text -> phonemes -> token ids itself. We hand it a string
 * and get back float32 PCM @ 24 kHz.
 *
 * All 53 voices live in one voices.bin; the voice is just the speaker id
 * passed to generate(), so the Router picker switch is instant and does
 * not even require an engine reload (kept anyway for status surfacing).
 *
 * EP: CPU (provider="cpu") — FORCED EXCLUSION from NNAPI/HTP; the NPU
 * belongs to Nexa OmniNeural-4B. GPU offload, if ever needed, is a
 * sherpa rebuild question, not an app code change.
 */
class KokoroTtsEngine(
    private val context: Context,
    private val modelDir: String,
    private val voiceName: String = KokoroDownloader.DEFAULT_VOICE
) {
    private var tts: OfflineTts? = null
    @Volatile private var stopRequested = false
    private var audioTrack: AudioTrack? = null

    val isLoaded: Boolean get() = tts != null

    suspend fun load() = withContext(Dispatchers.Default) {
        val dir = File(modelDir)
        val model = File(dir, "model.onnx")
        require(model.isFile) { "Kokoro model missing at $model" }

        // Optional extras in the multi-lang archive; pass only what exists.
        val lexicon = listOf("lexicon-us-en.txt", "lexicon-zh.txt")
            .map { File(dir, it) }.filter { it.isFile }
            .joinToString(",") { it.absolutePath }
        val dictDir = File(dir, "dict").takeIf { it.isDirectory }?.absolutePath ?: ""

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = model.absolutePath,
                    voices = File(dir, "voices.bin").absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = File(dir, "espeak-ng-data").absolutePath,
                    lexicon = lexicon,
                    dictDir = dictDir,
                ),
                numThreads = 2,
                provider = "cpu",
            ),
        )
        tts = OfflineTts(assetManager = null, config = config)
        Log.i(TAG, "Kokoro (sherpa) loaded: voice=$voiceName sid=${KokoroDownloader.sidOf(voiceName)} " +
            "speakers=${tts!!.numSpeakers()} rate=${tts!!.sampleRate()}")
    }

    /**
     * Synthesize and play. Suspends until playback finishes or [stop] is
     * called. Synthesis is chunk-streamed: sherpa invokes the callback per
     * generated sentence-chunk and we feed AudioTrack as chunks arrive, so
     * first audio is heard long before the full text is synthesized.
     * Returning 0 from the callback aborts generation (barge-in).
     */
    suspend fun speak(text: String, pitch: Float = 1f, rate: Float = 1f) =
        withContext(Dispatchers.Default) {
            val engine = tts ?: error("KokoroTtsEngine not loaded")
            if (text.isBlank()) return@withContext
            stopRequested = false
            val track = newAudioTrack(engine.sampleRate())
            try {
                engine.generateWithCallback(
                    text = text,
                    sid = KokoroDownloader.sidOf(voiceName),
                    speed = rate,
                ) { samples ->
                    if (stopRequested) return@generateWithCallback 0
                    writePcm(track, samples)
                    if (stopRequested) 0 else 1
                }
                if (!stopRequested) {
                    // Drain: AudioTrack buffers ~hundreds of ms past the last write.
                    runCatching { track.stop() } // play out then stop
                }
            } finally {
                runCatching { track.release() }
                if (audioTrack === track) audioTrack = null
            }
        }

    private fun writePcm(track: AudioTrack, samples: FloatArray) {
        // float [-1,1] -> PCM16. Blocking write paces us to playback speed.
        val pcm = ShortArray(samples.size) {
            (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        var off = 0
        while (off < pcm.size && !stopRequested) {
            val n = track.write(pcm, off, pcm.size - off)
            if (n <= 0) break
            off += n
        }
    }

    fun stop() {
        stopRequested = true
        runCatching { audioTrack?.pause(); audioTrack?.flush() }
    }

    fun release() {
        stop()
        runCatching { audioTrack?.release() }; audioTrack = null
        runCatching { tts?.release() }; tts = null
    }

    private fun newAudioTrack(sampleRate: Int): AudioTrack {
        runCatching { audioTrack?.release() }
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
