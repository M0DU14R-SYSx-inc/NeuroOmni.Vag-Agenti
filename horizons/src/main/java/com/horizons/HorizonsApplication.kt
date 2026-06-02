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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HorizonsApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var watchdog: WatchdogWsClient
        private set

    @Volatile private var edge: EdgeModel = StubEdgeModel()
    private val loadMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        runCatching {
            watchdog = WatchdogWsClient(this).also { it.start() }
        }.onFailure { Log.e(TAG, "watchdog init failed", it) }
        if (!::watchdog.isInitialized) watchdog = WatchdogWsClient(this)

        edge = runCatching { EdgeModelFactory.create(this) }
            .onFailure { Log.e(TAG, "edge factory failed", it) }
            .getOrElse { StubEdgeModel() }
    }

    fun engine(): EdgeModel = edge

    suspend fun reloadEngine() = loadMutex.withLock {
        runCatching { edge.unload() }
        val next = runCatching { EdgeModelFactory.create(this) }.getOrElse { StubEdgeModel() }
        runCatching { next.load() }.onFailure {
            Log.e(TAG, "engine load failed; falling back to stub", it)
            edge = StubEdgeModel()
            return@withLock
        }
        edge = next
    }

    fun reloadEngineAsync() { scope.launch { reloadEngine() } }

    private companion object { const val TAG = "HorizonsApp" }
}
