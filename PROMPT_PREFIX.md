# Horizons — Rolling Session Prefix

**Purpose.** This file is the volatile per-session cacheable prefix sent
as the Anthropic `system` block on every Claude call routed through
the Horizons orchestrator (direct API or Vertex/anthropic). It pairs
with `CLAUDE_AT_HORIZONS.md` (the stable architecture-of-record):

  - `CLAUDE_AT_HORIZONS.md` — slowly-changing, edited between projects
  - `PROMPT_PREFIX.md` — refreshed pre-session, frozen during a session

The two are concatenated in this order (stable → volatile) so the
stable doc occupies the longest stable byte-prefix and earns the
deepest cache hit window. Edits to this file should be batched
between sessions; mid-session edits invalidate the cache and force a
re-write at 1.25x (5min TTL) or 2x (1hr TTL).

---

## Caching strategy

  - Wire: `system` block array with `cache_control: {type: ephemeral, ttl: "1h"}`
    on the last block. 1h TTL chosen for bursty session pattern (writes
    cost 2x but pay off after ~3 reads).
  - Breakpoint budget: 4 markers max per request. Layout:
      1. (tools, if any)
      2. system block (this file + wiki)
      3. (optional) history-summary breakpoint
      4. (reserved) mid-conversation breakpoint
  - Pre-warm: one `max_tokens: 1` call is fired before any sub-agent
    fan-out so the cache is written before parallel reads begin
    (Anthropic: cache entry only becomes available after the first
    response begins streaming).
  - Verification: `AnthropicDirectClient.lastUsage` /
    `VertexClient.lastUsage` expose `cacheCreationTokens` and
    `cacheReadTokens`. `isCacheHit` returns true when reads > 0.

## LIGHTHOUSE DOC — supersedes prior pivots (read first)

A lighthouse reference doc (deep-dive analysis from the horse's mouth)
arrived end-of-session and **overturns** the earlier termux-api STT/TTS
pivot. The correct locked stack is below. Treat anything in
this file or `CLAUDE_AT_HORIZONS.md` that contradicts it as STALE.

### Locked stack (verified by lighthouse against reference impl `mjnong/chatapp-v2`)

| Layer | Model | Runtime | Hardware | EP config |
|-------|-------|---------|----------|-----------|
| VLM | OmniNeural-4B | Nexa SDK 0.0.24 | Hexagon NPU v79 | `plugin_id = "npu"` (Nexa native, NOT ONNX) |
| STT | Moonshine | ONNX Runtime android | CPU big-cores | `addCpu()`, `setIntraOpNumThreads(2)`, `setInterOpNumThreads(1)` |
| TTS | Kokoro (am_adam) | ONNX Runtime android | Adreno GPU | `addNnapi()` (delegates to QNN GPU on Snapdragon) |

The Termux-API clients (`TermuxTtsClient`, `TermuxSttClient`)
committed in `ef2f1ba` STAY as emergency-fallback **only** —
NOT primary. Primary stack is Kokoro QNN-GPU + Moonshine CPU.
Lighthouse rationale: Adreno GPU and Hexagon NPU are separate
silicon → Kokoro on GPU does NOT contend with OmniNeural NPU.
Termux system TTS gives up Kokoro voice quality and is a regression.

### Non-negotiable Gradle flags (verified present as of commit ec74f8c)

> Earlier in this session I claimed these were "verified present"
> when in fact `useLegacyPackaging` was MISSING — caused every shipped
> APK to fail `NexaSdk.init` with `Cannot find libnexa_plugin_*.so`
> at dlopen time. Fixed in ec74f8c. The 6-hour bug. Don't trust
> "verified" claims that aren't paired with an actual file:line grep.

```kotlin
minSdk = 27
ndk { abiFilters += "arm64-v8a" }
packagingOptions {
    jniLibs.useLegacyPackaging = true
    pickFirsts += setOf(
        "**/libonnxruntime.so",         // Nexa SDK ships this too — must pickFirst
        "**/libonnxruntime4j_jni.so"
    )
}
// AndroidManifest:
//   android:largeHeap="true"
//   android:requestLegacyExternalStorage="true"
```

### Nexa model_path (critical correction)

Lighthouse explicitly: Nexa SDK needs the **file path** to
`files-1-1.nexa`, NOT the folder. Commits show flip-flop;
verify the final state of `NexaVlmEngine.kt` model_path points to
the .nexa file. If folder, fix or have the folder-walker resolve
the .nexa explicitly. Target: `/data/data/<pkg>/files/models/OmniNeural-4B/files-1-1.nexa`.

### ORT EP rules (anti-foot-guns)

  - **`addCpu()` on Moonshine is FORCED EXCLUSION, not a default.**
    Omitting it lets ORT-android auto-enable NNAPI on Snapdragon,
    which steals the HTP/NPU context Nexa has the exclusive lock on
    → NPU driver crashes / model-load failures / non-deterministic
    hangs. Always register `addCpu()` explicitly on the Moonshine
    session. Same applies to any other ONNX model NOT intended for
    NPU/GPU.
  - **Do NOT** `addNnapi()` or `addQnn()` on the Moonshine STT
    session — that steals NPU from Nexa or contends with Kokoro on
    the GPU.
  - **Do NOT** try `addQnn()` directly in ORT-android Maven —
    method doesn't exist. Use `addNnapi()` and let the Snapdragon
    driver delegate to QNN GPU.
  - Each model = separate `OrtSession` with separate `SessionOptions`.
    No per-model EP switching within one session.
  - Verify Kokoro session at runtime: read
    `session.sessionOptions.executionProviders`. If it contains
    `CPUExecutionProvider` and NOT `NNAPIExecutionProvider`, QNN
    init failed and Kokoro is fighting Moonshine for CPU.

### Resource segregation map (Snapdragon 8 Elite)

| Silicon | Tenant | Mechanism |
|---------|--------|-----------|
| Hexagon NPU | Nexa SDK / OmniNeural-4B | `plugin_id = "npu"` (native QNN/HTP, NOT ONNX) |
| Adreno GPU | ORT / Kokoro TTS | `addNnapi(NnapiOptions { executionMode = PREFER_SUSTAINED_SPEED })` |
| Kryo CPU (Oryon, 8 cores) | ORT / Moonshine STT | `addCpu()` + `setIntraOpNumThreads(2)` + `setInterOpNumThreads(1)` + `setMemoryPatternOptimization(true)` |

Moonshine is ~5-layer LSTM-class encoder, RTF << 0.1 on Oryon cores.
CPU is the correct silicon, not a fallback. The 6 remaining cores
handle UI, WebSocket, Watchdog, and system.

### Memory arena shrinkage (both sessions)

```kotlin
options.setMemoryPatternOptimization(true)
// ORT 1.17+: options.addConfigEntry("session.memory_arena_shrinkage", "10")
```
Without this, the default arena grows forever and OOMs alongside
the 4B context.

### VAD layer — missing

Lighthouse flags: no Voice Activity Detection in front of
Moonshine = burn CPU transcribing silence. Add **Silero VAD v5**
(small ONNX) before Moonshine. Open task.

### 9 weak links flagged by lighthouse (queue)

  1. Key stored as String in memory (Retrofit headers) — switch to
     OkHttp Interceptor pulling from EncryptedSharedPreferences
     at request time.
  2. OAuth refresh-token expiry for Cloud Shell — needs refresh loop.
  3. Model router burns cloud credits on simple queries — add
     confidence threshold + complexity classifier before fallback.
  4. WebSocket 10s timeout vs cloud 30s+ inference — bump to 60s,
     ping/pong every 5s, stream partial tokens.
  5. API key validation missing — ping `/models` before saving.
  6. NPU thermal throttle → permanent cloud fallback — retry
     with 30s backoff, track thermal state.
  7. Credential scope confusion (AI Studio key in Vertex slot,
     etc.) — lock endpoint↔key-type pairs in UI.
  8. Cloud Shell 20-min idle / 12-hr hard limit — treat shell as
     stateless, no `cd`/env assumptions.
  9. Network state not checked before cloud — `ConnectivityManager`
     guard, skip cloud entirely if offline.

### Reference repo

`mjnong/chatapp-v2` — has Kokoro ONNX + NPU pipeline working on
Snapdragon 8 Elite. Use as a code-shape reference for any session
options or model-loading questions.

### Cloud architecture (per lighthouse)

OpenRouter + Vertex + AI Studio + Cloud Shell all live in
**Watchdog**, not Horizons. Horizons holds the UI + Model Library
CRUD. Watchdog holds the keys, the router, and all network IO.
WebSocket `ws://127.0.0.1:47821` is IPC. ProviderLibrary today is
JSON-file based; lighthouse spec is Room/SQLite with `ModelConfig`
+ `ApiKey` tables. Migration to Room is a future task, not blocking.

---

## ARCHITECTURE PIVOT — Skills become primary; STT/TTS via termux-api

Two structural changes locked at end-of-session:

### Skills architecture is primary going forward

Every agent (Horizons sub-agents + the main NeuralMash builder) will
use Anthropic Skills (SKILL.md frontmatter + Markdown) as the primary
memory/context/tools/tasks bundle, customized per agent + workflow.
The wiki (`CLAUDE_AT_HORIZONS.md`) and rolling prefix (this file) stay
as source-of-truth for humans, but the runtime memory layer for each
agent is its own Skill folder under `skills/<agent-name>/SKILL.md`.

This means:
  - Each agent gets its own `skills/<name>/SKILL.md` (eventually).
  - The system prompt references the Skill by name; the wiki contents
    get inlined or referenced from inside the Skill.
  - Skills are interoperable across Claude Code, Codex, Cursor, the
    managed-agents runtime. Vendor-portable.
  - First step: keep using `skills/horizons-wiki/SKILL.md` for the
    NeuralMash builder. Future agents get their own folders.

### STT/TTS — **STALE pivot, do NOT follow** (kept for audit only)

~~Earlier this session I documented "Python + onnxruntime in Termux"
then pivoted to `termux-api` shell-out as primary.~~ **OVERTURNED
by the lighthouse doc above.** Primary path is **ONNX Runtime
Android** (Moonshine on CPU EP, Kokoro on NNAPI→QNN-GPU EP) — see
the Locked Stack table at the top.

The two Kotlin clients `TermuxTtsClient.kt` and `TermuxSttClient.kt`
remain in the tree as **emergency fallback only** (e.g. if QNN GPU
init crashes and Kokoro can't reach the speaker). They are NOT the
primary path. Do NOT gut `MoonshineSttEngine.kt`,
`KokoroTtsEngine.kt`, or the `onnxruntime-android` Gradle dep.

## Next-session burn order

1. **Rotate the leaked Anthropic API key** (still live; paste new
   one into env vars panel or chat-then-rotate).
2. **On-device foundation verify (text only):**
   - Install latest APK from `latest-debug` release.
   - Router → enter Nexa coin → HF download OmniNeural-4B → wait for 13/13.
   - Chat panel: type a prompt, hit Send. Should answer on NPU.
   - Diag panel: confirm engine = `ready: nexa`, no error.
   - Report failures with Diag error string + folder listing.
   - Verify Nexa `model_path` points at the `files-1-1.nexa` FILE
     (lighthouse correction), not the folder.
3. **Wire real ONNX inference into the engine stubs** (the actual
   work — current `MoonshineSttEngine.transcribe()` and
   `KokoroTtsEngine.speak()` are placeholder stubs):
   - Moonshine session: `OrtSession.SessionOptions().apply { addCpu(); setIntraOpNumThreads(2); setInterOpNumThreads(1); setMemoryPatternOptimization(true) }`.
   - Kokoro session: `OrtSession.SessionOptions().apply { addNnapi(NnapiOptions().apply { executionMode = NnapiExecutionMode.PREFER_SUSTAINED_SPEED }) }`.
   - Verify Kokoro at runtime: read
     `session.sessionOptions.executionProviders`, fail loud if
     `NNAPIExecutionProvider` is missing.
   - **Do NOT** add `addNnapi()` or `addQnn()` to Moonshine — it
     steals NPU from Nexa.
4. **Add VAD (Silero VAD v5)** in front of Moonshine — lighthouse
   flagged: no VAD = burn CPU transcribing silence.
5. **Smoke tests** (after #3 + #4):
   - Mic button → AudioRecorder PCM → Moonshine returns real
     transcript → lands in input.
   - Send → response → Kokoro synthesizes → AudioTrack plays.
6. **Termux round-trip verify** (separate from STT/TTS — for
   the Tasker / dispatcher / Terminal panel paths):
   - Add a "Send `ls`" button in TerminalPanel that fires through
     `TermuxBridge.run("ls")` and surfaces the result.
7. **Screenshot capture field test:**
   - The current code has a 400ms `delay()` after FGS start, before
     `getMediaProjection`. If that throws SecurityException on the
     Razr, replace with a bound-service ready signal.
8. **Tasker outbound smoke test:**
   - Diag panel button that fires `app.tasker.runTask("HorizonsTest")`
     against a known Tasker profile to confirm the broadcast lands.

## Open known issues

- **Moonshine inference = stub** (returns `"[moonshine: N samples...]"`).
  Replace with real ONNX inference per burn-order #3.
- **Kokoro synth = stub** (logs + no-op). Same fate.
- **No VAD** in front of Moonshine — burn-order #4.
- **Cloud agent MCPs** stripped pending vault wiring.
- **CI Node 20 actions** deprecation Sept 16 2026 — bump versions.
- **Leaked API key from chat is still live.** Rotate.

## Session state — current as of latest commit

Tonight's wins (2026-06-05 → 2026-06-06):

Code shipped to `main`:

  - `AnthropicDirectClient` with `systemPrompt`, `cache_control: ephemeral`,
    `lastUsage` (cacheCreation/cacheRead tokens), and `preWarm()`.
  - `VertexClient` (anthropic publisher) gained the same caching surface.
  - `CLAUDE_AT_HORIZONS.md` bundled into `horizons/src/main/assets/`.
  - `HorizonsApplication.wikiSystemPrompt` lazy-loads the asset.
  - `HorizonsApplication.preWarmAnthropic()` + `cacheStatus` StateFlow
    (states: idle, warming…, `write Nt (1h)`, `hit Nt (read)`, errors).
  - `Orchestrator` takes `systemPromptSupplier: () -> String` and threads
    it through `ProviderFactory.build`. Pre-existing ctor mismatch fixed.
  - `ProviderFactory` injects systemPrompt into Anthropic + Vertex/anthropic.
  - `InteractionLogger.logResponse` schema extended with
    `cache_creation_tokens` / `cache_read_tokens` JSONL fields.
  - `RouterPanel` got: Anthropic API key row, Pre-warm (1h) / Pre-warm (5m)
    buttons, live cache status line.
  - `AudioRecorder.kt`: removed `@Synchronized` on suspend funs that was
    failing CI (AtomicBoolean `running` is the actual guard).

Cloud agent (Anthropic managed-agents 2026-04-01):

  - Agent ID `agent_01RaU3nbhVGcFi9ZRcCinT9r` is at **v6**.
  - System prompt source: `agents/neuralmash-builder.system.md`.
    Edit it, then `ant beta:agents update --version <N>` against piped YAML
    `system: "@file://agents/neuralmash-builder.system.md"`.
  - MCPs stripped (github/drive/bigquery were unauthenticated; re-add once
    a vault is wired).
  - Built-in `agent_toolset_20260401` retained.
  - Environment: `env_01Srnj2osSiRfd1AGBaxDoVH` (`neuralmash-edge-moe-env`,
    unrestricted networking, $0.08/hr active).
  - Pre/post manifest snapshots in `agents/.snapshots/`.

Verified cache behaviour (Anthropic platform):

  - 2-turn session: turn 1 wrote 6956t; turn 2 read 6956t (0.1x cost).
  - Cross-session: brand-new session on same agent hit 3382t on first
    turn (server-side prefix cache survives between sessions).
  - Managed-agents service defaults to `ephemeral_5m` cache tier, NOT
    `ephemeral_1h`. Override only matters for our direct API path; the
    managed-agents path is platform-managed.

New artifacts in repo root:

  - `skills/horizons-wiki/SKILL.md` — open SKILL.md standard wrapper.
  - `agents/build-runner.yaml` — managed-agents YAML stub (slot 1).
  - `agents/neuralmash-builder.system.md` — source-of-truth for deployed
    agent's system prompt.
  - `agents/.snapshots/` — pre/post YAML snapshots.

What still needs to happen next:

  - **Rotate the Anthropic API key** leaked earlier in chat (still live).
  - Custom MCP design (long-term: hosted on Cloud Run, OAuth out to GCP,
    bridges Horizons ↔ console; deferred until awake-planning session).
  - Vault wiring on Anthropic console if managed-agent MCPs are wanted
    back (github/drive/bigquery).
  - Sub-agent slots 2/3/4 (wiki-groom, diagnostics, code-review) — only
    slot 1 has YAML; the others are blank.
  - Surface `cacheStatus` in a real Diagnostics panel (issue #10).
  - Wire `AnthropicDirectClient.lastUsage` into `InteractionLogger`
    calls on each chat response (logger has the fields, no call site).
  - Method 2 (compile/export workflow): end-of-session agent that
    grooms the conversation into a fresh `PROMPT_PREFIX.md` for next
    boot.
  - CI: bumping Node 20 actions to Node 24 before Sept 16 2026 deprecation.

## Sub-agent assignments (4-agent fan-out template)

Every sub-agent reads the **identical** system bytes (this file +
`CLAUDE_AT_HORIZONS.md`). Per-agent role/task divergence happens in
the first user message, which is *not* cached. This is the design
that makes one cache write serve N parallel reads.

| Slot | Role | Primary scope | Tools the agent expects |
|------|------|---------------|-------------------------|
| 1 | build-runner | Compile, lint, test, CI watch | bash, read, grep, glob |
| 2 | wiki-groom | Edit `PROMPT_PREFIX.md` between sessions | read, edit, write |
| 3 | diagnostics | Inspect logs, surface cache stats, regressions | read, grep, glob |
| 4 | code-review | Review diffs before push | read, grep |

Slot composition is a stub — refine after first dispatch.

## Hard rules for every sub-agent

  - Working branch: `main`. Never push to `main`
    without explicit user permission.
  - Never commit credentials. `debug.keystore` is the documented
    exception (public-by-design for stable APK signatures).
  - Use the `--no-verify` flag only if the user asks. If a hook fails,
    fix the underlying cause.
  - Edits to `CLAUDE_AT_HORIZONS.md` and this file must be batched
    between sessions, not during.
  - On-device fleet: Moonshine for STT, Kokoro for TTS, OmniNeural-4B
    on Hexagon NPU v79 via Nexa SDK. No Python sidecar, no Vulkan, no
    Ollama, no `nexa serve`, no LiteLLM proxy.
  - Cloud failover: OpenRouter is the singular auto-failover. Vertex,
    Anthropic direct, AI Studio are explicit-pick only.

