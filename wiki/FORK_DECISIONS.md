# Fork decisions

When a path is abandoned, log it here. Format per entry:

```
## <abandoned thing> → <replacement>

- **Trigger:** what made us fork.
- **Replacement choice:** what we picked + why.
- **Archived branch:** `archive/<name>` if branch is preserved.
- **Date:** YYYY-MM-DD.
```

Rule: forks are decisions, not failures. The failure log captures
attempts; this file captures the cut.

---

## sherpa-onnx voice stack (Moonshine + Kokoro) → Parakeet (NPU) + VoxSherpa (CPU)

- **Trigger:** Two compounding failures — Moonshine int8 ORT op gap and
  Kokoro Adreno routing — required a 14 MB AAR shim with libonnxruntime.so
  collisions. Both engines were stub-quality on-device.
- **Replacement choice:** Parakeet TDT/ASR on Hexagon NPU via Nexa SDK
  (boundary 8, co-resident with OmniNeural). VoxSherpa as system TTS
  via `android.speech.tts.TextToSpeech` (boundary 9, already installed,
  higher voice quality).
- **Archived branch:** `archive/sherpa-voice-stack` (to be created at G8).
- **Date:** 2026-06-13.

## In-app Orchestrator with cloud failover → Separate cloud-frontend app

- **Trigger:** Truman Show principle — model code branched on
  `is StubEdgeModel` to fall back to OpenRouter, leaking backend
  awareness into the engine layer.
- **Replacement choice:** Cloud frontends, hot-swap, CLI access, and API
  access move to a separate process / app. Main Horizons reaches them
  through a capability adapter that returns `NexaEngine`-shaped handles.
- **Archived branch:** `archive/orchestrator-cloud-failover` (to be
  created at G8).
- **Date:** 2026-06-14.

## EdgeModelFactory type labels → opaque NexaEngine

- **Trigger:** VLM/MLLM/STT/TTS labels in the loader gated behavior —
  every new model required a new type. Phantom complexity.
- **Replacement choice:** `NexaModelSpec(name, pluginId, deviceId, …)`.
  Loader returns `NexaEngine`. No labels in public API.
- **Archived branch:** legacy code still in tree; deletion at G8.
- **Date:** 2026-06-14.
