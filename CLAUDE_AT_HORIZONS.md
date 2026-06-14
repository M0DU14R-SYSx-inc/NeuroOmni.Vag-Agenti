# CLAUDE @ Horizons — Architecture Wiki

> **Stable architecture-of-record.** Slowly changing. Edit between sessions,
> not during. Pair with the rolling `PROMPT_PREFIX.md` (volatile) and
> `SOTU.md` (per-session snapshot).
>
> Last full rewrite: 2026-06-14 (greenfield pivot). Previous version
> archived at `.archive/CLAUDE_AT_HORIZONS.2026-06-14.md`.

---

## Product

**Horizons** is a native Android Kotlin/Compose app for the Motorola Razr
Ultra 2025 (Snapdragon 8 Elite, Hexagon NPU v79, Adreno GPU, 16 GB RAM,
arm64-v8a). It runs on-device AI organized around a **9-boundary stack**
with three control surfaces:

1. **On-device model runtimes** — Nexa SDK loading Gemma-4-E4B-IT on GPU,
   OmniNeural-4B + Parakeet TDT/ASR on NPU, VoxSherpa as the system TTS node.
2. **Cloud frontends** — separate process / separate app. Hot-swap model
   loading, API access, CLI access. The main app reaches them through a
   capability adapter, not by knowing they exist.
3. **Per-tile terminals** — every UI tile carries its own embedded shell so
   the operator can patch code in-app without leaving the app.

Hard constraints: Razr 2025 only. arm64-v8a. No Pi/Jetson, no Vulkan, no
Python sidecar.

---

## The 9 hard boundaries

Order is load-bearing. No omissions, no workarounds, no side-steps.

| # | Layer | Where it lives |
|---|---|---|
| 1 | Nexa Studio | Model source (off-device, curated by operator) |
| 2 | Nexa SDK / CLI | Runtime entry — Kotlin in-process on Android |
| 3 | Nexa Server | On-device serving (in-process; **not** a daemon) |
| 4 | Nexa ML | Runtime layer |
| 5 | Qualcomm QNN SDK | NPU + GPU compute, reached via Nexa plugin layer |
| 6 | Android Accessibility Service API | System surface — overlay + injection |
| 7 | Gemma-4-E4B-IT | GPU (Adreno), via Nexa `cpu_gpu` GGUF plugin, `device_id="dev0"` |
| 8 | OmniNeural-4B + Parakeet TDT/ASR | NPU (Hexagon), `plugin_id="npu"`, co-resident |
| 9 | VoxSherpa | System TTS node (CPU), `com.CodeBySonu.VoxSherpa` via `android.speech.tts.TextToSpeech` |

Concurrent residency of (8) and (7) across NPU + GPU is the open question
gating the final architecture cut — see `wiki/FAILURE_LOG.md` /
`wiki/FORK_DECISIONS.md`.

## Truman Show principle

Models perform. They **never** know:

- a backend router exists
- other models exist on other devices
- cloud failover paths exist
- type labels (VLM / MLLM / STT / ASR / TTS) gate their behavior

Engine code surfaces one capability: `loadModel(name, plugin, device) → Engine`.
The Engine exposes `infer(input) → output`. No `if (isVlm)`. No `if (isStub)
fallToCloud()`. Routing happens **outside** the model code, in adjacent
control surfaces the model can't see.

---

## Three control surfaces

### A. On-device runtimes (boundaries 1-5, 7-9)

Code lives under `horizons/src/main/java/com/horizons/core/nexa/`. Public
shape (no type labels):

```kotlin
NexaModelLoader.load(context, spec) → NexaEngine
NexaEngine.infer(NexaInput) → NexaOutput
NexaEngine.stream(NexaInput) → Flow<String>
```

`spec.pluginId` is the only branch point and it's an implementation detail
of the loader, not a public type. Callers don't read it.

### B. Cloud frontends — separate process / separate app

Cloud routing, hot-swap model loading, third-party API access, and any CLI
that's not on-device live **outside** the main Horizons app. Main app
reaches them through a capability adapter that returns `NexaEngine`-shaped
handles, so model code can't tell local from remote.

Hot-swap means: operator toggles a tile setting, the adapter unloads the
current engine + loads the next, callers keep their `NexaEngine` interface.

### C. Per-tile terminal

Every tile in the UI carries its own embedded shell (Termux-backed via
boundary 6 + `core/shell/TaskerBridge`). The operator can patch the tile's
own code in-app — useful when the device is the only available editing
environment. Terminal scope is the tile's package; cross-tile changes go
through Git like normal.

---

## Vision + action pipeline (canonical flow)

Horizons sees the screen through **two parallel inputs** and emits actions
through **one output**. Both inputs feed an in-process orchestrator that
speaks OpenAI-compatible HTTP over `127.0.0.1` — even on-device, even
though there's no separate daemon. The HTTP layer is what makes the
Truman Show work: the cloud-frontend app speaks the same surface, so the
model layer cannot tell local from remote.

```
[USER ACTIONS / TILE INTERACTION]
                │
                ▼
   [ANDROID FOREGROUND SERVICE RUNTIME]
   ┌───────────────────────────────────────────────────────────────┐
   │ [Android MediaProjection API]   [Accessibility Service]       │
   │           │ RGBA_8888                    │ AccessibilityNode  │
   │           ▼                              ▼ tree extractor     │
   │  [ImageReader → JPEG 85%]    [Layout matrix → text]           │
   │           │                              │                    │
   │           ▼ base64 frame                 ▼ text payload       │
   └───────────┬──────────────────────────────┬────────────────────┘
               │ POST /v1/chat/completions    │ POST /v1/.../merge
               ▼                              ▼
   ┌───────────────────────────────────────────────────────────────┐
   │       LOCALHOST ORCHESTRATION ENGINE  (127.0.0.1:1234)        │
   │       In-process Ktor server → NexaEngine adapter             │
   └───────┬───────────────────────────────────────┬───────────────┘
           │ (1) image buffer                      │ (3) merged text
           ▼                                       ▼
   ┌──────────────────────────┐     ┌──────────────────────────────┐
   │      Hexagon NPU         │     │          Adreno GPU          │
   │  Nexa SDK plugin=npu     │     │  Nexa SDK plugin=cpu_gpu     │
   │  OmniNeural-4B           │     │  Gemma-4-E4B-IT (GGUF)       │
   │  Task: vision facts      │     │  Task: agentic reasoning     │
   └──────────────┬───────────┘     └──────────────┬───────────────┘
                  │ (2) structural layout text     │ (4) action JSON
                  └────────────────┬───────────────┘
                                   ▼
                  ┌────────────────────────────────────┐
                  │   ACCESSIBILITY WORKER RELAY       │
                  │   Native tap + type via boundary 6 │
                  └────────────────────────────────────┘
```

### In-process HTTP, not a daemon

`nexa serve --port` is documented as a separate process on PC/Linux. On
Android we **do not** spawn a daemon — we embed a Ktor HTTP server
inside the foreground service that proxies OpenAI-compatible requests
to in-process `NexaEngine.infer()` / `.stream()` calls. The port is real
(`127.0.0.1:1234`); the server is a thread, not a process.

Why uniform HTTP at all (instead of just Kotlin function calls):

1. **Truman Show:** the orchestrator code is identical for local vs.
   cloud. Cloud-frontend app exposes the same OpenAI-compatible surface
   on a different host. Engine code cannot tell which.
2. **Hot-swap:** swapping `OmniNeural-4B` ↔ `Gemma-4-E4B-IT` ↔ a cloud
   model is changing the upstream HTTP target. Nothing downstream knows.
3. **Per-tile terminal debugging:** operator can `curl 127.0.0.1:1234/v1/models`
   from any tile's embedded shell. Visible state, not invisible function
   calls.
4. **Multi-client:** the IME, Accessibility relay, and chat tile all hit
   the same in-process server. One inference queue, one cache.

### Dual-input rationale

- **MediaProjection** gives ground-truth pixels for the NPU vision model
  (OmniNeural-4B). Resolved at 1024px max edge, JPEG q=85, to keep prefill
  under NPU OOM (see `wiki/FAILURE_LOG.md` §Screenshot crashes NPU).
- **Accessibility Service** gives the structural tree for free — exact
  element IDs, bounds, content descriptions. No vision tokens spent.
- **Both at once** = the GPU reasoning model receives "what's on screen
  semantically" plus "what it looks like literally." That's how the
  reasoning model can ground "tap the blue button" against the tree
  without hallucinating element coordinates.

### Action output

Accessibility Worker Relay is the **same** Accessibility Service that
extracted the tree — boundary 6 is the surface for both directions.
It consumes action JSON like:

```json
{"op": "tap", "node": "view_id/send_button"}
{"op": "type", "node": "edit_text/message_input", "text": "hello"}
```

Models emit; relay executes. Truman Show: models never see the result;
the next perception cycle's tree-extraction is how they "see" what
happened.

---

## Repo layout (current)

```
NeuroOmni.Vag-Agenti/
├── SOTU.md                       # session pickup #1 — state of the union
├── PROMPT_PREFIX.md              # session pickup #2 — streamlined pointers
├── EXECUTION_BOARD.md            # session pickup #3 — live milestones
├── CLAUDE_AT_HORIZONS.md         # this file (stable wiki)
├── GREENFIELD_PLAN.md            # rebuild scope + salvage/scrap list
├── README.md                     # entry banner
├── AGENT_SETUP_WIZARD.md         # one-page deployment guide
├── HANDOFF.md, MANAGED_AGENT_KICKOFF.md,
│   SETUP_PROMPT.md, UNIVERSAL_PREFIX.md, UNIVERSAL_LAUNCH.md
├── wiki/                         # index folders, maintenance docs, failure log
├── rules/                        # hard rules (cache, git hygiene, at-bat)
├── skills/
│   ├── horizons-wiki/SKILL.md    # wiki bundle skill
│   └── project-memory/SKILL.md   # project memory skill (NEW)
├── agents/                       # managed-agent system prompts + snapshots
├── docs/                         # deep-dive references
├── .archive/                     # superseded versions of wiki + prefix
└── horizons/
    └── src/main/java/com/horizons/
        ├── core/                 # NEW: greenfield root (Truman Show)
        │   ├── nexa/             # opaque loader + engine + spec
        │   └── state/            # AppStateStore (single source of truth)
        └── … (legacy packages — being phased out per GREENFIELD_PLAN.md)
```

---

## State management

`AppStateStore` (`core/state/AppStateStore.kt`) is the single source of
truth for all persisted app state: credentials, toggles, picker selections,
tile config. Backed by `EncryptedSharedPreferences`. Exposes
`StateFlow<Map<String, String>>` so composables collect, not cache.

**The phantom-save rule:** UI code **never** holds a local
`remember { mutableStateOf(stored) }` for a persisted value. That diverges
from the store on the next read and was the root cause of the
"keys reset every restart" bug. Read from the flow; write through `put`.

Well-known keys live as constants on the store companion. Don't sprinkle
string literals.

---

## Sessions, hand-off, and the three pickup files

Going forward, every session opens with three files (in order):

1. **`SOTU.md`** — State of the Union. One screen. What just happened,
   what's next, what's stuck. Drafted by the agent at session close;
   committed by the operator.
2. **`PROMPT_PREFIX.md`** — Streamlined pointers + rules. No inline content.
3. **`EXECUTION_BOARD.md`** — live milestone list with claims dashboard.

These three are the contract. Anything else loads only if the at-bat
needs it.

### Project memory

A skill — `skills/project-memory/SKILL.md` — bundles the three pickup files
+ this wiki as a single context block. New agents that need full project
memory invoke the skill; agents with narrow scope skip it and pay less in
tokens.

---

## Tiles, terminals, and code-in-app

Each tile is a self-contained UI surface with:

- its own composable tree
- its own `AppStateStore` namespace (`tile.<name>.*`)
- an embedded terminal pane (shell access scoped to the tile's package)
- a model-selector hot-swap dropdown (calls the capability adapter)

The terminal makes the app self-modifying when the operator has no laptop
handy. Edits land in the tree, get committed through the normal Git flow
(not auto-pushed — operator reviews before push).

---

## Out of scope (this version)

- Watchdog secondary process and its loopback WS server — being re-evaluated
  during greenfield. Old design is in `.archive/CLAUDE_AT_HORIZONS.2026-06-14.md`.
- Sherpa-onnx voice stack (Moonshine + Kokoro) — replaced by Parakeet (NPU)
  + VoxSherpa (system TTS).
- Orchestrator with cloud failover — moved to the separate cloud-frontend
  app per the Truman Show principle.
