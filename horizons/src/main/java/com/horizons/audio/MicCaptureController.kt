package com.horizons.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.horizons.model.MoonshineSttEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MicCaptureController(
    private val context: Context,
    private val recorder: AudioRecorder,
    private val sttSupplier: () -> MoonshineSttEngine?,
) {
    sealed class State {
        object Idle : State()
        object Recording : State()
        object Transcribing : State()
        data class Error(val msg: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Serializes toggle() so a fast double-tap can't race start/stop.
    private val mutex = Mutex()

    fun isRecording(): Boolean = recorder.isRecording()

    suspend fun toggle(): Result<String?> = mutex.withLock {
        when (_state.value) {
            is State.Idle, is State.Error -> startRecording()
            is State.Recording -> stopAndTranscribe()
            is State.Transcribing -> Result.failure(IllegalStateException("Transcription in progress"))
        }
    }

    private suspend fun startRecording(): Result<String?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val msg = "Microphone permission not granted"
            _state.value = State.Error(msg)
            return Result.failure(SecurityException(msg))
        }
        return try {
            val r = recorder.start()
            if (r.isFailure) {
                val msg = r.exceptionOrNull()?.message ?: "Failed to start recording"
                _state.value = State.Error(msg)
                Result.failure(r.exceptionOrNull() ?: IllegalStateException(msg))
            } else {
                _state.value = State.Recording
                Result.success(null)
            }
        } catch (t: Throwable) {
            _state.value = State.Error(t.message ?: "Failed to start recording")
            Result.failure(t)
        }
    }

    private suspend fun stopAndTranscribe(): Result<String?> {
        _state.value = State.Transcribing
        val stopResult = try {
            recorder.stop()
        } catch (t: Throwable) {
            _state.value = State.Error(t.message ?: "Failed to stop recording")
            return Result.failure(t)
        }
        val pcm = stopResult.getOrElse {
            _state.value = State.Error(it.message ?: "Failed to stop recording")
            return Result.failure(it)
        }
        val engine = sttSupplier()
        if (engine == null || !engine.isLoaded) {
            val msg = "STT engine unavailable"
            _state.value = State.Error(msg)
            return Result.failure(IllegalStateException(msg))
        }
        return try {
            val text = engine.transcribe(pcm)
            _state.value = State.Idle
            Result.success(text)
        } catch (t: Throwable) {
            _state.value = State.Error(t.message ?: "Transcription failed")
            Result.failure(t)
        }
    }
}
