# Open questions

Single inbox for things blocked on an operator decision or unknown
technical answer. If a question lands here, no agent should burn an
at-bat guessing — flag it, move to another milestone.

Format per entry:

```
## Q<N> — <short title>

- **Asked:** YYYY-MM-DD
- **Blocking:** which milestone(s)
- **Question:** the actual ask, in one sentence.
- **Context:** ≤5 lines.
- **Answer:** (filled when resolved; date + 1-line answer)
- **Status:** open / answered / superseded
```

Rule: closed questions stay (audit trail). Don't delete; move to `## Answered` at bottom.

---

## Q1 — Concurrent NPU + GPU model residency

- **Asked:** 2026-06-13
- **Blocking:** G3, G6, all model-loading work for boundaries 7 + 8.
- **Question:** Can OmniNeural-4B + Parakeet (NPU) live simultaneously
  with Gemma-4-E4B-IT (GPU) under the Nexa Android SDK, or do we need
  evict-cycling?
- **Context:** Nexa Android docs don't address cross-device residency.
  The 9-boundary architecture assumes co-residency. If it doesn't hold,
  boundary 7 becomes a swap target rather than a permanent resident.
- **Answer:** —
- **Status:** open. Plan: test on device once G3 lands and
  `NexaModelLoader.load()` is real, not stubbed.

## Q2 — Parakeet Android wrapper API

- **Asked:** 2026-06-13
- **Blocking:** G3 (ASR path).
- **Question:** What is the Nexa Android wrapper class for Parakeet
  TDT/ASR? Is it `AsrWrapper`, `SpeechWrapper`, or does it ride
  `VlmWrapper` with audio attachments?
- **Context:** OmniNeural-4B uses `VlmWrapper.builder()`. The Parakeet
  pattern is unconfirmed. Without the class name + builder shape, G3
  ASR can't compile.
- **Answer:** 2026-06-14. Confirmed from `ai.nexa:core:0.0.24` AAR bytecode +
  multiple production repos. Class: `com.nexa.sdk.AsrWrapper`. Builder chain:
  `AsrWrapper.builder().asrCreateInput(AsrCreateInput(model_name, model_path,
  config=ModelConfig(max_tokens, npu_lib_folder_path, npu_model_folder_path),
  plugin_id="npu")).dispatcher(Dispatchers.IO).build()` → `Result<AsrWrapper>`.
  Transcribe: `wrapper.transcribe(AsrTranscribeInput(audioPath, "en"))` →
  `Result<AsrTranscribeOutput>`; text at `out.result.transcript`. No separate
  `AsrConfig` needed for create — uses shared `ModelConfig`. G3 wired.
- **Status:** answered.

## Q3 — Design artifact format + delivery

- **Asked:** 2026-06-14
- **Blocking:** G7 (UI scaffold).
- **Question:** Does the operator deliver a coded reference (Compose
  snippets / Figma export / screenshots), or a rough hand-drawn flow?
  G7 acceptance depends on what "matches the design" means.
- **Context:** Designer agent has failed 5 spin-ups. Operator considering
  rough-draft handoff. Greenfield can proceed without final design —
  see `DECISIONS.md` for the rough-then-restyle path.
- **Answer:** —
- **Status:** open. Operator decision.

## Q6 — GCP auth for Termux-on-device

- **Asked:** 2026-06-10
- **Blocking:** any cloud frontend that hits Google APIs.
- **Question:** What credential type works for Google APIs called from
  Termux on the device? iOS-restricted API key blocked; OAuth device-code
  returned malformed; service-account JSON untested.
- **Context:** Termux is neither iOS nor a registered Android app. The
  separate cloud-frontend module could carry the credentials if main
  Horizons never touches cloud.
- **Answer:** —
- **Status:** open. Deferred to cloud-frontend at-bat (G6).

---

## Answered

## Q4 — Per-tile terminal: which Termux entry point?

- **Asked:** 2026-06-14
- **Blocking:** G5.
- **Question:** Does per-tile terminal use `com.termux.RUN_COMMAND`
  (existing `TaskerBridge`) or a foreground PTY via
  `ProcessBuilder`?
- **Context:** RUN_COMMAND is fire-and-forget — easy to wire, no live
  prompt. PTY supports interactive use but needs an embedded terminal
  emulator.
- **Answer:** 2026-06-14. RUN_COMMAND v1 (fire-and-forget via TaskerBridge).
  Agent drives input composition; interactive PTY deferred to v2.
- **Status:** answered.

## Q5 — Cloud-frontend app: same repo or separate?

- **Asked:** 2026-06-14
- **Blocking:** G6.
- **Question:** Does the cloud-frontend live as a sibling module
  (`:cloudfront/`) in this repo, or a separate repo entirely?
- **Context:** Truman Show says it's "separate process / separate app."
  Doesn't say separate repo. Same-repo is faster to iterate; separate
  is cleaner boundary enforcement.
- **Answer:** 2026-06-14. Same repo, sibling module `:cloudfront/`.
  Cloud-frontend is a capability surface the agent uses as a tool,
  not a hard Truman Show boundary. Physical separation not required.
- **Status:** answered.
