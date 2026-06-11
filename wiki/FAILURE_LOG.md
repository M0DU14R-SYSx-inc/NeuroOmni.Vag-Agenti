# Failure Log

Append-only ledger of repeated blockers + attempted fixes. Each new
at-bat that hits a known failure **appends its attempt + outcome**
rather than starting a new entry.

Mirror title + 1-line summary into a GitHub Issue (`label:
failure-log`) for cross-session memory the operator can triage on the
web.

## Format

```
### <Subsystem> — <Symptom>
- Opened: <date> by <agent-id>
- Root cause (if known):
- Attempts:
  - <date> <agent-id>: <fix tried> → <outcome>
- Status: open / fixed / forked → see FORK_DECISIONS.md#<anchor>
```

---

### Moonshine STT — int8 ConvInteger ORT_NOT_IMPLEMENTED
- Opened: 2026-06 (pre-jolly-lamport)
- Root cause: ONNX Runtime Android 1.22.0 does not implement
  ConvInteger; int8 Moonshine variants ship with it.
- Open question: Moonshine is positioned as **ASR** (sequence-to-
  sequence) rather than streaming STT — earlier framing as pure STT
  may be why the chain integration keeps misfiring.
- Attempts:
  - 2026-06: addCpu(true) overload missing in ORT 1.22.0 → removed.
  - 2026-06: switched int8 → FP32 variant → Kotlin compile errors
    (val/var, toLongArray on List<Int>) fixed in PR #25.
  - 2026-06-11 (side-panel agent): root-cause re-diagnosis — the dead
    end was the **hand-rolled ORT decode loop** (fake empty
    past_key_values of wrong rank, input-name guessing, no KV cache),
    not Moonshine itself. Rewrote MoonshineSttEngine on
    **sherpa-onnx OfflineRecognizer** (AAR v1.13.2): proper
    cached/uncached decode, and sherpa's bundled ORT 1.24.3 implements
    ConvInteger so the int8 model (~250 MB sherpa package) loads.
    Compile verified; device verify pending.
- Status: fixed-in-code (sherpa-onnx rewrite) — awaiting on-device
  verification. Parakeet fallback is now a config swap, not a code
  rewrite (sherpa OfflineModelConfig supports NeMo transducers).

### Kokoro TTS — phonemizer + GPU routing
- Opened: 2026-06
- Root cause: espeak-ng JNI vs Termux phonemizer path unresolved;
  GPU/NPU routing for inference not landed.
- Notes: operator has VoxSherpa's updated voice files staged.
- Attempts:
  - 2026-06: locked M1.2 spec to espeak-ng JNI (P0). Implementation
    pending.
  - 2026-06-11 (side-panel agent): espeak-ng JNI port made
    unnecessary — rewrote KokoroTtsEngine on **sherpa-onnx
    OfflineTts** (the engine behind VoxSherpa). Phonemization happens
    inside sherpa's native layer using the espeak-ng-data shipped in
    the kokoro-multi-lang-v1_0 archive (~349 MB, 53 voices in one
    voices.bin; voice = speaker id at generate() time, so switching
    is instant). Streaming playback via generateWithCallback →
    AudioTrack with barge-in. Compile verified; device verify pending.
- Status: fixed-in-code (sherpa-onnx rewrite) — awaiting on-device
  verification. GPU offload deferred: sherpa runs CPU (provider=cpu;
  FORCED EXCLUSION keeps the NPU exclusive to Nexa). A GPU path is a
  sherpa rebuild question — fork criterion stays in FORK_DECISIONS.

### Voice stack — three competing libonnxruntime.so copies
- Opened: 2026-06-11 by side-panel agent
- Root cause: Nexa AAR (ORT 1.22.0), sherpa-onnx AAR (ORT 1.24.3) and
  Maven onnxruntime-android each ship lib/arm64-v8a/libonnxruntime.so;
  pickFirst alone is non-deterministic across AARs. ORT's C API is
  backward compatible only (old client on new runtime OK, NOT the
  reverse) — so the single packaged copy MUST be the newest:
  sherpa's 1.24.3.
- Attempts:
  - 2026-06-11: dropped Maven onnxruntime-android (no Java ORT users
    left after the sherpa rewrite); gradle task `extractSherpaOrt`
    unpacks sherpa's libonnxruntime.so into the app jniLibs source
    set, which always wins the packaging merge. Nexa (1.22 client)
    runs on the 1.24.3 runtime.
- Status: fixed-in-code — device verify required (Nexa NPU init must
  still pass on ORT 1.24.3). If Nexa breaks, fallback is rebuilding
  sherpa against ORT 1.22.0 or isolating sherpa in a separate process.

### GCP / Hermes API auth
- Opened: 2026-06
- Root cause: API key Application restriction set to "iOS apps" (an
  allowlist, not a blocklist) blocked Android Termux. Device-code
  OAuth returned "malformed code".
- Attempts:
  - 2026-06: tried AI Studio key with claude-opus-4-6 — AI Studio
    does not serve Claude (misleading 400).
  - 2026-06: recommended service-account JSON as alternative to
    device-code flow.
- Status: open pending operator GCP console clean-up.

### App surface — phantom saves + 20% response rate (operator audit 2026-06-11)
- Opened: 2026-06-11 by operator (device testing)
- Symptoms (verbatim): saved settings revert on screen switch ("says
  it's loaded but it's not"); Nexa key must be re-entered repeatedly;
  model responds ~20% of the time; screenshot + screen share dead;
  settings placeholder; no terminal UI; Router buttons cryptic
  (HF/Files/Folder/VLM); chat surface unusable.
- Root-cause hypotheses: KeyRow holds remember{}'d local value+saved
  flags (no single source of truth) → phantom saves; response rate
  needs Orchestrator instrumentation before diagnosis.
- Attempts:
  - 2026-06-11 (side-panel agent): speced Layer 6 overhaul on the
    EXECUTION_BOARD (M6.1 persistent state engine, M6.2 three-layer
    control plane, M6.3 theme, M6.4 chat rebuild, M6.5 reliability
    triage). Implementation queued for next at-bat.
- Status: open — M6.x milestones AVAILABLE, M6.1/M6.5 are P0.
