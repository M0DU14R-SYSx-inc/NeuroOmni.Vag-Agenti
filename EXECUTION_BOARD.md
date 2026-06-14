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
| G2 — Salvage port | AVAILABLE | — | — |
| G3 — Nexa SDK wire-up | AVAILABLE | — | — |
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
status: AVAILABLE
difficulty: 2/5
depends_on: [G1]
scope: |
  Port 5 files into core/, preserving package public-API where it crosses
  module boundaries. Add 1-line redirect shims in old paths so callers
  keep compiling.
files:
  - audio/SystemTtsClient.kt     → core/voice/SystemTtsClient.kt
  - screen/ScreenshotCapture.kt  → core/screen/ScreenshotCapture.kt
  - logging/CrashRecorder.kt     → core/log/CrashRecorder.kt
  - logging/InteractionLogger.kt → core/log/InteractionLogger.kt
  - tasker/TaskerBridge.kt       → core/shell/TaskerBridge.kt
acceptance: gradle build clean; old call sites still resolve.
```

### G3 — Nexa SDK wire-up

```yaml
status: AVAILABLE
difficulty: 4/5
depends_on: [G1]
scope: |
  Wire NexaModelLoader.load() to the real Nexa Android SDK. Branch on
  spec.pluginId internally (VlmWrapper for "npu" / "cpu_gpu"; AsrWrapper
  or equivalent for ASR — confirm class name from SDK jar). Public API
  must stay type-label-free.
open_questions:
  - Parakeet wrapper class name + load pattern (AsrWrapper?).
  - Concurrent NPU + GPU residency (boundary 7 + 8 simultaneously?).
acceptance: |
  Smoke test: load OmniNeural-4B on NPU → infer "hello" → returns text.
  Same loader loads Gemma-4-E4B-IT on GPU. No type-branching in caller.
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
  Every UI tile gets an embedded shell pane. Scoped to the tile's package
  via TaskerBridge / Termux. Edits land in the tree, NOT auto-pushed.
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
  Capability adapter that returns NexaEngine-shaped handles backed by a
  separate cloud-frontend app/process. Model code can't tell local from
  remote. Hot-swap toggle in Router tile.
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
