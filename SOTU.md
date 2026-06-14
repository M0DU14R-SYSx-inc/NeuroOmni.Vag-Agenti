# State of the Union — Horizons

> **Pickup file.** Read this first every session. One page. Updated at the end
> of every session (operator pushes; agent drafts). If a session ends without
> a SOTU bump, the next agent flags it.
>
> Format is fixed — do not add sections. Bump dated line + replace the four
> blocks. Keep total ≤ 1 screen.

**Snapshot:** 2026-06-14 12:32 UTC · branch `claude/jolly-lamport-5cJJ4`

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

- Greenfield branch scaffolded (`GREENFIELD_PLAN.md`, `core/nexa/`, `core/state/`).
- `horizons/libs/` (sherpa AAR) gitignored — sherpa is on the scrap list.
- Docs reorg: SOTU/prefix/board as the three pickup files; `wiki/` + `rules/`
  index folders; project-memory skill stood up.
- Added `DECISIONS.md`, `OPEN_QUESTIONS.md`, `GLOSSARY.md` as cheap-to-load
  secondary references.

**Session halted by operator at this commit — usage credits.** All work
landed clean: branch pushed, working tree clean, board reflects state.

## Build strategy decision (locked here)

Greenfield engine work can ship **under the existing installed APK's UI**.
Operator keeps the current interface they have on the phone; new at-bats
swap the engine layer underneath via update APK. Design swap (G7) happens
only when the operator has a rough draft ready. Until then, all G2-G6
work uses the existing UI as the host shell.

## What's next (one at-bat each, in order — see EXECUTION_BOARD.md)

| # | Name | What it does |
|---|---|---|
| G2 | Salvage port | Move 5 keeper files (SystemTtsClient, ScreenshotCapture, CrashRecorder, InteractionLogger, TaskerBridge) into `core/`. |
| G3 | Nexa SDK wire-up | Make `NexaModelLoader.load()` real. OmniNeural+Parakeet on NPU, Gemma-4-E4B-IT on GPU. No type labels in public API. |
| G4 | AppStateStore adoption | Migrate UI off the phantom-save `remember{}` pattern onto the single `StateFlow` source of truth. Kills the keys-reset bug. |
| G5 | Per-tile terminal | Every tile gets an embedded shell (TaskerBridge → RUN_COMMAND v1). |
| G6 | Cloud-frontend adapter | Capability adapter that returns `NexaEngine`-shaped handles backed by a separate cloud app. Hot-swap models from Router tile. |
| G7 | UI scaffold | **Gated on operator dropping a rough design draft.** Until then, keep existing UI. |
| G8 | Legacy deletion | Once G2 + G3 land, delete sherpa, Orchestrator, providers, EdgeModelFactory, mic button. |

G2 through G6 can ship under the current installed UI. The operator
installs the next update APK and uses the new engine layer through the
old screens. G7 is the only at-bat that touches visuals.

## Stuck / waiting on

- **Q1 (OPEN_QUESTIONS):** concurrent NPU + GPU residency — undocumented.
  Blocks G3 acceptance.
- **Q2:** Parakeet Android wrapper API — blocks G3 ASR path.
- **Q3:** design artifact format + delivery — blocks G7 only.
- **Q5:** cloud-frontend repo layout (same repo vs separate) — blocks G6.

---

**Maintenance:** end-of-session, agent drafts new SOTU as part of close-out.
Operator reviews + commits. Skip a session → next agent reads it as stale
and asks before relying on it.
