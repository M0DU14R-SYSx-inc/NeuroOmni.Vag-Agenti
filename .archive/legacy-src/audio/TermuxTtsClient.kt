package com.horizons.audio

import android.content.Context
import com.horizons.termux.TermuxBridge

/**
 * Thin TTS client that shells out to Termux's `termux-tts-speak` (from the
 * termux-api add-on). Speak is fire-and-forget on Android's system TTS engine —
 * zero on-device model overhead, zero Python, zero ONNX. The trade-off is
 * voice quality (system voice, not Kokoro am_adam).
 *
 * Requires on the device:
 *   - Termux installed (F-Droid build with RUN_COMMAND).
 *   - termux-api package: `pkg install termux-api`.
 *   - Termux:API companion app installed (Play / F-Droid).
 *   - `allow-external-apps=true` in ~/.termux/termux.properties.
 */
class TermuxTtsClient(context: Context) {

    private val bridge = TermuxBridge(context)

    suspend fun speak(text: String): Result<Unit> {
        if (text.isBlank()) return Result.success(Unit)
        // Single-quote shell-escape: replace ' with '"'"' then wrap in single quotes.
        val escaped = "'" + text.replace("'", "'\"'\"'") + "'"
        return bridge.run("termux-tts-speak $escaped", timeoutMs = 60_000)
            .map { Unit }
    }

    fun stop() {
        // No reliable abort path for termux-tts-speak from outside the process; the
        // next speak() call queues serially. Surfacing this as a no-op is honest.
    }
}
