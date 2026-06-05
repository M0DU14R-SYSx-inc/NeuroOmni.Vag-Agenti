package com.horizons

import android.app.Application
import android.util.Log
import com.horizons.ipc.WatchdogWsClient
import com.horizons.model.EdgeModel
import com.horizons.model.EdgeModelFactory
import com.horizons.model.KokoroDownloader
import com.horizons.model.KokoroTtsEngine
import com.horizons.model.MoonshineDownloader
import com.horizons.model.MoonshineSttEngine
import com.horizons.model.StubEdgeModel
import com.horizons.orchestrator.Orchestrator
import com.horizons.provider.CredentialStore
import com.horizons.provider.ProviderLibrary
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
    lateinit var credentials: CredentialStore
        private set
    lateinit var providerLibrary: ProviderLibrary
        private set
    lateinit var orchestrator: Orchestrator
        private set

    /**
     * Cacheable static prefix — the CLAUDE_AT_HORIZONS.md wiki bundled in
     * assets. Loaded once, kept in memory. Pass this as `systemPrompt` to
     * AnthropicDirectClient / VertexClient (anthropic publisher) so the
     * Anthropic Messages API caches it with cache_control: ephemeral.
     * Use [com.horizons.provider.AnthropicDirectClient.preWarm] before
     * fanning out sub-agents so the first read is a hit, not a miss.
     */
    val wikiSystemPrompt: String by lazy {
        runCatching {
            assets.open("CLAUDE_AT_HORIZONS.md").bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.w(TAG, "wiki load failed", it); ""
        }
    }

    @Volatile private var edge: EdgeModel = StubEdgeModel()
    @Volatile var moonshine: MoonshineSttEngine? = null
        private set
    @Volatile var kokoro: KokoroTtsEngine? = null
        private set

    private val loadMutex = Mutex()

    private val _engineStatus = MutableStateFlow<String>("idle")
    val engineStatus: StateFlow<String> = _engineStatus.asStateFlow()
    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()
    private val _sttStatus = MutableStateFlow<String>("not loaded")
    val sttStatus: StateFlow<String> = _sttStatus.asStateFlow()
    private val _ttsStatus = MutableStateFlow<String>("not loaded")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        credentials = CredentialStore(this)

        // Set NEXA_TOKEN env var BEFORE any SDK init. Per Nexa docs the token is
        // the NPU license key — one device per token, free from Nexa AI Model Hub.
        // Without it the SDK may silently fail to activate the NPU path. Setting
        // empty is harmless; setting before init is what matters.
        applyNexaToken()

        runCatching {
            watchdog = WatchdogWsClient(this).also { it.start() }
        }.onFailure { Log.e(TAG, "watchdog init failed", it) }
        if (!::watchdog.isInitialized) watchdog = WatchdogWsClient(this)

        providerLibrary = ProviderLibrary(this)
        orchestrator = Orchestrator(
            context = this,
            getEdge = { edge },
            credentials = credentials,
            library = providerLibrary,
            systemPromptSupplier = { wikiSystemPrompt },
        )

        edge = StubEdgeModel()
        if (EdgeModelFactory.installedModelDir(this) != null) {
            _engineStatus.value = "model staged; loading on startup"
            scope.launch { reloadEngine() }
        } else {
            _engineStatus.value = "no model staged"
        }
        // STT/TTS — load in background if files are staged.
        scope.launch { tryLoadStt() }
        scope.launch { tryLoadTts() }
    }

    fun engine(): EdgeModel = edge

    suspend fun reloadEngine() = loadMutex.withLock {
        runCatching { edge.unload() }
        _engineError.value = null
        _engineStatus.value = "creating engine..."
        val next = runCatching { EdgeModelFactory.create(this) }.getOrElse {
            _engineError.value = "factory: ${it.javaClass.simpleName}: ${it.message}"
            edge = StubEdgeModel()
            _engineStatus.value = "fell back to stub (factory)"
            return@withLock
        }
        _engineStatus.value = "loading ${next.backendTag}..."
        runCatching { next.load() }.onFailure { t ->
            val n = next as? com.horizons.model.NexaVlmEngine
            val listing = n?.lastFolderListing ?: ""
            val initMsg = n?.lastInitMessage ?: ""
            _engineError.value = "${next.backendTag} load: ${t.javaClass.simpleName}: ${t.message}\nInit: $initMsg\nFolder:\n$listing"
            edge = StubEdgeModel()
            _engineStatus.value = "fell back to stub (load)"
            return@withLock
        }
        edge = next
        _engineStatus.value = "ready: ${next.backendTag}"
    }

    fun reloadEngineAsync() { scope.launch { reloadEngine() } }

    /**
     * Read nexa.token from CredentialStore and export it as the NEXA_TOKEN
     * env var so the Nexa SDK / NexaML runtime can activate the NPU license.
     * Safe to call multiple times — used at boot and after the user enters
     * a new token in Router panel.
     */
    fun applyNexaToken() {
        val token = credentials.get("nexa.token")?.trim().orEmpty()
        runCatching {
            android.system.Os.setenv("NEXA_TOKEN", token, true)
            Log.i(TAG, "NEXA_TOKEN env var set (length=${token.length})")
        }.onFailure { Log.w(TAG, "Failed to set NEXA_TOKEN env var", it) }
    }

    suspend fun tryLoadStt() {
        val dir = MoonshineDownloader.installedDir(this) ?: run {
            _sttStatus.value = "not staged"; return
        }
        _sttStatus.value = "loading..."
        runCatching {
            val eng = MoonshineSttEngine(this, dir.absolutePath)
            eng.load()
            moonshine = eng
            _sttStatus.value = "ready"
        }.onFailure {
            Log.e(TAG, "Moonshine load failed", it)
            _sttStatus.value = "load failed: ${it.javaClass.simpleName}: ${it.message}"
        }
    }

    suspend fun tryLoadTts() {
        val dir = KokoroDownloader.installedDir(this) ?: run {
            _ttsStatus.value = "not staged"; return
        }
        _ttsStatus.value = "loading..."
        runCatching {
            val eng = KokoroTtsEngine(this, dir.absolutePath)
            eng.load()
            kokoro = eng
            _ttsStatus.value = "ready (am_adam)"
        }.onFailure {
            Log.e(TAG, "Kokoro load failed", it)
            _ttsStatus.value = "load failed: ${it.javaClass.simpleName}: ${it.message}"
        }
    }

    private companion object { const val TAG = "HorizonsApp" }
}
