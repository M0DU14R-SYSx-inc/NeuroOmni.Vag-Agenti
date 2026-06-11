# Horizons Execution Board

> **Living artifact.** This file is the active task list + claims dashboard.
> Any coding agent picking up work updates the relevant milestone's `status`
> and `claimed_by` lines, then commits this file (1-line diff usually).
> Multiple agents can run in parallel on independent milestones — the
> dependency map below tells you what's safe to pick up.
>
> **Not** a wiki. Not architecture-of-record. For that read
> `CLAUDE_AT_HORIZONS.md` + `PROMPT_PREFIX.md` + `docs/LIGHTHOUSE.md` first.

---

## Active claims dashboard

| Milestone | Status | Claimed by | Started |
|---|---|---|---|
| M1.1 | DONE (sherpa rewrite, device-verify pending) | side-panel agent | 2026-06-11 |
| M1.2 | DONE (sherpa rewrite, device-verify pending) | side-panel agent | 2026-06-11 |
| M2.3 | IN_PROGRESS | Opus 4.8 (Claude Code) | 2026-06-09 |

*(Update this table when you claim a milestone. Format: just edit the row,
commit `chore(board): M1.3 claimed by main`.)*

---

## Difficulty scale + model-strength guidance

| Rating | Scope | Recommended model |
|---|---|---|
| 1/5 | Trivial. 1 small file, ≤50 LOC, no integration risk. | Any current code-capable model. Haiku/Sonnet-fast fine. |
| 2/5 | Small. ~100 LOC, touches 1-2 files, isolated. | Sonnet 4.x, Qwen-Coder, Codex equivalent. |
| 3/5 | Medium. ~300 LOC, 2-3 files, careful integration. | Sonnet 4.x with explicit spec. Opus 4.x as safety. |
| 4/5 | Hard. Architectural decision involved, multi-file, may need iteration cycles. | Opus 4.6+ or Sonnet 4.x with adversarial-review pattern. |
| 5/5 | Research-grade. Unknown unknowns. May require pivot mid-build. | Opus 4.8 / human collaboration loop. |

## Status flow

```
AVAILABLE → CLAIMED → IN_PROGRESS → DONE
                            ↓
                       BLOCKED / FAILED → AVAILABLE
```

- **AVAILABLE** — ready for any agent to claim. All deps DONE.
- **CLAIMED** — agent has picked it up but not started touching code yet.
- **IN_PROGRESS** — agent is committing changes against it.
- **BLOCKED** — needs user input or an undone dep just surfaced. Add a `blocker:` note.
- **FAILED** — agent gave up or repeated failures. Operator hands off to stronger model. Add a `failed_notes:` block.
- **DONE** — PR/commit merged, acceptance criteria verified on device or in CI.

---

## Layer 1 — On-device foundations

*No cross-layer deps. All M1.x can run in parallel with each other and with
all M2/M3/M4 milestones. M1.1 + M1.2 are the user-visible "voice loop"
prereqs.*

### M1.1 — Real Moonshine STT inference

> **2026-06-11 RESOLUTION**: rewritten on **sherpa-onnx OfflineRecognizer**
> (Moonshine base int8, ~250 MB sherpa archive). The spec below describes
> the abandoned hand-rolled ORT path — kept for history. See
> `wiki/FAILURE_LOG.md` + `wiki/FORK_DECISIONS.md` ("Voice runtime").
> Device verification pending: tap mic → speak → transcript appears.

```yaml
status: DONE   # sherpa-onnx rewrite 2026-06-11; on-device verify pending
claimed_by: null
difficulty: 3
depends_on: []
files:
  - horizons/src/main/java/com/horizons/model/MoonshineSttEngine.kt
  - (maybe) horizons/src/main/java/com/horizons/model/MoonshineDownloader.kt
spec: |
  Replace the stub body of MoonshineSttEngine.transcribe(ShortArray, Int): String
  with real ONNX inference. Public signature unchanged.
  - load(): SessionOptions().apply { addCpu(); setIntraOpNumThreads(2);
    setInterOpNumThreads(1); setMemoryPatternOptimization(true) }
  - transcribe pipeline: PCM16 → FloatArray (/32768f) → encoder input_values
    [1,N] → encoder_hidden_states → greedy decode on decoder with input_ids +
    encoder_hidden_states until EOS or 256 tokens → detokenize via
    tokenizer.json.
  - Discover input/output tensor names at runtime via session.inputInfo /
    outputInfo. Do NOT hardcode.
  - Close OnnxTensor instances after use (Kotlin use{} blocks).
  - On failure return "[moonshine error: <message>]" so callers see something.
  - Update MoonshineDownloader.FILES if tokenizer.json / tokenizer_config.json
    aren't in the list.
acceptance:
  - tap mic in ChatPanel → speak → input field shows actual transcript
  - Diag shows "STT: ready"
notes: |
  See PROMPT_PREFIX.md "LIGHTHOUSE DOC" section — Moonshine MUST use addCpu
  with FORCED EXCLUSION. Do NOT addNnapi() on Moonshine — that steals NPU
  from Nexa. Model is onnx-community/moonshine-base-ONNX (raw audio in,
  no mel-spectrogram needed; encoder takes float32 audio directly).
```

### M1.2 — Real Kokoro TTS inference

> **2026-06-11 RESOLUTION**: rewritten on **sherpa-onnx OfflineTts**
> (kokoro-multi-lang-v1_0, 53 voices, espeak-ng data bundled in the
> archive — the M1.2a JNI phonemizer below became unnecessary). The
> operator-locked "espeak-ng, not Termux" decision is honored: sherpa
> runs espeak-ng natively. NNAPI/GPU half of M1.2b intentionally NOT
> done — sherpa runs provider=cpu per FORCED EXCLUSION; GPU is a fork
> criterion in `wiki/FORK_DECISIONS.md`. Spec kept for history.
> Device verification pending: chat reply → am_adam speaks, volume
> toggle barge-in works.

```yaml
status: DONE   # sherpa-onnx rewrite 2026-06-11; on-device verify pending
claimed_by: null   # was: Gemini Code Assist (claim superseded by rewrite)
difficulty: 4
depends_on: []
priority: P0   # operator-locked: voice loop blocker
files:
  - horizons/src/main/java/com/horizons/model/KokoroTtsEngine.kt
  - horizons/src/main/java/com/horizons/model/KokoroPhonemizer.kt  (new)
  - horizons/src/main/cpp/ + CMakeLists.txt (new — espeak-ng JNI)
  - horizons/build.gradle.kts (externalNativeBuild block)
spec: |
  OPERATOR-LOCKED DECISION (do not re-litigate): use **espeak-ng via JNI**
  for phonemization. NOT termux fallback (operator explicitly rejected
  system-TTS path — voice quality matters). NOT a pure-Kotlin G2P port
  (too fragile for English edge cases).

  Replace stub body of KokoroTtsEngine.speak(text, pitch, rate): Unit
  with real synth + AudioTrack playback. Two halves:

  M1.2a — espeak-ng JNI phonemizer (this is the hard part):
  - Vendor espeak-ng 1.51 source (or a known-good Android port — check
    https://github.com/numediart/espeak-ng-android or rhasspy/piper variants
    for prebuilt AARs/CMake setups that don't require building the whole
    espeak data dictionary on-device).
  - CMakeLists.txt under horizons/src/main/cpp/ that builds libespeak.so
    for arm64-v8a only. Add externalNativeBuild block to horizons/build.gradle.kts.
  - Bundle espeak-ng data files (~3-5 MB) in assets/espeak-ng-data/, extract
    to filesDir on first use, point ESPEAK_DATA_PATH at that.
  - KokoroPhonemizer.kt: `phonemize(text: String): IntArray` — calls JNI,
    returns IPA token IDs matching Kokoro's vocab (see misaki's vocab file
    if you need the mapping; HF model has phoneme→id in tokenizer.json or
    similar — verify which file Kokoro v1.0 ships).

  M1.2b — synth + playback:
  - load(): OrtSession.SessionOptions().apply {
      addNnapi(NnapiOptions().apply { executionMode = PREFER_SUSTAINED_SPEED })
    } so Kokoro lands on Adreno GPU via NNAPI→QNN. Verify at runtime via
    session.sessionOptions.executionProviders — must contain
    "NNAPIExecutionProvider", fail loud (throw) if it falls back to CPU.
  - speak() pipeline:
      text → phonemize() → IntArray phoneme IDs
      → OnnxTensor input_ids [1, N]
      + OnnxTensor style [1, 256] (the loaded voiceEmbedding FloatArray)
      + OnnxTensor speed [scalar] (1.0f default; param `rate` from caller)
      → session.run(...) → OnnxTensor audio_out float32 @ 24kHz
      → convert to PCM16 short array (clamp to [-1, 1] then * 32767)
      → AudioTrack.write() in chunks
      → honor stopRequested between chunks for barge-in
  - Reuse the existing newAudioTrack() helper in the file (currently
    @Suppress("unused")). 24kHz mono PCM16, USAGE_ASSISTANT,
    CONTENT_TYPE_SPEECH.
  - Close all OnnxTensor instances after use.

acceptance:
  - On reinstall, send a chat prompt → response streams in
  - Auto-speak fires → Kokoro am_adam voice plays the response
  - Volume icon (autoSpeak toggle off) silences mid-playback
  - Diag tab shows "TTS: ready (am_adam, NNAPI)" not "(CPU fallback)"

burn budget:
  This is genuinely 1-2 days of focused work. Difficulty 4 — recommend
  flagship model (Opus 4.6+ / GPT-5 / O3). If a Sonnet-class agent tries,
  expect handoff after M1.2a with phonemizer working and M1.2b still TODO.
  TermuxTtsClient files (TermuxTtsClient.kt) stay in tree — DO NOT
  delete — but are NOT the primary path; they're emergency fallback only.

notes: |
  Reference impl candidates worth looking at first (DON'T reinvent):
    - mjnong/chatapp-v2 (lighthouse-cited, has Kokoro on QNN GPU working
      on Snapdragon 8 Elite — but check whether their phonemization is
      espeak-ng JNI or something else)
    - hexgrad/kokoro (the original PyTorch repo) for vocab/phoneme mapping
    - rhasspy/piper-android (uses espeak-ng JNI for TTS — direct precedent)

  Quick sanity check before committing: run a single hardcoded phoneme
  sequence through session.run() to confirm the model outputs sane audio.
  If THAT works, the rest is wiring. If it doesn't, the model file or
  input shape is wrong — fix that first, don't chase phonemization bugs.
```

### M1.3 — `<think>` block parser + collapsible reply UI

```yaml
status: DONE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/ChatPanel.kt
spec: |
  When enableThinking=true, the model emits "<think>...</think>" blocks
  before its actual reply. Currently these render as literal text.
  - Parse: extract text between <think> and </think> tags from incoming
    stream. Strip from the visible reply.
  - Render: if a turn has thinking content, show a small "Show thinking"
    toggle below the reply that expands to a collapsible Box with the
    thinking text in lighter monospace.
  - Handle streaming: tags may arrive across multiple stream chunks.
    Buffer until </think> seen.
acceptance:
  - brain icon on, ask question → reply shows clean answer + "Show
    thinking" link → tap → expander reveals reasoning trace.
```

### M1.4 — Tool/RAG orchestrator (NPU gets external context)

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 3
depends_on: []
files:
  - horizons/src/main/java/com/horizons/orchestrator/ToolBox.kt (new)
  - horizons/src/main/java/com/horizons/orchestrator/Orchestrator.kt (edits)
spec: |
  Build a ToolBox interface in orchestrator that exposes a small set of
  fetchers the NPU can use as context-prefixers:
  - WebFetcher.fetch(url): suspend → String (truncated to N tokens via okhttp)
  - ShellFetcher.run(cmd): suspend → String (via TermuxBridge)
  - FileReader.read(path): suspend → String
  - ScreenReader.captureAndOcr(): suspend → String (uses ScreenshotCapture)
  - GhFetcher (curl github API for PRs / issues / commits)
  - GcloudFetcher (delegated through TermuxBridge)
  Pattern: before sending the user prompt to NPU, classify intent. If user
  asks "what's on this page" with URL → WebFetcher first → stuff truncated
  content into the user message as "Context:\n<...>\n\nQuestion: <orig>".
  Initial classifier can be: regex on user input ("http*" → web, etc).
  Smarter classifier comes later.
acceptance:
  - "summarize https://news.ycombinator.com" → NPU sees fetched HTML
    (truncated) and produces summary based on real content
  - "what's on my screen" → ScreenReader fires → NPU describes it
```

### M1.5 — JSONL few-shot example bank

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/assets/fewshot/<domain>.jsonl (new)
  - horizons/src/main/java/com/horizons/orchestrator/FewShotBank.kt (new)
  - horizons/src/main/java/com/horizons/model/NexaVlmEngine.kt (edit)
spec: |
  Load N few-shot examples per domain from JSONL assets, prepend them to
  VlmChatMessage[] before the user's question. Format per line:
    {"user": "...", "assistant": "..."}
  Domains to seed (one JSONL each): devops, code-review, summarize,
  rephrase, command-classify.
  NexaVlmEngine.generateStream accepts an optional fewShotKey; ChatPanel
  picks the key based on backend picker selection or a "use few-shot:"
  dropdown.
acceptance:
  - pick "code-review" few-shot → ask "review this snippet" → response
    matches the format established by the examples.
```

### M1.6 — Multi-skill loader

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: [M1.4]   # uses ToolBox to fetch skill files at runtime
files:
  - skills/<domain>/SKILL.md (new — at least 2 sample skills)
  - horizons/src/main/java/com/horizons/orchestrator/SkillLoader.kt (new)
  - horizons/src/main/java/com/horizons/HorizonsApplication.kt (wire)
spec: |
  Skills are markdown with YAML frontmatter (name, description, tags).
  SkillLoader scans skills/ dir, parses frontmatter, returns a list.
  Operator can pick active skill(s) in Settings → activeSkills StateFlow.
  When non-empty, their SKILL.md bodies get appended to the system prompt
  for the next VLM call.
acceptance:
  - drop skills/gcp-cli/SKILL.md → Settings shows checkbox → check it →
    NPU answers questions with that skill's info in scope.
```

### M1.7 — Kokoro voice pack (all 55 voices + picker)

```yaml
status: DONE
claimed_by: null
difficulty: 1
depends_on: []
files:
  - horizons/src/main/java/com/horizons/model/KokoroDownloader.kt
  - horizons/src/main/java/com/horizons/HorizonsApplication.kt
  - horizons/src/main/java/com/horizons/ui/panels/RouterPanel.kt
notes: |
  Ships all 55 voices in one Kokoro download (~28 MB extra, ~108 MB
  total). Voice picker dropdown in Router → TTS section. Voice
  preference persisted in CredentialStore at `kokoro.voice`.
  Switching is instant (release + reload, no re-download). Reads
  selected voice in HorizonsApplication.tryLoadTts(). Done before
  M1.2 lands so when Kokoro synth is wired, the picker's already
  there.
```

---

## Layer 2 — Watchdog backend router

*M2.3 (contract) is the foundation; everything else in L2 depends on it.
L3 cloud routing depends on L2 being alive.*

### M2.3 — WsContract message types

```yaml
status: IN_PROGRESS
claimed_by: Opus 4.8 (Claude Code)
difficulty: 1
depends_on: []
files:
  - shared/src/main/java/com/horizons/shared/ipc/WsContract.kt (edit)
  - shared/src/main/java/com/horizons/shared/ipc/Messages.kt (edit)
spec: |
  Define sealed kotlinx.serialization message hierarchy:
  - Inbound (Horizons → Watchdog): Hello, ChatRequest, KeyGet, KeySet,
    DownloadRequest, TelemetryPing, Shutdown
  - Outbound (Watchdog → Horizons): Welcome, ChatStreamToken, ChatComplete,
    ChatError, KeyValue, DownloadProgress, DownloadComplete, DownloadError,
    Pong
  All under one sealed class WsMessage with @SerialName tags. Port stays
  DEFAULT_PORT = 47821.
acceptance:
  - schema parses and round-trips via kotlinx.serialization
  - both modules can reference the same types via :shared
```

### M2.1 — WS server in WatchdogService

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 3
depends_on: [M2.3]
files:
  - watchdog/src/main/java/com/horizons/watchdog/service/WatchdogService.kt
  - watchdog/src/main/java/com/horizons/watchdog/ws/WsServer.kt (new)
spec: |
  Use the java-websocket library (already on classpath per libs.versions.toml).
  Server binds ws://127.0.0.1:47821 in onCreate, kills in onDestroy.
  Single connection (Horizons is sole client). Echo Hello → Welcome.
  Parse incoming WsMessage, route to handler, send response.
  Initially: just handle ChatRequest by delegating to existing
  providers/ code (will be moved to Watchdog in M2.4/M2.5).
acceptance:
  - install both APKs, Watchdog FGS running, see Welcome logged in
    Horizons after connection
```

### M2.2 — Horizons WatchdogWsClient with reconnect

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: [M2.3]
files:
  - horizons/src/main/java/com/horizons/ipc/WatchdogWsClient.kt (replace stub)
spec: |
  Use okhttp's WebSocket client. Connect on app start to
  ws://127.0.0.1:47821. Send Hello, expect Welcome.
  Exponential backoff reconnect if Watchdog not running: 1s, 2s, 5s, 10s,
  30s, then steady 60s.
  Expose suspend fun send(WsMessage): WsMessage (round-trip with timeout) +
  Flow<WsMessage> for streaming responses.
acceptance:
  - Diag tab shows "Watchdog: connected" when both APKs running
  - kill Watchdog → "Watchdog: reconnecting" → restart Watchdog → reconnects
```

### M2.4 — CredentialStore migration to Watchdog

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: [M2.1, M2.2, M2.3]
files:
  - watchdog/src/main/java/com/horizons/watchdog/keys/KeyVault.kt (new)
  - horizons/src/main/java/com/horizons/provider/CredentialStore.kt (edit)
spec: |
  Move EncryptedSharedPreferences-backed key vault from Horizons to
  Watchdog. Horizons-side CredentialStore becomes a thin client that
  goes via WS: get(key) → KeyGet → KeyValue, put(k,v) → KeySet → ack.
  Migration helper: on first run, Watchdog imports existing Horizons
  keys from the legacy SharedPreferences, then deletes them.
acceptance:
  - keys saved before migration are still readable after both APKs upgrade
  - Diag shows key vault count from Watchdog, not Horizons
```

### M2.5 — Route cloud calls through Watchdog

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 3
depends_on: [M2.1, M2.2, M2.3, M2.4]
files:
  - watchdog/src/main/java/com/horizons/watchdog/router/ModelRouter.kt (new)
  - horizons/src/main/java/com/horizons/orchestrator/Orchestrator.kt (edit)
spec: |
  Move OpenRouterClient, AnthropicDirectClient, VertexClient,
  AIStudioGeminiClient invocation from Horizons orchestrator to a
  Watchdog ModelRouter. Horizons orchestrator becomes: try edge engine,
  if fail send ChatRequest via WS to Watchdog, stream tokens back.
  The provider classes themselves stay in their current shared location
  (referenced from both modules) so we don't dup code.
acceptance:
  - kill Horizons mid-stream → Watchdog finishes the cloud call →
    Horizons reconnects → can replay last response
```

### M2.6 — Persistent download queue

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 3
depends_on: [M2.1, M2.2, M2.3]
files:
  - watchdog/src/main/java/com/horizons/watchdog/download/DownloadQueue.kt (new)
  - horizons/src/main/java/com/horizons/ui/panels/RouterPanel.kt (edit)
spec: |
  Move Moonshine/Kokoro/EdgeModel downloads to Watchdog. WS-streamed
  progress events (DownloadProgress) update a StateFlow that RouterPanel
  observes. Survives Horizons restart (downloads continue), survives
  tab switches (already fixed via app.scope, but this makes it actually
  durable).
acceptance:
  - start download → kill Horizons → restart → download still going,
    progress bar resumes from current position
```

---

## Layer 3 — Cloud routing surface

*L3 doesn't strictly require L2, but works much better with it.
M3.x assumes M2.5 has landed unless noted.*

### M3.1 — Full NamedBackend CRUD UI in Router

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/RouterPanel.kt
  - horizons/src/main/java/com/horizons/ui/panels/BackendEditDialog.kt (new)
spec: |
  ProviderLibrary already supports CRUD on NamedBackend, but Router
  panel has no UI for it. Add: "Add backend" button → dialog with fields
  (id, displayName, wire, baseUrl, modelId, credentialKey,
  isFailoverTarget, dispatcherEligible, purposeHint). Existing backends
  list with edit/delete affordances. Failover toggle.
acceptance:
  - add Anthropic-direct + Vertex-Claude + Vertex-Gemini backends
    through the UI without editing JSON manually
  - Chat backend picker dropdown shows them
```

### M3.2 — Model list fetcher per provider

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: [M3.1]
files:
  - horizons/src/main/java/com/horizons/provider/ModelListFetcher.kt (new)
  - horizons/src/main/java/com/horizons/ui/panels/BackendEditDialog.kt (edit)
spec: |
  When operator picks a wire+baseUrl+credential in BackendEditDialog,
  hit the provider's /models endpoint (OpenRouter, AIStudio, Vertex)
  and populate a dropdown for modelId selection. No more guessing
  model IDs.
acceptance:
  - paste OpenRouter key → modelId dropdown populates with all
    available models, sorted by cost
```

### M3.3 — Key validator (ping /models before saving)

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 1
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/RouterPanel.kt
spec: |
  When operator hits "save" on a credential row, immediately ping the
  provider's lightest endpoint (/models for most). On failure show
  inline error "key rejected: <message>" and refuse to save.
acceptance:
  - paste a malformed/expired OpenRouter key → see "key rejected"
    inline, key not saved
```

### M3.4 — Confidence threshold + classifier before cloud fallback

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 4
depends_on: [M1.4]
files:
  - horizons/src/main/java/com/horizons/orchestrator/Orchestrator.kt
  - horizons/src/main/java/com/horizons/orchestrator/Classifier.kt (new)
spec: |
  Before falling back to cloud (when forcedBackendId == null), use the
  NPU itself as a cheap classifier: "is this question something a 4B
  model on-device can answer well, or does it need a cloud model?".
  Prompt: structured output, return {confidence: 0..1, route: "local"|"cloud"}.
  If confidence < threshold or route=="cloud", skip NPU and go straight
  to cloud failover.
acceptance:
  - "what is 2+2" → local (NPU answers fast)
  - "build me a Vertex AI pipeline that..." → cloud (NPU classifier
    routes to OpenRouter/Vertex)
```

---

## Layer 4 — Action surface (Termux + Tasker + screen)

### M4.1 — Termux round-trip verify + Terminal panel UI

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/TerminalPanel.kt (build out)
spec: |
  TerminalPanel is currently empty. Add: input field + Send button +
  scrolling output area. On send: TermuxBridge.run(input), append
  stdout/stderr to output. "Clear" button. "ls" smoke test button.
acceptance:
  - install Termux from F-Droid + enable allow-external-apps + grant
    RUN_COMMAND perm → type "ls" → see file listing
```

### M4.2 — NPU-classified user query → fires Termux/Tasker action

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 4
depends_on: [M1.1, M4.1, M4.3]
files:
  - horizons/src/main/java/com/horizons/orchestrator/ActionRouter.kt (new)
  - horizons/src/main/java/com/horizons/ui/panels/ChatPanel.kt (edit)
spec: |
  Pipeline: voice/text input → NPU classifies intent:
  {kind: "chat" | "shell_cmd" | "tasker_task", payload: "..."}.
  If shell_cmd: fire TermuxBridge.run(payload), show result inline in chat.
  If tasker_task: app.tasker.runTask(payload), confirm "fired".
  Else: normal chat flow.
acceptance:
  - say "turn off wifi" → tasker_task → fires Tasker profile
  - say "show me my running processes" → shell_cmd → ps output in chat
```

### M4.3 — Tasker outbound smoke test button (Diag)

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 1
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/DiagnosticsPanel.kt
spec: |
  Add a button "Fire Tasker test task" that calls
  app.tasker.runTask("HorizonsTest"). Show success or "Tasker not
  installed" inline. Operator wires the named task in Tasker side.
acceptance:
  - tap button with Tasker installed + HorizonsTest task defined →
    Tasker fires the task → operator's test action runs
```

### M4.4 — Screen capture field test on real device

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/ChatPanel.kt (maybe)
  - horizons/src/main/java/com/horizons/screen/ScreenshotCapture.kt (maybe)
spec: |
  Current code has FGS readySignal handshake (good). Need to verify on
  Razr Ultra that capture doesn't throw SecurityException, and image
  reaches VLM as imagePath. If it does, replace 400ms sleep removal
  with proper signal. If breaks, investigate.
acceptance:
  - tap camera in Chat → consent dialog → grant → indicator shows
    "screenshot pending" → send "describe this screen" → VLM returns
    a real description
```

---

## Layer 5 — Surface polish + observability

### M5.1 — Settings panel build

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/MainActivity.kt
  - horizons/src/main/java/com/horizons/ui/panels/SettingsPanel.kt (new)
spec: |
  Settings tab is "Phase 2/7 stubbed in" — build the real one. Sections:
  - System prompt editor (override DEFAULT_SYSTEM at runtime)
  - Default backend (sticky picker default)
  - Active skills checklist (from M1.6)
  - Few-shot domain selector (from M1.5)
  - Cache TTL preference (5m/1h)
  - Diagnostic log level
acceptance:
  - edit system prompt → next chat call uses the new prompt
  - all settings persist across app restart
```

### M5.2 — Crash log surfacing in Diag

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 2
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/DiagnosticsPanel.kt
spec: |
  Add "Recent crashes" section pulling from CrashLog (already in watchdog/).
  Show stack trace top frames, timestamp, "copy to clipboard" button.
acceptance:
  - force-stop and reopen after a crash → Diag shows the last crash
    with copy-paste-able stack
```

### M5.3 — Cache hit/miss telemetry in Diag

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 1
depends_on: []
files:
  - horizons/src/main/java/com/horizons/ui/panels/DiagnosticsPanel.kt
  - horizons/src/main/java/com/horizons/HorizonsApplication.kt (edit)
spec: |
  Extend cacheStatus to include rolling counts (last 50 calls):
  writes, reads, miss-on-known-prefix events. Show in Diag.
acceptance:
  - after several cloud chat turns, Diag shows "hits: 7  writes: 1
    miss: 0" with percent saved
```

### M5.4 — Accessibility service config XML validation

```yaml
status: AVAILABLE
claimed_by: null
difficulty: 1
depends_on: []
files:
  - horizons/src/main/res/xml/accessibility_service_config.xml (verify)
spec: |
  Lighthouse flagged: AccessibilityService doesn't show up in
  Settings → Accessibility if the meta-data XML is wrong/missing.
  Verify the XML matches lighthouse spec; add deep-link button to
  Settings → Accessibility from the SettingsPanel.
acceptance:
  - install fresh → open Settings → Accessibility → Horizons appears
    in the list with proper label
```

---

## Layer 6 — Control-plane UI overhaul (operator mandate 2026-06-11)

*Operator verdict on the current app: "looks like garbage… nothing works."
This layer REPLACES the Router/Settings surface. Operator supplied theme
references: dark navy/black dashboard, neon blue/purple glow accents,
card-based sections around a central hub motif, status strips along the
bottom (agents online, task progress, guardrail status). Think
"AI orchestration hub" sci-fi control plane, not a settings form.*

### M6.1 — Persistent state engine (P0 — blocks everything)

```yaml
status: AVAILABLE
priority: P0
difficulty: 3
depends_on: []
files:
  - horizons/src/main/java/com/horizons/provider/CredentialStore.kt
  - horizons/src/main/java/com/horizons/state/AppStateStore.kt (new)
  - horizons/src/main/java/com/horizons/ui/panels/RouterPanel.kt
spec: |
  OPERATOR BUG (verbatim): "every time you save something even if you
  switch screens it defaults back, you have to reload, it'll say it's
  loaded but it's not… you got to go and upload the NEXA key again,
  none of that ever saves."

  Root-cause hypothesis: each KeyRow holds remember{}'d local copies of
  value + saved flag — no single source of truth; recomposition shows
  stale state, "saved" UI lies about persistence.

  Fix:
  - AppStateStore: single StateFlow<Map<String,String>> snapshot backed
    by CredentialStore (creds) + DataStore (non-secret prefs: selected
    models, voices, endpoints, layout). ALL UI reads flow from here;
    writes go through it; commit() not apply() for creds.
  - KeyRow & every picker become stateless: value from the flow,
    onSave -> store.put() -> flow re-emits -> UI reflects REAL state.
  - On save of nexa.token: applyNexaToken() + reload, status surfaced.
acceptance:
  - save Nexa key -> kill app -> relaunch -> key present, engine inits
  - switch tabs 5x: every saved field still shows saved value
```

### M6.2 — Three-layer provider control plane

```yaml
status: AVAILABLE
priority: P0
difficulty: 4
depends_on: [M6.1]
spec: |
  Replace RouterPanel with three clearly-separated sections (operator
  design, do not re-litigate):

  LAYER A — ON-DEVICE: models living on the phone.
    - Nexa NPU VLM, Moonshine STT, Kokoro TTS (+ future GGUF/ONNX).
    - Per-model card: status chip (loaded/staged/absent), dropdown to
      pick variant/voice, download/import/upload buttons, reload.
    - Replaces the cryptic HF/Folder/Files/VLM button salad — every
      control labeled by what it actually does.

  LAYER B — CLOUD/API: pluggable providers.
    - Anthropic, OpenRouter, Gemini/Vertex, HF inference, custom URL.
    - Per-provider card: key field (persisted via M6.1), model picker
      fetched from provider (M3.2), test-ping button (M3.3), enable
      toggle feeding the Orchestrator routing table.

  LAYER C — TERMINAL/AGENTS: CLIs + agents reachable on-device.
    - Termux session launcher, installed CLI detection (claude, gemini,
      gh…), per-agent card with launch/attach, output panel.
    - This is the missing "terminal interface" — M4.1 folds in here.

  Every section: dropdowns + uploads, state persists (M6.1).
acceptance:
  - all three layers visible, scrollable, statuses truthful
  - kill+relaunch: every selection intact
```

### M6.3 — Theme + navigation shell (operator reference boards)

```yaml
status: AVAILABLE
difficulty: 3
depends_on: []
spec: |
  Dark sci-fi control-plane theme per operator reference images:
  near-black navy surfaces, neon blue/cyan + purple accent glow,
  card-based panels with thin glowing borders, monospace status
  readouts, bottom status strip (engine/STT/TTS/cache health chips).
  Material3 dynamic color OFF; custom ColorScheme. Bottom nav or rail:
  Chat / Control Plane / Terminal / Diagnostics / Settings.
acceptance: side-by-side with reference boards, operator signs off.
```

### M6.4 — Chat surface rebuild

```yaml
status: AVAILABLE
difficulty: 3
depends_on: [M6.3]
spec: |
  Operator: "actual chat interface is trash." Rebuild: message bubbles
  w/ role styling, streaming token indicator, model/backend chip per
  reply, copy button, image thumbnails for staged screenshots, voice
  state (recording/transcribing) inline, error cards w/ retry — not
  raw "role: text" lines.
```

### M6.5 — Reliability triage: response rate + capture chain

```yaml
status: AVAILABLE
priority: P0
difficulty: 4
depends_on: [M6.1]
spec: |
  Operator: "model responds maybe 20% of the time", screenshot/screen
  share dead, settings placeholder. Instrument Orchestrator.stream
  end-to-end (InteractionLogger): log routing decision, backend tried,
  failure cause. Surface last-failure on chat error card. Fix the top
  3 causes found. Verify ScreenCaptureService FGS consent flow on
  API 35.
acceptance: 10 consecutive chat sends -> 10 replies (cloud fallback
  allowed); screenshot attach works.
```

### M6.6 — Settings architecture: per-layer + global (operator addendum)

```yaml
status: AVAILABLE
priority: P0
difficulty: 3
depends_on: [M6.1]
spec: |
  Settings exist at TWO levels (operator design, 2026-06-11):

  PER-LAYER settings — each Layer A/B/C card has its own gear:
    - On-device model card: inference params (temperature, top_p,
      max tokens, thinking on/off), context size, EP/thread knobs.
    - Cloud provider card: endpoint, model list, per-model sampling
      defaults, spend caps.
    - Terminal agent card: launch args, working dir, env.

  GLOBAL app settings (replaces the placeholder Settings tab):
    - Voice: STT model picker, TTS model + voice picker, speed,
      auto-speak default.
    - Vision: which model handles images/screenshots (NOT everything
      is a VLM — vision routing is explicit, never assumed).
    - Inference: global sampling presets the per-model params inherit
      from — "conservative / balanced / verbose" style presets plus
      raw temperature etc. for advanced mode.
    - Cloud: default failover order, API vs library-load preference.
    - Storage: on-device model library manager — list, size, DELETE
      staged models to reclaim space.
  All persisted via M6.1 AppStateStore.
acceptance:
  - change temperature preset -> next reply reflects it
  - delete a staged model from Storage -> space reclaimed, card
    status flips to absent
```

### M6.7 — Runtime stacks + plug-and-play simple/advanced mode

```yaml
status: AVAILABLE
difficulty: 4
depends_on: [M6.2]
spec: |
  STACKS (operator concept): the Nexa NPU stack is just ONE runtime.
  The control plane treats runtimes as swappable stacks:
    - Nexa stack (NPU, current)
    - llama.cpp stack (GGUF on CPU/GPU)
    - OpenAI-compatible stack (any /v1/chat/completions endpoint)
    - sherpa voice stack (already landed)
  Models declare which stack runs them; some compose (MoE mixtures,
  VLM + text stacked). Adding a stack must not require UI changes —
  cards render from a stack registry.

  SOURCES: first-class HF Hub + GitHub-releases pull (search repo,
  pick file, download into the on-device library) — generalize
  EdgeModelDownloader/VoiceModelFetcher into one ModelSource API.

  SIMPLE vs ADVANCED toggle: simple mode strips the surface to
  chat + a few big switches (truly plug-and-play); advanced exposes
  every knob (stacks, EPs, sampling, endpoints). Per-section
  visibility toggles so the operator can hide what they never use.
acceptance:
  - toggle simple mode -> only chat + essentials visible, state kept
  - add an OpenAI-compatible endpoint in advanced mode -> appears as
    a routable backend in chat with zero code change
```

---

## Cross-layer dependency map

```
M2.3 (contract) ──────────┬─→ M2.1 (WS server)  ┬─→ M2.4 (key vault)
                          ├─→ M2.2 (WS client)  ┴─→ M2.5 (cloud routing)
                          └──────────────────────→ M2.6 (downloads)

M1.4 (ToolBox) ───────────┬─→ M1.6 (multi-skill)
                          └─→ M3.4 (classifier-routed cloud)

M1.1 (Moonshine STT) ─────┬─→ M4.2 (voice→action pipeline)
M4.1 (Termux UI) ─────────┤
M4.3 (Tasker smoke) ──────┘

Everything else is independent.
```

## Recommended parallelization for 5-agent fan-out

| Agent | Layer | First milestone | Difficulty |
|---|---|---|---|
| 1 | L1 | M1.1 Moonshine STT | 3 |
| 2 | L1 | M1.3 think-block UI | 2 |
| 3 | L2 | M2.3 → M2.1 (contract then server) | 1 → 3 |
| 4 | L4 | M4.1 Terminal UI + M4.3 Tasker smoke | 2 + 1 |
| 5 | L5 | M5.4 accessibility check + M5.3 cache telemetry | 1 + 1 |

After this batch:
- Agent 1 → M1.2 Kokoro TTS (harder, needs phonemization decision)
- Agent 2 → M1.4 ToolBox/RAG
- Agent 3 → M2.2 client → M2.4 key vault
- Agent 4 → M4.2 action router (depends on M1.1 from agent 1 being done)
- Agent 5 → M5.1 Settings panel + M5.2 crash log
