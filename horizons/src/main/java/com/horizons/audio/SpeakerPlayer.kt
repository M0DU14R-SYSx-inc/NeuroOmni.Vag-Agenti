package com.horizons.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SpeakerPlayer(
    private val ttsSupplier: () -> com.horizons.model.KokoroTtsEngine?,
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
        val engine = ttsSupplier()
        if (engine == null) {
            _state.value = State.Error("Kokoro not loaded")
            return Result.failure(IllegalStateException("Kokoro not loaded"))
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
        _state.value = State.Idle
    }
}
