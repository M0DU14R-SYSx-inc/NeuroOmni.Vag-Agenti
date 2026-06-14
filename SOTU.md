# State of the Union — Horizons

> **Pickup file.** Read this first every session. One page. Updated at the end
> of every session (operator pushes; agent drafts). If a session ends without
> a SOTU bump, the next agent flags it.
>
> Format is fixed — do not add sections. Bump dated line + replace the four
> blocks. Keep total ≤ 1 screen.

**Snapshot:** 2026-06-14 16:37 UTC · branch `claude/intelligent-dijkstra-1zva9t`

---

## Where we are

Greenfield rebuild in flight. 9-boundary Truman Show stack. G1 (scaffold)
and G2 (salvage port) are done. **G3 (Nexa SDK wire-up) just landed** —
`NexaModelLoader.load()` is real: VlmWrapper for OmniNeural-4B (NPU) +
Gemma-4-E4B-IT (GPU), AsrWrapper for Parakeet TDT (NPU). Public API stays
type-label-free; callers receive a `NexaEngine` regardless of modality.

Old packages still in the tree so HEAD keeps building. Q2 (Parakeet API)
answered via bytecode decompile of `ai.nexa:core:0.0.24`. Q5 (cloud-frontend
repo layout) answered: sibling module `:cloudfront/`. Q1 (NPU + GPU
co-residency) still open — test on device with G3 live.

## What changed this session

- G3 complete: `NexaModelLoader`, `VlmEngineImpl`, `AsrEngineImpl` wired to
  real SDK. `NexaModelSpec` gained `Modality.LLM / Modality.ASR` enum.
- Q2 closed: `AsrWrapper.builder().asrCreateInput(AsrCreateInput(...)).build()`
  confirmed from AAR decompile + 4 production codebases.
- Q5 closed (prior session, board updated): `:cloudfront/` sibling module.
- EXECUTION_BOARD: G3 → DONE.

## Build strategy decision (locked here)

Greenfield engine work ships **under the existing installed APK's UI**.
Operator keeps the current interface on the phone; new at-bats swap the engine
layer via update APK. Design swap (G7) happens only when the operator has a
rough draft ready. Until then, G4-G6 use the existing UI as the host shell.

## What's next (one at-bat each — see EXECUTION_BOARD.md)

| # | Name | What it does |
|---|---|---|
| G4 | AppStateStore adoption | Migrate UI off phantom-save `remember{}` → single `StateFlow`. Kills keys-reset bug. |
| G5 | Per-tile terminal | Every tile gets an embedded shell (TaskerBridge → RUN_COMMAND v1). |
| G6 | Cloud-frontend adapter | `:cloudfront/` module that returns `NexaEngine`-shaped handles via cloud. |
| G7 | UI scaffold | **Gated on operator rough design draft.** |
| G8 | Legacy deletion | After G2+G3 proven: scrape sherpa, Orchestrator, EdgeModelFactory. |

## Stuck / waiting on

- **Q1 (OPEN_QUESTIONS):** concurrent NPU + GPU residency — undocumented.
  Smoke-test on device now that G3 is real.
- **Q3:** design artifact format + delivery — blocks G7 only.

---

**Maintenance:** end-of-session, agent drafts new SOTU as part of close-out.
Operator reviews + commits. Skip a session → next agent reads it as stale
and asks before relying on it.
