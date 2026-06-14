# State of the Union — Horizons

> **Pickup file.** Read this first every session. One page. Updated at the end
> of every session (operator pushes; agent drafts). If a session ends without
> a SOTU bump, the next agent flags it.
>
> Format is fixed — do not add sections. Bump dated line + replace the four
> blocks. Keep total ≤ 1 screen.

**Snapshot:** 2026-06-14 12:00 UTC · branch `claude/jolly-lamport-5cJJ4`

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

## What's next (one at-bat each)

1. Port salvage files (SystemTtsClient, ScreenshotCapture, CrashRecorder,
   InteractionLogger, TaskerBridge) into `core/voice|screen|log|shell`.
2. Wire `NexaModelLoader.load()` to the real Nexa Android SDK (VlmWrapper
   internal; surface stays type-label-free).
3. Per-tile terminal capability (every tile can run a shell to update its
   own code in-app — see CLAUDE_AT_HORIZONS.md §Tiles).
4. UI scaffold once design agent drops the artifact.

## Stuck / waiting on

- **Concurrent NPU + GPU residency** — undocumented in Nexa Android. Can
  OmniNeural+Parakeet (NPU) and Gemma-4-E4B-IT (GPU) live simultaneously?
- **Design artifact** — operator preparing rough version; agents hold off
  on UI scaffolding until it lands.
- **Parakeet Android API** — confirm wrapper class name + load pattern.

---

**Maintenance:** end-of-session, agent drafts new SOTU as part of close-out.
Operator reviews + commits. Skip a session → next agent reads it as stale
and asks before relying on it.
