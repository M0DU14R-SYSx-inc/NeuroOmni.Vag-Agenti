package com.horizons

import android.app.Application
import android.util.Log
import com.horizons.ipc.WatchdogWsClient
import com.horizons.model.EdgeModel
import com.horizons.model.EdgeModelFactory
import com.horizons.model.StubEdgeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HorizonsApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var watchdog: WatchdogWsClient
        private set

    @Volatile private var edge: EdgeModel = StubEdgeModel()
    private val loadMutex = Mutex()

    private val _engineStatus = MutableStateFlow<String>("idle")
    val engineStatus: StateFlow<String> = _engineStatus.asStateFlow()
    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        runCatching {
            watchdog = WatchdogWsClient(this).also { it.start() }
        }.onFailure { Log.e(TAG, "watchdog init failed", it) }
        if (!::watchdog.isInitialized) watchdog = WatchdogWsClient(this)

        edge = StubEdgeModel()
        if (EdgeModelFactory.installedModelDir(this) != null) {
            _engineStatus.value = "model staged; loading on startup"
            scope.launch { reloadEngine() }
        } else {
            _engineStatus.value = "no model staged"
        }
    }

    fun engine(): EdgeModel = edge

    suspend fun reloadEngine() = loadMutex.withLock {
        runCatching { edge.unload() }
        _engineError.value = null
        _engineStatus.value = "creating engine..."
        val next = runCatching { EdgeModelFactory.create(this) }.getOrElse {
            _engineError.value = "factory: ${it.javaClass.simpleName}: ${it.message}"
            Log.e(TAG, "factory failed", it)
            edge = StubEdgeModel()
            _engineStatus.value = "fell back to stub (factory)"
            return@withLock
        }
        _engineStatus.value = "loading ${next.backendTag}..."
        runCatching { next.load() }.onFailure { t ->
            val listing = (next as? com.horizons.model.NexaVlmEngine)?.lastFolderListing ?: ""
            _engineError.value = "${next.backendTag} load: ${t.javaClass.simpleName}: ${t.message}\nFolder:\n$listing"
            Log.e(TAG, "engine load failed; falling back to stub", t)
            edge = StubEdgeModel()
            _engineStatus.value = "fell back to stub (load)"
            return@withLock
        }
        edge = next
        _engineStatus.value = "ready: ${next.backendTag}"
    }

    fun reloadEngineAsync() { scope.launch { reloadEngine() } }

    private companion object { const val TAG = "HorizonsApp" }
}
