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
import com.horizons.audio.AudioRecorder
import com.horizons.audio.MicCaptureController
import com.horizons.audio.SpeakerPlayer
import com.horizons.orchestrator.Orchestrator
import com.horizons.provider.AnthropicDirectClient
import com.horizons.provider.CredentialStore
import com.horizons.provider.ProviderLibrary
import com.horizons.screen.ScreenshotCapture
import com.horizons.tasker.TaskerBridge
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

    val audioRecorder: AudioRecorder by lazy { AudioRecorder(this) }
    val micController: MicCaptureController by lazy {
        MicCaptureController(this, audioRecorder) { moonshine }
    }
    val speaker: SpeakerPlayer by lazy { SpeakerPlayer { kokoro } }
    val tasker: TaskerBridge by lazy { TaskerBridge(this) }
    val screenshotCapture: ScreenshotCapture by lazy { ScreenshotCapture(this) }

    // termux-api voice path — primary going forward, replacing the ORT stubs.
    // ChatPanel rewire to use these is queued for next session per PROMPT_PREFIX.
    val termuxTts: com.horizons.audio.TermuxTtsClient by lazy { com.horizons.audio.TermuxTtsClient(this) }
    val termuxStt: com.horizons.audio.TermuxSttClient by lazy { com.horizons.audio.TermuxSttClient(this) }

    /**
     * Absolute path to a screenshot the user staged via ChatPanel's camera
     * button. Consumed (and cleared) by the next send. Threaded into
     * Orchestrator.stream as `imagePath` so the VLM can see the screen.
     */
    private val _pendingImagePath = MutableStateFlow<String?>(null)
    val pendingImagePath: StateFlow<String?> = _pendingImagePath.asStateFlow()
    fun setPendingImagePath(path: String?) { _pendingImagePath.value = path }

    /**
     * Anthropic prompt-cache surface. Format: "idle" before any call,
     * "warming…" while preWarm is in flight, then a snapshot string like
     * "write 1234t" or "hit 1234t (read)" after a real call. Updated from
     * [preWarmAnthropic] and intended to also be updated from the chat path
     * after each Claude response by reading the client's lastUsage.
     */
    private val _cacheStatus = MutableStateFlow<String>("idle")
    val cacheStatus: StateFlow<String> = _cacheStatus.asStateFlow()

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
    /**
     * Fire a 1-token request against the Anthropic API with the wiki as the
     * system prompt so the cache is written BEFORE any sub-agent fan-out.
     * Per Anthropic docs the cache entry only becomes available after the
     * first response begins — without this, parallel sub-agents all miss.
     *
     * Requires `anthropic.key` in CredentialStore. No-op (logs only) otherwise.
     * Updates [cacheStatus] StateFlow so RouterPanel can surface state.
     */
    fun preWarmAnthropic(ttl: AnthropicDirectClient.CacheTtl = AnthropicDirectClient.CacheTtl.ONE_HOUR) {
        if (credentials.get(AnthropicDirectClient.CRED_KEY).isNullOrBlank()) {
            _cacheStatus.value = "no anthropic.key"
            return
        }
        _cacheStatus.value = "warming…"
        scope.launch {
            // wikiSystemPrompt is a lazy { assets.open() } — defer the first read into
            // this coroutine so we don't block the caller's thread (typically main).
            val prompt = runCatching { wikiSystemPrompt }.getOrDefault("")
            if (prompt.isBlank()) { _cacheStatus.value = "wiki empty"; return@launch }
            runCatching {
                val client = AnthropicDirectClient(
                    credentials = credentials,
                    systemPrompt = prompt,
                    cacheTtl = ttl,
                )
                client.preWarm()
                client.lastUsage
            }.onSuccess { usage ->
                _cacheStatus.value = when {
                    usage == null -> "no usage returned"
                    usage.isCacheHit -> "hit ${usage.cacheReadTokens}t (read)"
                    usage.cacheCreationTokens > 0 -> "write ${usage.cacheCreationTokens}t (${ttl.apiValue})"
                    else -> "no cache activity (prefix below threshold?)"
                }
            }.onFailure { t ->
                _cacheStatus.value = "warm failed: ${t.javaClass.simpleName}: ${t.message}"
                Log.w(TAG, "preWarmAnthropic failed", t)
            }
        }
    }

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

    /** Operator-selected Kokoro voice. Persisted in CredentialStore under
     *  `kokoro.voice`. Defaults to KokoroDownloader.DEFAULT_VOICE (am_adam).
     *  Changed via setKokoroVoice() which also reloads the TTS engine. */
    fun kokoroVoice(): String =
        credentials.get("kokoro.voice")?.takeIf { it in KokoroDownloader.ALL_VOICES }
            ?: KokoroDownloader.DEFAULT_VOICE

    /** Persist a new Kokoro voice + reload the TTS engine in the background.
     *  Caller doesn't await — UI just updates the picker, Diag shows the
     *  new voice once loaded. */
    fun setKokoroVoice(voice: String) {
        require(voice in KokoroDownloader.ALL_VOICES) { "Unknown Kokoro voice: $voice" }
        credentials.put("kokoro.voice", voice)
        scope.launch {
            runCatching { kokoro?.release() }
            kokoro = null
            tryLoadTts()
        }
    }

    suspend fun tryLoadTts() {
        val dir = KokoroDownloader.installedDir(this) ?: run {
            _ttsStatus.value = "not staged"; return
        }
        val voice = kokoroVoice()
        _ttsStatus.value = "loading ($voice)…"
        runCatching {
            val eng = KokoroTtsEngine(this, dir.absolutePath, voice)
            eng.load()
            kokoro = eng
            _ttsStatus.value = "ready ($voice)"
        }.onFailure {
            Log.e(TAG, "Kokoro load failed", it)
            _ttsStatus.value = "load failed: ${it.javaClass.simpleName}: ${it.message}"
        }
    }

    private companion object { const val TAG = "HorizonsApp" }
}
