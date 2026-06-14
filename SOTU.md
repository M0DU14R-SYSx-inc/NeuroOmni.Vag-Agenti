# State of the Union — Horizons

> **Pickup file.** Read this first every session. One page. Updated at the end
> of every session (operator pushes; agent drafts). If a session ends without
> a SOTU bump, the next agent flags it.
>
> Format is fixed — do not add sections. Bump dated line + replace the four
> blocks. Keep total ≤ 1 screen.

**Snapshot:** 2026-06-14 17:35 UTC · branch `claude/jolly-lamport-5cJJ4`

> ⚠️ **DEAD STACK — DO NOT REINTRODUCE.** Sub-agents have wandered back to
> these. Reject any output that names them as live:
>
> Moonshine STT · Kokoro TTS · sherpa-onnx · ORT-Android NNAPI delegate ·
> `EdgeModelFactory` type labels · `Orchestrator` cloud failover ·
> `ProviderLibrary` · in-app cloud clients · `ChatPanel` mic button ·
> `KeyRow` `remember{}` phantom-save · `Watchdog` loopback WS.
>
> **LIVE STACK:** Parakeet (NPU) + VoxSherpa (system TTS) for voice;
> OmniNeural-4B (NPU) + Gemma-4-E4B-IT (GPU) for text/vision. Cloud lives
> in a separate app behind a capability adapter.

---

## Where we are

Greenfield rebuild in flight. Old app code (sherpa-onnx voice stack, cloud
provider clients, EdgeModelFactory type-label soup, mic button) is being
scrapped in favor of a 9-boundary stack with the Truman Show principle:
models perform, they don't know about routing.

Live skeleton committed to `horizons/src/main/java/com/horizons/core/`:
opaque `NexaEngine` + `NexaModelLoader` (no type labels) and `AppStateStore`
(single StateFlow source of truth, kills the phantom-save credential bug).

Old packages still in the tree so HEAD keeps building. Clean deletion happens
after the new core compiles end-to-end and the design agent's UI lands.

## What changed this session

- **G1 — core scaffold** (DONE): `GREENFIELD_PLAN.md`, `core/nexa/`,
  `core/state/AppStateStore`.
- **G2 — salvage port** (DONE, parallel agent `intelligent-dijkstra`):
  5 files into `core/` + typealias shims at old paths so callers keep
  resolving. Also closed Q4 (per-tile terminal = TaskerBridge RUN_COMMAND
  v1) and Q5 (cloud-frontend = sibling module `:cloudfront/`, same repo).
- `horizons/libs/` (sherpa AAR) gitignored — sherpa is on the scrap list.
- Docs reorg: SOTU/prefix/board as the three pickup files; `wiki/` +
  `rules/` index folders; project-memory skill stood up.
- Added `DECISIONS.md`, `OPEN_QUESTIONS.md`, `GLOSSARY.md` as cheap-to-load
  secondary references.
- **Doc hardening pass:** SCRAPPED banners on every `.archive/*` file;
  DEAD STACK block on `SOTU.md` + `PROMPT_PREFIX.md` so sub-agents
  cannot wander back to Kokoro / Moonshine / sherpa / Orchestrator.
- **Vision + action pipeline** canonicalized into `CLAUDE_AT_HORIZONS.md`
  (dual-input MediaProjection + Accessibility tree, in-process Ktor
  OpenAI-compatible HTTP server at `127.0.0.1:1234`, Accessibility
  Worker Relay for action output). Cloud-frontend speaks the same wire.
- **`rules/AAR_DECOMPILE.md`:** decompile the Nexa AAR before writing SDK
  code — bytecode is ground truth, scraped docs lie.
- **G3 — Nexa SDK wire-up** (DONE): decompiled the AAR, confirmed real
  package is `com.nexa.sdk`. Wrote `LiveNexaVlmEngine` (VlmWrapper —
  covers OmniNeural NPU + Gemma GPU) and `LiveNexaAsrEngine` (AsrWrapper
  — Parakeet NPU). `NexaModelLoader.load()` is real, not stub.
  `compileDebugKotlin` clean. Closed Q2.
- **PR #36 merged to main.** All greenfield docs + G1/G2/G3 in tree.

## Build strategy decision (locked here)

Greenfield engine work can ship **under the existing installed APK's UI**.
Operator keeps the current interface they have on the phone; new at-bats
swap the engine layer underneath via update APK. Design swap (G7) happens
only when the operator has a rough draft ready. Until then, all G2-G6
work uses the existing UI as the host shell.

## What's next (one at-bat each, in order — see EXECUTION_BOARD.md)

| # | Name | What it does |
|---|---|---|
| ~~G2~~ | Salvage port | **DONE** by `intelligent-dijkstra`. |
| ~~G3~~ | Nexa SDK wire-up | **DONE.** `LiveNexaVlmEngine` + `LiveNexaAsrEngine` real and compiling. |
| **G4** | AppStateStore adoption | Migrate UI off the phantom-save `remember{}` pattern onto `AppStateStore.snapshot` StateFlow. Kills the keys-reset bug. |
| **G5** | Per-tile terminal | Embedded shell per tile via `core/shell/TaskerBridge` (RUN_COMMAND v1; Q4 closed). |
| **G6** | Cloud-frontend adapter | `:cloudfront/` sibling module (Q5 closed). Capability adapter returns `NexaEngine`-shaped handles. Hot-swap from Router tile. |
| G7 | UI scaffold | **Gated on operator dropping a rough design draft.** Until then, keep existing UI. |
| G8 | Legacy deletion | Once G3 lands, delete sherpa, Orchestrator, providers, EdgeModelFactory, mic button. |

G2 through G6 can ship under the current installed UI. The operator
installs the next update APK and uses the new engine layer through the
old screens. G7 is the only at-bat that touches visuals.

## Stuck / waiting on

- **Q1:** concurrent NPU + GPU residency — code can issue both loads now;
  needs live device test (operator runs an at-bat or installs the APK).
- **Q3:** design artifact format + delivery — blocks G7 only. G4-G6
  ship under existing UI.
- **Q6:** GCP auth from Termux — deferred to G6.
- ~~Q2~~: closed by G3 (AsrWrapper confirmed in `com.nexa.sdk`).

---

**Maintenance:** end-of-session, agent drafts new SOTU as part of close-out.
Operator reviews + commits. Skip a session → next agent reads it as stale
and asks before relying on it.
