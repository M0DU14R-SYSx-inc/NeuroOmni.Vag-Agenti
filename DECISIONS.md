# Decisions log

Append-only ADR-style record of architectural cuts. Cheaper to skim than
the wiki when you only need the **why**, not the full picture.

Format per entry:

```
## YYYY-MM-DD — <decision title>

- **Context:** what forced the choice.
- **Decision:** what we picked.
- **Alternatives considered:** what we rejected + 1-line why.
- **Consequences:** what this commits us to / breaks.
- **Status:** active / superseded by <link>.
```

Rule: a decision goes here when it would surprise a future agent reading
the code. "We use Kotlin" doesn't. "We dropped sherpa-onnx" does.

---

## 2026-06-14 — Greenfield rebuild on `claude/jolly-lamport-5cJJ4`

- **Context:** Cumulative failures (sherpa-onnx ORT collisions, Orchestrator
  cloud failover leaking, EdgeModelFactory type labels, phantom-save bug,
  mic-button crashes) made surgical fix more expensive than restart.
- **Decision:** Rebuild app code around the 9-boundary stack + Truman Show
  principle. Preserve framework docs (wiki, rules, skills, agents).
- **Alternatives considered:**
  - Surgical fix per failure — rejected; root causes were design, not bugs.
  - Full repo reset — rejected; framework docs are the real gold.
- **Consequences:** Old packages stay live until G2 + G3 land. APK keeps
  building during transition. Salvage list in `GREENFIELD_PLAN.md`.
- **Status:** active.

## 2026-06-14 — Truman Show as load-bearing principle

- **Context:** Old `Orchestrator` branched on `is StubEdgeModel` to fall
  back to cloud. Engine code leaked backend awareness. New models needed
  new type labels.
- **Decision:** Engine code surfaces one capability:
  `NexaModelLoader.load(spec) → NexaEngine`. `NexaEngine.infer(input)`.
  No type labels, no backend awareness, no routing inside model code.
- **Alternatives considered:**
  - Tagged-union of engine types — rejected; same labeling problem.
  - Strategy pattern with explicit backend — rejected; leaks abstraction.
- **Consequences:** Cloud / hot-swap / CLI move to a capability adapter
  outside the model layer. Adapter returns `NexaEngine`-shaped handles.
- **Status:** active.

## 2026-06-14 — Three pickup files contract

- **Context:** Agents were starting cold + re-reading 12 root MD files.
  No clear "what changed since last session" surface.
- **Decision:** Every session opens with `SOTU.md` → `PROMPT_PREFIX.md`
  → `EXECUTION_BOARD.md`. Pickup files are the contract; everything else
  loads only if the at-bat needs it.
- **Alternatives considered:**
  - Single mega-prefix — rejected; pays token cost for context not needed.
  - Wiki-only (no SOTU) — rejected; wiki is stable, not per-session signal.
- **Consequences:** SOTU must be drafted at session close (rule:
  AT_BAT_PROTOCOL §4). Skipped close-out is a flagged failure.
- **Status:** active.

## 2026-06-14 — sherpa-onnx voice stack scrapped

- **Context:** See `wiki/FAILURE_LOG.md` (Moonshine int8 + Kokoro GPU).
- **Decision:** Parakeet TDT/ASR on Hexagon NPU via Nexa SDK; VoxSherpa
  as system TTS via `android.speech.tts.TextToSpeech`.
- **Alternatives considered:**
  - Bundle ORT 1.24.3 alongside Nexa's 1.22 — rejected; .so collisions
    cost a week.
  - Whisper-via-Termux — rejected; Python sidecar violates the stack.
- **Consequences:** Sherpa AAR comes out (G8). Parakeet wrapper API
  needs confirmation (open question).
- **Status:** active.

## 2026-06-14 — In-process OpenAI-compatible HTTP, not Kotlin function calls

- **Context:** Operator's vision-layer hypothesis showed `nexa serve --port 1234`
  behind the orchestrator. On Android there's no separate daemon, so the
  literal `nexa serve` doesn't apply — but the *shape* (HTTP at `127.0.0.1`)
  has architectural value.
- **Decision:** Embed a **Ktor HTTP server in the foreground service
  process** that exposes an OpenAI-compatible surface (`/v1/chat/completions`,
  `/v1/models`, etc.) and proxies to in-process `NexaEngine` calls. Port
  `127.0.0.1:1234`. The cloud-frontend app speaks the same surface on a
  different host.
- **Alternatives considered:**
  - Direct Kotlin function calls (`engine.infer(input)`) — rejected;
    breaks Truman Show because cloud-path code would diverge from local.
  - Spawn `nexa serve` as a sub-process — rejected; Android doesn't allow
    that pattern and the SDK doesn't ship a binary for it.
- **Consequences:**
  - Local + cloud are wire-compatible. Hot-swap is a host change.
  - Per-tile terminal can `curl 127.0.0.1:1234/v1/models` for debugging.
  - IME, Accessibility relay, and chat tile share one in-process queue.
  - Adds Ktor dependency (~600 KB); routing layer in the foreground service.
- **Status:** active. Implementation lands during G3 / G6.

## 2026-06-14 — Decompile the AAR before writing Nexa SDK code

- **Context:** Sub-agents kept reverse-engineering the Nexa Android SDK
  API from call-site patterns in production apps. That misses optional
  parameters, nullability, `Result<T>` return wrappers, and mangled
  suspend signatures. One at-bat shipped silently no-op'ing code.
- **Decision:** `javap` against the unzipped AAR is the first step on any
  Nexa SDK at-bat. Procedure documented in `rules/AAR_DECOMPILE.md`.
- **Alternatives considered:**
  - Trust scraped docs / agent research — rejected; 5 failed spin-ups.
  - Wait for Nexa to publish KDoc — rejected; out of our control.
- **Consequences:** ~30 seconds added to any Nexa-touching at-bat.
  Eliminates a recurring failure class.
- **Status:** active.

## 2026-06-14 — AppStateStore is the single source of truth

- **Context:** `KeyRow` in old `RouterPanel` held local
  `remember { mutableStateOf(stored) }` that diverged from
  `CredentialStore` on next read. Operator's keys "reset every restart."
- **Decision:** `AppStateStore` exposes `StateFlow<Map<String, String>>`
  backed by `EncryptedSharedPreferences`. Composables collect; never
  cache. Hard rule: no `remember` of a persisted value.
- **Alternatives considered:**
  - Fix `KeyRow` in place — rejected; pattern was repeated in 8 panels.
- **Consequences:** All UI panels migrate at G4. Old `CredentialStore`
  comes out at G8.
- **Status:** active.
