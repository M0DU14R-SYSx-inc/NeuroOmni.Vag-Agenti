# Greenfield Rebuild — Horizons v2

**Branch:** `claude/jolly-lamport-5cJJ4`
**Created:** 2026-06-14
**Status:** scaffold landed; cleanup + UI pending design-agent output

## Why greenfield

Old app accumulated layered failures (sherpa-onnx ORT collisions, Orchestrator
cloud failover leaking through model code, EdgeModelFactory type labels gating
behavior, mic button crashes, phantom-save credential bug). Operator decided to
scrap app code and rebuild around a fixed 9-layer stack. **Framework docs
survive** — see "Preserved" below.

## The 9 hard boundaries (in order, no omissions, no workarounds)

1. **Nexa Studio** — model source
2. **Nexa SDK / CLI** — runtime entry (Kotlin in-process on Android)
3. **Nexa Server** — on-device serving surface (in-process here, not a daemon)
4. **Nexa ML** — runtime layer
5. **Qualcomm QNN SDK** — NPU/GPU compute (reached via Nexa plugin layer)
6. **Android Accessibility Service API** — system surface / overlay / injection
7. **Gemma-4-E4B-IT** — GPU (Adreno, via Nexa `cpu_gpu` GGUF plugin)
8. **OmniNeural-4B + Parakeet TDT/ASR** — NPU, together (`plugin_id="npu"`)
9. **VoxSherpa** — already installed; accessed as a node, CPU
   (`com.CodeBySonu.VoxSherpa` via `android.speech.tts.TextToSpeech`)

Backend/cloud routing is a **separate dedicated app**. Models inside Horizons
never know it exists — Truman Show principle.

## Truman Show principle (load-bearing)

Models perform. They never see:
- a backend router
- other models on other devices
- cloud failover paths
- type labels (VLM/MLLM/STT/ASR/TTS) gating their behavior

Engine code surfaces one capability: `loadModel(name, plugin, device) -> Engine`.
The Engine exposes `infer(input) -> output`. No "this is a VLM" branching.

## Preserved (do not touch)

Framework docs are the gold:

- `CLAUDE_AT_HORIZONS.md`, `PROMPT_PREFIX.md`, `EXECUTION_BOARD.md`
- `AGENT_GO.md`, `HANDOFF.md`, `UNIVERSAL_PREFIX.md`,
  `MANAGED_AGENT_KICKOFF.md`, `SETUP_PROMPT.md`
- `wiki/`, `rules/`, `agents/`, `skills/`, `docs/`
- `sub-agent.agent.yaml`

## Salvage (5 files, port as-is into new package)

These solved real problems and don't carry the bad patterns:

| Source | Destination | Why kept |
|---|---|---|
| `audio/SystemTtsClient.kt` | `core/voice/SystemTtsClient.kt` | VoxSherpa bridge — boundary 9 |
| `screen/ScreenshotCapture.kt` | `core/screen/ScreenshotCapture.kt` | MediaProjection + 1024px downscale (NPU OOM fix) |
| `logging/CrashRecorder.kt` | `core/log/CrashRecorder.kt` | Native-crash file marker |
| `logging/InteractionLogger.kt` | `core/log/InteractionLogger.kt` | JSONL audit trail |
| `tasker/TaskerBridge.kt` | `core/shell/TaskerBridge.kt` | Termux/Tasker shell route |
| `accessibility/HorizonsAccessibilityService.kt` (skeleton) | `core/a11y/` | Boundary 6 surface |

## Scrap (do not port)

- All sherpa-onnx: `MoonshineSttEngine`, `KokoroTtsEngine`, downloaders,
  phonemizer, `extractSherpaOrt` Gradle task. Parakeet on NPU replaces STT;
  VoxSherpa replaces TTS.
- `orchestrator/Orchestrator.kt` — cloud failover routing leaks backends.
- `provider/` — OpenRouter, Anthropic, Vertex clients. Cloud lives in the
  separate router app.
- `model/EdgeModelFactory.kt`, `EdgeModelDownloader.kt`, `EdgeModelImporter.kt`,
  `NexaVlmEngine.kt` — type-label soup.
- `ChatPanel` mic button — keyboard mic handles STT.
- `KeyRow` phantom-save pattern in `RouterPanel.kt` — replaced by AppStateStore.

## New core skeleton (this commit)

```
horizons/src/main/java/com/horizons/core/
├── nexa/
│   ├── NexaModelLoader.kt    # one entry point, no type labels
│   ├── NexaModelSpec.kt      # name + plugin_id + device_id + config
│   └── NexaEngine.kt         # opaque handle, infer(input)->output
├── state/
│   └── AppStateStore.kt      # single StateFlow<Map> source of truth
└── README.md                 # how to extend
```

The salvaged files port into adjacent packages (`core/voice`, `core/screen`,
`core/log`, `core/shell`, `core/a11y`) in a follow-up commit so the cut is
reviewable.

## Open questions (resolve before final cut)

1. **Concurrent NPU + GPU residency** — can `OmniNeural+Parakeet` live on the
   Hexagon NPU while `Gemma-4-E4B-IT` lives on Adreno simultaneously? Nexa
   Android docs don't say. Determines whether we evict-cycle or co-resident.
2. **Parakeet Android API surface** — confirm `AsrWrapper` (or whatever Nexa
   names it) load pattern. The VLM/`VlmWrapper.builder()` pattern is known;
   ASR's may differ.
3. **Concurrent consent persistence** — MediaProjection consent should survive
   for a whole session, not prompt per screenshot. Carry the `Intent` data via
   a foreground service handle.

## Out of scope this commit

- Deleting old code — done in a follow-up after the new core compiles and the
  design agent's UI lands.
- Wiring `MainActivity` / `HorizonsApplication` to the new core — pending UI.
- Gradle dependency pruning (sherpa-onnx removal) — same reason.
