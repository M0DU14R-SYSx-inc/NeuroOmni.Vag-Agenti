package com.horizons.audio

import android.content.Context
import com.horizons.termux.TermuxBridge

/**
 * Thin STT client that shells out to Termux's `termux-speech-to-text` (from
 * the termux-api add-on). Captures fresh from the mic via Android's system
 * speech recognizer (Google Speech) — no AudioRecorder, no Moonshine, no
 * on-device ONNX. Online recognition.
 *
 * Returns the recognized text on stdout. Returns Result.failure with the
 * shell-error message if the recognizer was cancelled / no speech detected.
 *
 * Same prerequisites as TermuxTtsClient (Termux + termux-api + Termux:API
 * app + allow-external-apps).
 */
class TermuxSttClient(context: Context) {

    private val bridge = TermuxBridge(context)

    suspend fun listen(timeoutMs: Long = 30_000): Result<String> {
        return bridge.run("termux-speech-to-text", timeoutMs = timeoutMs).mapCatching { out ->
            val text = out.stdout.trim()
            if (text.isEmpty()) {
                throw IllegalStateException(
                    "termux-speech-to-text returned empty. stderr=${out.stderr.take(200)}"
                )
            }
            text
        }
    }
}
