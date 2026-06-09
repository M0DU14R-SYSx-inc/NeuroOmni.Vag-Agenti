# CLAUDE @ Horizons — Full Architecture & Build State

Reload context for the Horizons (N0.V4) build on the Motorola Razr Ultra.
This is the authoritative snapshot — read top-to-bottom before resuming.

---

## Product

A native Android Kotlin/Compose app for the Razr Ultra (Snapdragon 8 Elite,
Hexagon NPU v79, arm64-v8a) that runs on-device AI with cloud failover. Two
apps, one repo:

- **Horizons** (`com.horizons`) — the main app: chat, router/provider library,
  terminal, diagnostics, settings. Owns: edge VLM, STT, TTS, orchestration,
  provider clients, Termux/Tasker bridges, screenshot capture, audio capture,
  JSONL logging.
- **Nova / Watchdog** (`com.horizons.watchdog`) — separate process, separate
  launcher icon (cogwheel + RPM gauge). Foreground service hosts loopback WS
  server on `127.0.0.1:47821`. Will own telemetry, fallback-ladder triggering,
  crash capture, boot resurrection. Currently scaffolded; logic is TODO.

Hard constraints: Razr only. No Pi/Jetson/Tailscale, no Ollama, no Vulkan, no
Python sidecar, no `nexa serve`, no Whisper-via-Termux (Moonshine stays),
no multi-device. Browser automation deferred to Phase 7.

---

## Monorepo layout

```
NeuroOmni.Vag-Agenti/
├── horizons/                          # main app module (com.horizons)
│   ├── build.gradle.kts                # AGP 8.8.0, Kotlin 2.1.0, JDK 17, ABI=arm64-v8a, signed by debug.keystore
│   └── src/main/
│       ├── AndroidManifest.xml         # all permissions + queries com.termux + FGS types
│       ├── java/com/horizons/
│       │   ├── HorizonsApplication.kt  # app singleton; engine + credentials + orchestrator + STT/TTS lifecycles
│       │   ├── MainActivity.kt         # Compose entry, bottom nav, top bar
│       │   ├── audio/
│       │   │   └── AudioRecorder.kt    # PCM16 mono @16k, push-to-talk capture
│       │   ├── ime/HorizonsImeService.kt          # TODO Phase 7 (custom keyboard)
│       │   ├── accessibility/HorizonsAccessibilityService.kt   # TODO Phase 7 (text injection)
│       │   ├── ipc/WatchdogWsClient.kt # connects to watchdog loopback
│       │   ├── logging/
│       │   │   └── InteractionLogger.kt   # JSONL daily files, mutex-guarded append
│       │   ├── model/
│       │   │   ├── EdgeModel.kt        # interface seam
│       │   │   ├── EdgeModelFactory.kt # picks NexaVlmEngine vs StubEdgeModel
│       │   │   ├── NexaVlmEngine.kt    # OmniNeural-4B-mobile loader, callback-based init
│       │   │   ├── StubEdgeModel.kt    # CI/no-model fallback
│       │   │   ├── EdgeModelDownloader.kt   # HF → 13-file flat layout
│       │   │   ├── EdgeModelImporter.kt     # SAF import with canonicalName ()/N/.bin tolerance
│       │   │   ├── MoonshineDownloader.kt   # onnx-community/moonshine-base-ONNX
│       │   │   ├── MoonshineSttEngine.kt    # ORT session loaded; transcribe() loop TODO (Phase 2)
│       │   │   ├── KokoroDownloader.kt      # onnx-community/Kokoro-82M-v1.0-ONNX + am_adam voice
│       │   │   └── KokoroTtsEngine.kt       # ORT session loaded; speak() loop TODO (Phase 4)
│       │   ├── orchestrator/
│       │   │   ├── Tool.kt             # interface: id, displayName, run(prompt,imagePath): Flow<String>
│       │   │   ├── SttMode.kt          # Dictation / MetaPrompt / BashCommand
│       │   │   ├── Dispatcher.kt       # VLM-as-manager tool picker (Phase 5 TODO)
│       │   │   └── Orchestrator.kt     # NPU first → ProviderLibrary failover → openrouter.key direct → stub
│       │   ├── overlay/FloatingOverlayService.kt   # TODO Phase 7 floating tiles
│       │   ├── provider/
│       │   │   ├── NamedBackend.kt     # @Serializable; Wire enum now includes TermuxCli
│       │   │   ├── ProviderLibrary.kt  # JSON-persisted list in filesDir, CRUD (load/save/add/update/delete)
│       │   │   ├── ProviderFactory.kt  # NamedBackend → Tool instance per Wire type
│       │   │   ├── CredentialStore.kt  # EncryptedSharedPreferences (Keystore-backed)
│       │   │   ├── WireNormalizer.kt   # Polar-shape canonical request (Phase 6 TODO)
│       │   │   ├── OpenRouterClient.kt # SSE, models[] fallback chain qwen→claude→gemini
│       │   │   ├── OpenAICompatibleClient.kt   # generic; uses WireNormalizer
│       │   │   ├── AnthropicDirectClient.kt    # x-api-key + anthropic-version SSE
│       │   │   ├── AIStudioGeminiClient.kt     # x-goog-api-key + streamGenerateContent
│       │   │   ├── VertexClient.kt     # service-account JWT (RSA-SHA256) + token cache + ANTHROPIC/GOOGLE publishers
│       │   │   └── TermuxCliClient.kt  # {prompt} template fired via TermuxBridge
│       │   ├── screen/
│       │   │   ├── ScreenshotCapture.kt   # MediaProjection consent + reuse + PNG capture + prune 10 newest
│       │   │   └── ScreenCaptureService.kt # FGS type=mediaProjection (API 34+ req)
│       │   ├── tasker/
│       │   │   └── HorizonsTaskerReceiver.kt   # inbound `am broadcast`: RELOAD_ENGINE/IMPORT_FOLDER/SEND_PROMPT/GET_STATUS
│       │   ├── termux/
│       │   │   └── TermuxBridge.kt     # outbound RUN_COMMAND, PendingIntent + receiver, timeout, retry, error mapping
│       │   ├── tiles/                  # MicTile, AiPillTile, ScreenAskTile, ReadBackTile (objects), QsScreenAskTile (TileService)
│       │   ├── ui/panels/              # ChatPanel, RouterPanel, TerminalPanel, ModePicker
│       │   ├── ui/settings/SettingsDrawer.kt   # TODO #12
│       │   ├── ui/theme/
│       │   │   ├── Color.kt            # slate #222C34, teal #2DD4D9, near-black #050709, action yellow #F5C518
│       │   │   ├── Theme.kt            # M3 darkColorScheme + LocalContentColor forced
│       │   │   └── Backdrop.kt         # pure Compose Brush.radialGradient (NOT painterResource)
│       │   └── widget/ChatAppWidgetProvider.kt # home-screen widget (tap → launch MainActivity)
│       └── res/                        # drawables (ic_horizon_sun, ic_settings_cog, ic_tile_lightbulb, ic_mic), themes, colors, mipmaps
├── watchdog/                          # Nova app module (com.horizons.watchdog)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/horizons/watchdog/
│       │   ├── WatchdogApplication.kt
│       │   ├── WatchdogActivity.kt     # minimal Compose dashboard
│       │   ├── service/WatchdogService.kt   # FGS + WS server stub
│       │   ├── service/BootReceiver.kt
│       │   ├── telemetry/DeviceTelemetry.kt # thermal/failure-count stubs
│       │   ├── crash/CrashLog.kt       # TODO
│       │   └── ladder/FallbackLadder.kt    # placement transitions
│       └── res/drawable/ic_watchdog_cog_rpm.xml   # cog + RPM gauge launcher icon
├── shared/                            # pure WS contract (kotlinx-serialization)
│   └── src/main/java/com/horizons/shared/ipc/
│       ├── WsContract.kt               # port 47821, heartbeat constants
│       └── Messages.kt                 # Hello, Heartbeat, Telemetry, FailureFlag, HotSwap, RestartSession, ImageRef, Placement, FailureType
├── legacy/                            # archived previous tree, reference only, not built
├── release/debug.keystore             # committed; every CI build signs with it (installs as update)
├── docs/TASKER_AND_TERMUX.md          # operator-facing am broadcast examples
├── .github/workflows/build-apk.yml    # CI: build both APKs + publish to "latest-debug" GitHub Release
├── gradle/libs.versions.toml          # nexa 0.0.24, onnxruntime 1.20.0, okhttp 4.12.0, java-websocket 1.5.7, kotlinx-serialization 1.7.3
└── settings.gradle.kts                # include :shared :horizons :watchdog
```

---

## Runtime stack on-device

### VLM (locked)
- **Model:** `NexaAI/OmniNeural-4B-mobile` from HF, flat 13-file layout
- **Engine:** Nexa Android SDK `ai.nexa:core:0.0.24` (Apache 2.0, ~274 MB AAR
  including QNN runtimes for HTP v79/v81/v85)
- **Wrapper:** `VlmWrapper.builder().vlmCreateInput(VlmCreateInput(model_name="omni-neural", model_path=<folder>, mmproj_path="", config=ModelConfig(max_tokens=2048, enable_thinking=false), plugin_id=NexaSdk.PLUGIN_ID_NPU, device_id=""))`
- **Init contract:** `NexaSdk.getInstance().init(ctx, InitCallback)` — callback overload
  REQUIRED to surface failure reasons; no-callback overload swallows them.
- **Token:** `NEXA_TOKEN` env var must be set BEFORE init. NPU license activator,
  1 device per token, free from nexahub.io. `HorizonsApplication.applyNexaToken()`
  exports it via `android.system.Os.setenv`.
- **Asset constraint:** DO NOT strip `assets/npu/htp-files-v81/` or `v85/`. SDK's
  `HTP_ASSET_DIRS` constant iterates all three at init. Stripping breaks init.
- **Files (13 required, verified against HF sizes):** nexa.manifest (81 B),
  files-1-1.nexa (11.4 MB), attachments-1-3.nexa (575 B), attachments-2-3.nexa
  (576 B), attachments-3-3.nexa (576 B), weights-1-8 (767 MB), weights-2-8
  (1.16 GB), weights-3-8 (4.57 MB — legit, not truncated), weights-4-8 (900 MB),
  weights-5-8 (14.3 MB — legit), weights-6-8 (5.62 MB — legit), weights-7-8
  (651 MB), weights-8-8 (1.24 GB). Plus 0-byte config.json auto-created on load.

### STT (locked: Moonshine)
- **Model:** `onnx-community/moonshine-base-ONNX`, int8 variant
- **Files:** encoder_model_int8.onnx (20.5 MB) + decoder_model_merged_int8.onnx
  (42.4 MB) + tokenizer.json + config files (~67 MB total)
- **Engine:** Android ORT in-process via `onnxruntime-android 1.20.0`
- **Status:** session loading wired; mel preprocessing + greedy decode loop TODO
  (issue #13)
- **Capture:** AudioRecorder produces PCM16 mono @ 16 kHz `ShortArray`
- **Parakeet shelved.** Even though Nexa SDK has AsrWrapper + parakeet-tdt-0.6b-npu-mobile
  is published, real-estate cost not justified given Moonshine works.

### TTS (locked: Kokoro)
- **Model:** `onnx-community/Kokoro-82M-v1.0-ONNX`, q8f16 variant (86 MB)
- **Voice:** am_adam (522 kB) — operator pick, Bud Light "Real Men of Genius" tone
- **Engine:** Android ORT in-process
- **Status:** session + voice load wired; phonemize + synthesis loop TODO
  (issue #14)
- **Audio out:** AudioTrack streaming @ 24 kHz, tap-to-interrupt barge-in via
  `stopRequested` between ~100 ms chunks
- **Nexa TtsWrapper exists** but no NPU-mobile TTS model published; will swap
  when Nexa publishes one.

---

## Cloud routing

```
NPU first (OmniNeural via Nexa) → fails →
ProviderLibrary failover entry (default: OpenRouter, configurable via isFailoverTarget) → fails →
legacy openrouter.key direct → none → stub error
```

**Failover lane:** OpenRouter only (single endpoint, OpenAI-compatible,
`models[]` chain qwen-2.5-72b → claude-3.5-sonnet → gemini-2.0-flash).
**Less fragmented** than Vertex's per-publisher endpoints + JWT auth.

**Explicit-pick backends** (selectable per Chat / per Router test / per future
VLM dispatcher decision):
- OpenRouter (any model, generic)
- Vertex Claude / Vertex Gemini (GCP credits, service-account JWT)
- AI Studio Gemini (x-goog-api-key; same GCP project = same credit pool)
- Anthropic Direct (workspace-scoped sk-ant-... keys)
- TermuxCLI variants (Claude Code CLI, Gemini CLI, gcloud, gh — template-driven via TermuxBridge)

OpenRouter ≠ GCP pipe. Each cloud lane is its own client with its own
credentials. No proxy in between (LiteLLM/Frontier-proxy idea is DEAD).

---

## Bridges

- **TermuxBridge** (outbound `com.termux.RUN_COMMAND`): per-call unique action
  String, programmatically-registered receiver, PendingIntent FLAG_ONE_SHOT |
  FLAG_MUTABLE, withTimeout wrap, ActivityNotFoundException + SecurityException
  + IllegalStateException paths, FGS via startForegroundService on API 26+.
- **HorizonsTaskerReceiver** (inbound `am broadcast`): RELOAD_ENGINE,
  IMPORT_FOLDER (with `tree_uri` extra), SEND_PROMPT (with `text` extra),
  GET_STATUS. Emits `com.horizons.action.RESULT` broadcast with `source_action`
  + `body` extras + optional `output_file` append. Exported, no permission gate.
- **AudioRecorder**: @Synchronized lifecycle, AtomicBoolean, private supervisor
  scope, defensive ShortArray copies, ContextCompat permission check,
  AudioRecord released on every failure path.
- **ScreenshotCapture**: prepareConsentIntent() → onConsentResult() stores
  resultCode+data; ScreenCaptureService FGS started by Activity BEFORE
  `MediaProjectionManager.getMediaProjection()` (Android 14+ req); VirtualDisplay
  + ImageReader single-frame capture → PNG → `filesDir/screenshots/snap_<ts>.png`;
  auto-prune to last 10.

---

## CI / build / release

- **AGP** 8.8.0, **Kotlin** 2.1.0, **Compose BOM** 2024.12.01, **compileSdk** 35,
  **minSdk** 31, **JDK** 17
- **ABI** filter: arm64-v8a only (Razr Ultra) — saves ~55 MB
- **Packaging:**
  - `jniLibs.pickFirsts += "**/libonnxruntime.so", "**/libonnxruntime4j_jni.so"`
    (Nexa AAR + onnxruntime-android both ship them)
  - HTP asset dirs NOT stripped (broke SDK init when attempted)
- **Signing:** stable `release/debug.keystore` committed; every CI build signs
  identical → installs as APK update, no uninstall ever needed
- **Workflow:** `.github/workflows/build-apk.yml` builds both APKs on every
  push to any branch, generates gradle wrapper inline, uploads as artifacts AND
  publishes to GitHub Release tagged `latest-debug` (rolls forward each push)
- **Stable APK URL:** `github.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/releases/tag/latest-debug`
- **Current APK size:** ~787 MB (large because Nexa AAR is 274 MB × replicated
  for arm64). Acceptable since downloads via direct GitHub release link work.

---

## Bugs overcome (chronological highlights)

| # | Symptom | Root cause | Fix |
|---|---|---|---|
| 1 | APK 842 MB, "couldn't parse" on install | Nexa AAR ships native libs for 4 ABIs | `ndk.abiFilters += "arm64-v8a"` + `packaging.jniLibs.excludes` non-arm |
| 2 | Folder picker silent fail | Pre-seeded `EXTRA_INITIAL_URI` was a document URI (not tree URI) on modern Android | `launch(null)` — opens to default location |
| 3 | "Engine: stub" forever | `EdgeModelFactory.create()` returned `NexaVlmEngine` but `load()` never called | `HorizonsApplication.onCreate` launches `reloadEngine()` if model staged |
| 4 | Checklist stuck 13/14 forever | HF ships `config.json` as 0 bytes; Chrome drops 0-byte files | Removed from required checklist; `NexaVlmEngine.load()` auto-creates empty `config.json` |
| 5 | Reload button clipped off-screen | 4 buttons in one row exceeded phone width | 2×2 grid with `Modifier.weight(1f)` |
| 6 | Splash-then-crash on app open | `painterResource` on `<shape>` XML drawable throws | Replaced with pure Compose `Brush.radialGradient` |
| 7 | Model create() error codes -204029176 / 568078504 / random | No-callback `NexaSdk.init(ctx)` swallows real failures → half-init state returns garbage memory as "error code" | Switched to `init(ctx, InitCallback)` — `onFailure(message)` surfaces real reason |
| 8 | Stripping `htp-files-v81/v85` broke init | SDK's `HTP_ASSET_DIRS = [htp-files, htp-files-v81, htp-files-v85]` iterates all three at boot regardless of device HTP version | Reverted asset strip; APK back to ~787 MB |
| 9 | NPU token unused | `nexa.token` slot existed in CredentialStore but never read | `HorizonsApplication.applyNexaToken()` exports as `NEXA_TOKEN` env var before SDK init; reapplied + engine reloaded when user saves new token |
| 10 | InteractionLogger agent landed file in `legacy/app/...` path | Agent saw archived monorepo + assumed it was current | Cherry-picked file content, rewrote `package` line, committed to correct `horizons/...` path |
| 11 | Operator's "truncated" weight uploads (weights-3-8 = 4.6 MB etc.) | NOT truncated — real HF sizes | Apologized; restored full 13-file checklist |
| 12 | Mid-AAR `model_path` confusion | Docs show file path `/.../files-1-1.nexa`; legacy code passes folder | Settled on folder path (matches legacy-verified working pattern); auto-create config.json regardless |

---

## Agent / issue board state

### Filed GitHub issues (15 total)

| # | Title | Status |
|---|---|---|
| 3 | [ping channel] Claude → Clovis pings | open, used for build notifications |
| 4 | [ping test] Does this fire on your phone? | closed test |
| 5 | [provider] Vertex AI client (Claude + Gemini, SA JWT) | **MERGED** (branch claude/agent-vertex-client) |
| 6 | [provider] AI Studio Gemini (x-goog-api-key) | **MERGED** (branch claude/agent-aistudio-gemini) |
| 7 | [provider] Anthropic Direct (Bearer + SSE) | **MERGED** (branch claude/agent-anthropic-direct) |
| 8 | [termux] Outbound RUN_COMMAND bridge | **MERGED** (branch claude/agent-termux-bridge) |
| 9 | [logging] JSONL InteractionLogger | **MERGED + path-fixed** (branch claude/agent-interaction-logger) |
| 10 | [ui] Real Diagnostics panel | open — single-author UI work |
| 11 | [ui] Provider library CRUD in Router panel | open — single-author UI work |
| 12 | [ui] Settings drawer with cogwheel + theme controls | open — single-author UI work |
| 13 | [edge] Moonshine STT inference loop | open — agent-safe, ready to dispatch |
| 14 | [edge] Kokoro TTS synthesis loop | open — agent-safe, ready to dispatch |
| 15 | [audio] PCM16 push-to-talk AudioRecorder | **MERGED** (branch claude/agent-audio-recorder) |
| 16 | [vision] ScreenshotCapture via MediaProjection | **MERGED** (branch claude/agent-screenshot-capture) |
| 17 | [provider] ProviderFactory + TermuxCliClient | **MERGED** (branch claude/agent-provider-factory) |

### Agent constructor-signature notes (carry forward)
- `OpenRouterClient` does NOT implement Tool; hardcoded openrouter base URL.
  ProviderFactory wraps it in an anonymous Tool adapter. `backend.baseUrl` is
  ignored for OpenAI-compat backends.
- `AnthropicDirectClient` and `AIStudioGeminiClient` always read their hardcoded
  credential key (`anthropic.key`, `aistudio.key`) internally — ignore
  `backend.credentialKey`. Factory pre-checks the hardcoded key. Divergence
  documented; refactor if multi-account support needed later.

### Uncommitted in working tree (this session)
- `provider/ProviderLibrary.kt` — JSON persistence to `filesDir/provider_library.json`,
  full CRUD (load/save/add/update/delete/byId/failoverTarget)
- `orchestrator/Orchestrator.kt` — failover via ProviderLibrary + ProviderFactory,
  with forcedToolId path for explicit per-request backend, legacy openrouter.key
  fallback preserved

### Next agent dispatches (ready to fire, agent-safe)
- **Issue #13** — Moonshine STT inference loop: log-mel preprocessing (80 mels,
  hop 160, win 400, n_fft 512), encoder ORT run, decoder greedy decode using
  start/eos token from tokenizer.json, BPE detokenize. Reference
  transformers.js source for preprocessor params.
- **Issue #14** — Kokoro TTS synthesis loop: G2P phonemize (English am_adam
  first, espeak-ng table OR simple table), token IDs → ORT model run with voice
  embedding + speed scalar → float32 audio @ 24 kHz → AudioTrack streaming
  honoring stopRequested.

### Next single-author UI work (one congruent push planned)
- Commit + push the uncommitted Orchestrator + ProviderLibrary refactor
- Issue #11 Provider library CRUD UI in Router panel — add/edit/delete dialog,
  per-row test button (1-token prompt + pass/fail), provider-type dropdown,
  per-row `isFailoverTarget` + `dispatcherEligible` toggles
- Issue #10 Diagnostics panel — replace MainActivity placeholder, show engine
  state, STT/TTS state, Termux bridge state (via test command), battery from
  BatteryManager, thermal from `/sys/class/thermal/`, permissions status,
  storage status, per-provider latency from InteractionLogger.tail()
- Issue #12 Settings drawer + cogwheel — `ModalNavigationDrawer` or sheet,
  theme color/opacity/font/highlight controls (ThemeState mutated at runtime),
  default backend + default STT/TTS dropdowns, permission shortcut buttons,
  log purge button
- Chat panel mic button → AudioRecorder.start() / stop() → MoonshineSttEngine
  .transcribe() (once #13 lands) → input field text
- Chat panel screenshot button → MainActivity-level consent launcher →
  ScreenshotCapture.captureToFile() → Orchestrator.stream(prompt, imagePath)
- Outbound Tasker intent fire-and-forget for device automation (open app,
  toggle setting, play media)

---

## Operator setup checklist (Razr-side)

1. Install Watchdog APK first (FGS survives independently)
2. Install Horizons APK (signed same keystore, installs as update going forward)
3. Open Horizons → Router panel
4. Enter `nexa.token` (free from nexahub.io, 1 device per token) → save → engine auto-reloads
5. Enter `openrouter.key` for cloud failover lane
6. Optional: enter Vertex / Anthropic / AI Studio keys for explicit-pick backends
7. Use **Folder** picker → navigate to wherever the 13 OmniNeural files are
   staged → Use this folder → checklist hits 13/13 → engine flips to `nexa-npu`
8. Chat panel → send a prompt → real reply

---

## Brand / palette

- **Background:** dark slate `#222C34` (NOT near-black; near-black is reserved
  for icon backplate only)
- **Surface:** `#35414A`; elevated `#42505A`
- **Primary teal:** `#2DD4D9` (Tasker/Weather icon match)
- **Highlight teal:** `#4FE7EC`
- **Icon backplate:** `#050709` (near-black)
- **Action yellow:** `#F5C518` (lightbulb flash + Vertex needle)
- **Backdrop:** Compose `Brush.radialGradient` with `#2A4E58` → `#2D3A43` → `#222C34`,
  centered upper-left. Pure Compose, not XML shape (painterResource on `<shape>`
  crashes).
- **Launcher icons:**
  - Horizons: teal sunset + horizon line + cloud on near-black backplate
  - Watchdog/Nova: teal cogwheel with 8 chunky teeth + RPM half-dial + yellow
    needle pointing high

---

## Source URLs (verified, not guessed)

- Repo: https://github.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti
- Branch: `main`
- Stable APK URL: https://github.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/releases/tag/latest-debug
- Ping channel: https://github.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/issues/3
- Nexa SDK Maven: https://repo1.maven.org/maven2/ai/nexa/core/0.0.24/
- Nexa Android docs: https://docs.nexa.ai/en/nexa-sdk-android/quickstart
- NexaAI HF org: https://huggingface.co/NexaAI
- OmniNeural HF: https://huggingface.co/NexaAI/OmniNeural-4B-mobile
- Moonshine HF: https://huggingface.co/onnx-community/moonshine-base-ONNX
- Kokoro HF: https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX
- Termux RUN_COMMAND: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
