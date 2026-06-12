package com.horizons.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wrapper around Android's system [TextToSpeech] engine. Works with whatever
 * TTS engine the operator has set as the system default — including
 * **VoxSherpa** (`com.CodeBySonu.VoxSherpa`), which registers as a TTS
 * service so every voice it ships becomes available system-wide.
 *
 * Why this exists alongside the in-app Kokoro engine: VoxSherpa-class voice
 * quality is a separate APK the operator installs and manages independently
 * (Play Store updates, voice picker, no need to ship 349 MB of weights with
 * Horizons). When VoxSherpa is the system default, our app uses it
 * automatically. When it isn't, fall back to in-app Kokoro.
 *
 * Lifecycle: lazy init on first speak() — init takes ~300 ms with no work
 * to amortize at app startup.
 */
class SystemTtsClient(private val context: Context) {

    private val readyDeferred = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech? = null

    /** True if a system TTS engine initialized successfully. */
    val isReady: Boolean get() = readyDeferred.isCompleted && (readyDeferred.getCompleted())

    /** Package name of the engine in use, or null. Useful in Diagnostics. */
    var engineInUse: String? = null
        private set

    /** Convenience: have we found VoxSherpa as the active engine? */
    val isVoxSherpa: Boolean get() = engineInUse == VOXSHERPA_PKG

    private fun ensureInit() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            engineInUse = tts?.defaultEngine
            Log.i(TAG, "system TTS init: status=$status engine=$engineInUse")
            readyDeferred.complete(ok)
        }
    }

    /**
     * Speaks [text] through the system engine. Suspends until playback
     * completes, the engine reports an error, or [stop] is called.
     */
    suspend fun speak(text: String): Result<Unit> {
        if (text.isBlank()) return Result.success(Unit)
        ensureInit()
        val ok = runCatching { readyDeferred.await() }.getOrDefault(false)
        if (!ok) return Result.failure(IllegalStateException("System TTS engine init failed"))
        val engine = tts ?: return Result.failure(IllegalStateException("TTS null after init"))

        engine.language = Locale.getDefault()
        val utteranceId = "horizons-${System.nanoTime()}"
        return runCatching {
            suspendCancellableCoroutine<Unit> { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (cont.isActive) cont.resume(Unit) // surface as completion, not crash
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
                val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (r != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume(Unit)
                }
                cont.invokeOnCancellation { runCatching { engine.stop() } }
            }
        }
    }

    fun stop() { runCatching { tts?.stop() } }

    fun release() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }

    companion object {
        private const val TAG = "SystemTtsClient"
        /** VoxSherpa-TTS package id — the operator-preferred engine. */
        const val VOXSHERPA_PKG = "com.CodeBySonu.VoxSherpa"

        /** True if VoxSherpa.apk is installed on the device. */
        fun isVoxSherpaInstalled(context: Context): Boolean = runCatching {
            context.packageManager.getPackageInfo(VOXSHERPA_PKG, 0)
            true
        }.getOrDefault(false)
    }
}
