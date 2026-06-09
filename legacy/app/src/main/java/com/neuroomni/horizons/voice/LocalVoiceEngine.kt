package com.neuroomni.horizons.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * On-device TTS for agent output (Horizons UI Spec §5, Architecture §4 Stack E).
 *
 * Binds a specific TTS engine package via the [TextToSpeech] 3-arg constructor,
 * defaulting to VoxSherpa (which ships Kokoro-82M as a system TTS engine). If that
 * engine isn't installed, the 3-arg constructor reports failure in [onInit] and we
 * transparently fall back to the system default engine — so the speak button never
 * fails silently (Checkpoint 4).
 *
 * NOTE: Kokoro-82M synthesis latency on-device can be high (neural vocoder on the
 * Adreno GPU / CPU). [speed] and [pitch] are exposed so callers can trade quality
 * for responsiveness; the system fallback engine is typically much faster.
 */
class LocalVoiceEngine(
    private val context: Context,
    private val enginePackage: String = DEFAULT_ENGINE_PACKAGE,
    private val speed: Float = 1.0f,
    private val pitch: Float = 1.0f,
    private val onReady: ((usingFallback: Boolean) -> Unit)? = null,
) {

    private var tts: TextToSpeech? = null

    /** True once the engine has finished initializing and is usable. */
    @Volatile
    var isReady: Boolean = false
        private set

    /** True when we fell back to the system default engine (VoxSherpa unavailable). */
    @Volatile
    var usingFallback: Boolean = false
        private set

    private val utteranceCounter = AtomicLong(0)

    private val initListener = OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            configure()
        } else {
            // The requested engine (VoxSherpa) failed to bind — fall back to the
            // system default engine rather than failing silently.
            Log.w(TAG, "Engine '$enginePackage' init failed (status=$status); using system default")
            tts?.shutdown()
            usingFallback = true
            tts = TextToSpeech(context, fallbackInitListener) // 2-arg = system default
        }
    }

    private val fallbackInitListener = OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            configure()
        } else {
            Log.e(TAG, "System default TTS also failed to initialize (status=$status)")
            isReady = false
        }
    }

    /** Bind the engine. Call once (e.g. on first composition). */
    fun initialize() {
        if (tts != null) return
        // 3-arg constructor pins a specific engine package.
        tts = TextToSpeech(context, initListener, enginePackage)
    }

    private fun configure() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "US English not fully supported by the active TTS engine")
        }
        engine.setSpeechRate(speed)
        engine.setPitch(pitch)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("deprecated in API level 21")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS error on $utteranceId (code=$errorCode)")
            }
        })
        isReady = true
        onReady?.invoke(usingFallback)
    }

    /**
     * Queue [agentOutputText] for speech. Uses QUEUE_ADD with a unique utterance id so
     * successive agent messages play back-to-back instead of cutting each other off.
     */
    fun speak(agentOutputText: String) {
        val engine = tts
        if (engine == null || !isReady || agentOutputText.isBlank()) return
        val utteranceId = "horizons-" + utteranceCounter.incrementAndGet()
        engine.speak(agentOutputText, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /** Stop any in-progress/queued speech without tearing down the engine. */
    fun stop() {
        tts?.stop()
    }

    /** Release the engine. Call when the owning scope is disposed. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "LocalVoiceEngine"

        /** VoxSherpa packages Kokoro-82M as an Android system TTS engine. */
        const val DEFAULT_ENGINE_PACKAGE = "com.CodeBySonu.VoxSherpa"
    }
}
