package com.horizons.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Speaks chat replies aloud. Has two backends in priority order:
 *
 *   1. **System TTS** ([SystemTtsClient]) — when VoxSherpa (or any other
 *      Android TTS engine the operator has set as default) is installed,
 *      this is preferred. Lighter, externally updated, no model shipped.
 *   2. **In-app Kokoro** — fallback when no system engine is available.
 *
 * The selection happens once per speak() call: if VoxSherpa is installed,
 * route to system TTS; else use Kokoro (lazy-loaded on first use).
 */
class SpeakerPlayer(
    private val ttsSupplier: () -> com.horizons.model.KokoroTtsEngine?,
    private val ttsLazyLoad: suspend () -> Unit = {},
    private val systemTtsSupplier: () -> SystemTtsClient? = { null },
    private val preferSystemTts: () -> Boolean = { false },
) {
    sealed class State {
        object Idle : State()
        object Synthesizing : State()
        object Speaking : State()
        data class Error(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var activeEngine: com.horizons.model.KokoroTtsEngine? = null

    suspend fun speak(text: String): Result<Unit> {
        if (text.isEmpty()) return Result.success(Unit)

        // Prefer system TTS (VoxSherpa etc.) when available. This path is
        // independent of in-app Kokoro and doesn't pay the cold-load cost.
        if (preferSystemTts()) {
            val sys = systemTtsSupplier()
            if (sys != null) {
                _state.value = State.Speaking
                val r = sys.speak(text)
                _state.value = if (r.isSuccess) State.Idle else State.Error(
                    r.exceptionOrNull()?.message ?: "system TTS failed"
                )
                if (r.isSuccess) return r
                // Fall through to Kokoro on failure.
            }
        }

        // Lazy-load on first auto-speak: see HorizonsApplication.onCreate.
        if (ttsSupplier()?.isLoaded != true) {
            runCatching { ttsLazyLoad() }
        }
        val engine = ttsSupplier()
        if (engine == null) {
            _state.value = State.Error("No TTS engine available")
            return Result.failure(IllegalStateException("No TTS engine available"))
        }
        activeEngine = engine
        // KokoroTtsEngine.speak() does synthesis + playback internally on its own
        // AudioTrack and suspends until playback finishes.
        _state.value = State.Speaking
        return try {
            engine.speak(text)
            _state.value = State.Idle
            Result.success(Unit)
        } catch (ce: CancellationException) {
            withContext(NonCancellable) { runCatching { engine.stop() } }
            _state.value = State.Idle
            throw ce
        } catch (t: Throwable) {
            runCatching { engine.stop() }
            _state.value = State.Error(t.message ?: t.javaClass.simpleName)
            Result.failure(t)
        } finally {
            activeEngine = null
        }
    }

    fun stop() {
        runCatching { activeEngine?.stop() }
        runCatching { systemTtsSupplier()?.stop() }
        _state.value = State.Idle
    }
}
