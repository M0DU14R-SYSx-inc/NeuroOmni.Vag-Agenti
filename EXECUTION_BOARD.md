# Horizons Execution Board

> **Pickup file #3.** Living task list + claims dashboard. Edit when you
> claim or advance a milestone; commit the 1-line diff (`chore(board): G2
> claimed by main`). Multiple agents can work in parallel on milestones
> with disjoint deps.
>
> Previous (pre-greenfield) board archived at `.archive/EXECUTION_BOARD.2026-06-14.md`.

---

## Active claims dashboard

| Milestone | Status | Claimed by | Started |
|---|---|---|---|
| G1 — Core scaffold | DONE | main | 2026-06-14 |
| G2 — Salvage port | DONE | intelligent-dijkstra | 2026-06-14 |
| G3 — Nexa SDK wire-up | DONE | intelligent-dijkstra | 2026-06-14 |
| G4 — AppStateStore adoption | AVAILABLE | — | — |
| G5 — Per-tile terminal | AVAILABLE | — | — |
| G6 — Cloud-frontend adapter | AVAILABLE | — | — |
| G7 — UI scaffold (gated on design) | BLOCKED | — | — |
| G8 — Legacy deletion | BLOCKED | — | — |

## Status flow

```
AVAILABLE → CLAIMED → IN_PROGRESS → DONE
                            ↓
                       BLOCKED / FAILED → AVAILABLE
```

## Difficulty + recommended model

| Rating | Scope | Model |
|---|---|---|
| 1/5 | ≤50 LOC, isolated | Haiku 4.5 / Sonnet-fast |
| 2/5 | ~100 LOC, 1-2 files | Sonnet 4.6 |
| 3/5 | ~300 LOC, careful integration | Sonnet 4.6 + adversarial review |
| 4/5 | Architectural, multi-file | Opus 4.7+ |
| 5/5 | Research-grade | Opus 4.8 / human loop |

---

## Greenfield milestones

### G1 — Core scaffold

```yaml
status: DONE
difficulty: 1/5
artifacts:
  - GREENFIELD_PLAN.md
  - horizons/src/main/java/com/horizons/core/nexa/{NexaModelSpec,NexaEngine,NexaModelLoader}.kt
  - horizons/src/main/java/com/horizons/core/state/AppStateStore.kt
acceptance: opaque engine interface compiles, AppStateStore unit-tested.
```

### G2 — Salvage port

```yaml
status: DONE
difficulty: 2/5
depends_on: [G1]
artifacts:
  - horizons/src/main/java/com/horizons/core/screen/ScreenshotCapture.kt  (ported + 1024px JPEG downscale)
  - horizons/src/main/java/com/horizons/core/log/InteractionLogger.kt      (ported)
  - horizons/src/main/java/com/horizons/core/shell/TaskerBridge.kt         (ported)
  - horizons/src/main/java/com/horizons/core/voice/SystemTtsClient.kt      (new — VoxSherpa boundary-9 bridge)
  - horizons/src/main/java/com/horizons/core/log/CrashRecorder.kt          (new — uncaught exception file marker)
  - typealias shims: screen/ScreenshotCapture, logging/InteractionLogger, tasker/TaskerBridge
notes: |
  SystemTtsClient and CrashRecorder did not exist in legacy tree; written from spec.
  TermuxTtsClient (audio/) left in place — it shells to Termux, not VoxSherpa.
acceptance: gradle build clean; old call sites still resolve via typealias shims.
```

### G3 — Nexa SDK wire-up

```yaml
status: AVAILABLE
difficulty: 4/5
depends_on: [G1]
artifacts:
  - horizons/src/main/java/com/horizons/core/nexa/NexaModelSpec.kt     (added Modality enum)
  - horizons/src/main/java/com/horizons/core/nexa/NexaModelLoader.kt   (real SDK init + load)
  - horizons/src/main/java/com/horizons/core/nexa/VlmEngineImpl.kt     (new — VlmWrapper impl)
  - horizons/src/main/java/com/horizons/core/nexa/AsrEngineImpl.kt     (new — AsrWrapper impl)
scope: |
  Wire NexaModelLoader.load() to the real Nexa Android SDK. Branch on
  spec.modality internally (AsrEngineImpl for ASR; VlmEngineImpl for LLM).
  VlmWrapper handles both NPU (OmniNeural) and CPU_GPU (Gemma). Public API
  stays type-label-free — callers receive NexaEngine regardless of modality.
notes: |
  Q2 answered via bytecode decompilation of ai.nexa:core:0.0.24 AAR.
  AsrCreateInput: model_name, model_path, config (ModelConfig with npu_lib_
  folder_path + npu_model_folder_path), plugin_id. Transcribe via
  AsrWrapper.transcribe(AsrTranscribeInput(audioPath, language)) → Result.
  NexaModelSpec gained Modality.LLM / Modality.ASR (default=LLM, no BC break).
  Q1 (NPU+GPU co-residency) still open — test on device.
acceptance: |
  Smoke test: load OmniNeural-4B on NPU → infer with image → returns text.
  Same loader loads Parakeet on NPU → infer with audio → returns transcript.
  Same loader loads Gemma-4-E4B-IT on GPU → infer text → returns text.
  No type-branching visible in callers.
```

### G4 — AppStateStore adoption

```yaml
status: AVAILABLE
difficulty: 2/5
depends_on: [G1]
scope: |
  Migrate all credential / toggle reads in UI panels from
  CredentialStore + KeyRow remember{} pattern → AppStateStore.snapshot
  StateFlow. Kills the phantom-save bug.
acceptance: |
  Restart app → all saved keys + toggles persist verbatim.
  No composable holds a remember{} mutableStateOf for a persisted value.
```

### G5 — Per-tile terminal

```yaml
status: AVAILABLE
difficulty: 3/5
depends_on: [G2]
scope: |
  Every UI tile gets an embedded shell pane using core/shell/TaskerBridge
  via Tasker RUN_COMMAND v1 (fire-and-forget — PTY upgrade deferred to v2
  per operator decision). Agent drives input; interactive PTY not required.
  Edits land in the tree, NOT auto-pushed.
acceptance: |
  Open a tile → terminal tab → `ls horizons/src/main/java/com/horizons/`
  → output rendered inline. `git status` from terminal shows changes
  staged from terminal-edited files.
```

### G6 — Cloud-frontend adapter

```yaml
status: AVAILABLE
difficulty: 4/5
depends_on: [G3]
scope: |
  Capability adapter that returns NexaEngine-shaped handles backed by the
  cloud-frontend sibling module (:cloudfront/ in this repo — same-repo
  per operator decision). Model code can't tell local from remote.
  Hot-swap toggle in Router tile.
acceptance: |
  Toggle model in Router → adapter unloads + loads → callers keep their
  NexaEngine reference + see new behavior. No backend-awareness leaks
  into model code.
```

### G7 — UI scaffold (gated on design artifact)

```yaml
status: BLOCKED
blocker: waiting on operator to drop a rough design draft (designer agent
  failed 5 spin-ups; operator now hand-providing).
difficulty: 3/5
note: |
  G2-G6 ship under the existing installed UI. Operator updates the APK,
  uses new engine layer through the old screens. Don't gate G2-G6 on this.
```

### G8 — Legacy deletion

```yaml
status: BLOCKED
blocker: gated on G2 + G3 landing so the cut is reviewable.
difficulty: 2/5
scope: |
  Remove sherpa-onnx engine code (Moonshine, Kokoro, downloaders,
  phonemizer, extractSherpaOrt Gradle task), Orchestrator, provider/
  cloud clients, EdgeModelFactory + NexaVlmEngine + EdgeModelDownloader,
  ChatPanel mic button, KeyRow phantom-save remembers.
acceptance: gradle build clean; APK shrinks by sherpa AAR size.
```

---

## Hard rules for any agent at-bat

- Working branch: `claude/jolly-lamport-5cJJ4`. Never push `main` without
  explicit operator permission.
- Never `--no-verify`, `--no-gpg-sign`, force-push, or `reset --hard`
  without confirmation.
- Never commit credentials. `release/debug.keystore` is the exception.
- Don't delete feature branches after merge — archive (`archive/<name>`).
- Edits to wiki + prefix are batched between sessions.
- Model code is Truman Show — no type labels, no backend awareness.

Full rules: `rules/`. Wiki: `CLAUDE_AT_HORIZONS.md`. Pickup: `SOTU.md`.
