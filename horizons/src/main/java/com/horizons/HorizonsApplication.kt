package com.horizons

import android.app.Application
import android.util.Log
import com.horizons.core.log.CrashRecorder
import com.horizons.core.nexa.NexaEngine
import com.horizons.core.nexa.NexaModelLoader
import com.horizons.core.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Greenfield application entry. No legacy paths — everything routes through
 * [NexaModelLoader] + [AppStateStore].
 *
 * UI is intentionally minimal until the design artifact lands. The chat /
 * vision / voice tiles will land in follow-up at-bats.
 */
class HorizonsApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var appState: AppStateStore
        private set

    @Volatile var engine: NexaEngine? = null
        private set

    private val _engineStatus = MutableStateFlow("idle")
    val engineStatus: StateFlow<String> = _engineStatus.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        CrashRecorder(this).install()
        appState = AppStateStore(this)
        applyNexaToken()

        scope.launch {
            runCatching { NexaModelLoader.ensureSdkInit(this@HorizonsApplication) }
                .onSuccess { _engineStatus.value = "sdk ready" }
                .onFailure {
                    _engineStatus.value = "sdk init failed"
                    _engineError.value = it.message
                    Log.w(TAG, "NexaSdk init failed", it)
                }
        }
    }

    /** Load a model into the singleton [engine] slot. Unloads the previous one. */
    suspend fun loadEngine(spec: com.horizons.core.nexa.NexaModelSpec) {
        _engineStatus.value = "loading ${spec.name}…"
        _engineError.value = null
        runCatching { engine?.unload() }
        runCatching { NexaModelLoader.load(this, spec) }
            .onSuccess {
                engine = it
                _engineStatus.value = "ready: ${spec.name} (${spec.pluginId})"
            }
            .onFailure {
                engine = null
                _engineStatus.value = "load failed"
                _engineError.value = "${it.javaClass.simpleName}: ${it.message}"
                Log.e(TAG, "engine load failed for ${spec.name}", it)
            }
    }

    /** Push `nexa.token` into the env var the SDK reads at NPU activation. */
    fun applyNexaToken() {
        val token = appState.get(AppStateStore.KEY_NEXA_TOKEN)?.trim().orEmpty()
        runCatching {
            android.system.Os.setenv("NEXA_TOKEN", token, true)
            Log.i(TAG, "NEXA_TOKEN env var set (length=${token.length})")
        }.onFailure { Log.w(TAG, "Failed to set NEXA_TOKEN", it) }
    }

    private companion object { const val TAG = "HorizonsApp" }
}
