package com.horizons

import android.app.Application
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
        watchdog = WatchdogWsClient(this).also { it.start() }
        // Pick an EdgeModel based on what's staged; Stub if model folder not present yet.
        edge = EdgeModelFactory.create(this)
    }

    /** Snapshot the current engine for callers (UI). May be Stub until model staged + loaded. */
    fun engine(): EdgeModel = edge

    /** Reselect the factory and load() the engine. Called after a successful model download. */
    suspend fun reloadEngine() = loadMutex.withLock {
        runCatching { edge.unload() }
        val next = EdgeModelFactory.create(this)
        next.load()
        edge = next
    }

    /** Fire-and-forget version for UI calls. */
    fun reloadEngineAsync() { scope.launch { reloadEngine() } }
}
