# Failure log

Append-only ledger of recurring blockers. Format per entry:

```
## <short title>

- **Symptom:** what the operator sees.
- **Root cause:** known / suspected / unknown.
- **Attempted fixes:**
  - <date> — <approach> — <outcome>
- **Current status:** open / resolved / forked (link FORK_DECISIONS).
```

Rule: every at-bat that hits a known failure **appends its attempt
+ outcome** rather than starting a new file. New blocker → new entry.

---

## Moonshine STT — int8 ConvInteger not implemented

- **Symptom:** ORT-Android 1.22.0 crashes loading Moonshine int8 encoder.
- **Root cause:** ORT 1.22 lacks `ConvInteger` op (added 1.24.3).
- **Attempted fixes:**
  - 2026-06-05 — switched encoder to FP32 — load OK, inference burns CPU.
  - 2026-06-08 — bundled sherpa-onnx AAR (ships ORT 1.24.3) — worked but
    introduced libonnxruntime.so collision with Nexa's 1.22.
  - 2026-06-09 — `extractSherpaOrt` Gradle task pickFirsts sherpa's .so —
    builds clean.
- **Current status:** forked. Moonshine + sherpa-onnx scrapped in
  greenfield; Parakeet on NPU via Nexa SDK replaces it.

## Kokoro TTS — GPU routing blocked

- **Symptom:** Kokoro on Adreno via `addNnapi()` either falls back to CPU
  or contends with Nexa NPU init.
- **Root cause:** ORT-Android's NNAPI delegate doesn't reliably route to
  QNN GPU on Snapdragon 8 Elite. `addQnn()` doesn't exist in the Maven
  artifact.
- **Attempted fixes:**
  - 2026-06-07 — `addNnapi(executionMode = PREFER_SUSTAINED_SPEED)` —
    inconsistent device selection.
  - 2026-06-10 — VoxSherpa as system TTS engine — works cleanly via
    `android.speech.tts.TextToSpeech`.
- **Current status:** forked. Kokoro scrapped; VoxSherpa is the CPU
  node per boundary 9.

## Concurrent NPU + GPU model residency — undocumented

- **Symptom:** Unknown whether OmniNeural-4B + Parakeet (NPU) can co-exist
  with Gemma-4-E4B-IT (GPU) simultaneously.
- **Root cause:** Nexa Android SDK docs don't address cross-device residency.
- **Attempted fixes:** none yet.
- **Current status:** OPEN. Blocking G6 acceptance. Plan: test on device
  with `core/nexa/NexaModelLoader` once G3 lands.

## Phantom-save credential bug

- **Symptom:** Keys "save" in Router but reset on next app launch.
- **Root cause:** `KeyRow` held local `remember { mutableStateOf(stored) }`
  that diverged from `CredentialStore` on the next read.
- **Attempted fixes:**
  - 2026-06-11 — read directly from store on each composition — flicker.
- **Current status:** designed-around. `AppStateStore` (greenfield) uses
  a single `StateFlow<Map>` so composables collect, never cache.

## Screenshot crashes NPU on full-res

- **Symptom:** Sending a full-res Razr screenshot (1080x2640) to OmniNeural
  instantly OOMs the NPU prefill.
- **Root cause:** ~14k vision tokens at full res.
- **Attempted fixes:**
  - 2026-06-12 — downscale to max-edge 1024px, JPEG q=85 — works.
- **Current status:** resolved. Salvaged into `core/screen/ScreenshotCapture`.

## GCP / Hermes auth — Termux

- **Symptom:** iOS-restricted API key blocks Android Termux; OAuth
  device-code returns "malformed code"; service-account JSON path
  untested.
- **Root cause:** mixed credential scope; Termux is neither iOS nor a
  registered Android app.
- **Attempted fixes:** none beyond reproduction.
- **Current status:** OPEN. Deferred — cloud frontends will be a separate
  app with their own auth surface; main Horizons doesn't touch cloud.
