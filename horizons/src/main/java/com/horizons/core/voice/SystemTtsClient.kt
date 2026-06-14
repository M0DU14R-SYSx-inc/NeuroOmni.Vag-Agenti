package com.horizons.core.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Boundary 9 — VoxSherpa (com.CodeBySonu.VoxSherpa) via Android TextToSpeech API.
 * Call init() once before use; call shutdown() when done.
 */
class SystemTtsClient(private val context: Context) {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    suspend fun init(): Result<Unit> = suspendCancellableCoroutine { cont ->
        val engine = TextToSpeech(context, { status ->
            ready = status == TextToSpeech.SUCCESS
            if (cont.isActive) {
                if (ready) cont.resume(Result.success(Unit))
                else cont.resume(Result.failure(IllegalStateException("VoxSherpa TTS init failed: status=$status")))
            }
        }, VOXSHERPA_PACKAGE)
        tts = engine
        cont.invokeOnCancellation { engine.shutdown() }
    }

    suspend fun speak(
        text: String,
        utteranceId: String = System.currentTimeMillis().toString()
    ): Result<Unit> {
        val engine = tts ?: return Result.failure(IllegalStateException("Not initialized — call init() first"))
        if (!ready) return Result.failure(IllegalStateException("TTS not ready"))
        if (text.isBlank()) return Result.success(Unit)
        return suspendCancellableCoroutine { cont ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Result.success(Unit))
                }
                @Deprecated("Deprecated in API 21")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive)
                        cont.resume(Result.failure(RuntimeException("TTS error utterance=$id")))
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val VOXSHERPA_PACKAGE = "com.CodeBySonu.VoxSherpa"
    }
}
