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
- Status: open — CPU path may be a dead end. Fork criterion in
  `FORK_DECISIONS.md`.

### Kokoro TTS — phonemizer + GPU routing
- Opened: 2026-06
- Root cause: espeak-ng JNI vs Termux phonemizer path unresolved;
  GPU/NPU routing for inference not landed.
- Notes: operator has VoxSherpa's updated voice files staged.
- Attempts:
  - 2026-06: locked M1.2 spec to espeak-ng JNI (P0). Implementation
    pending.
- Status: open — fork criterion: if GPU path stays blocked, run as a
  node (possibly GPU-pointed) with VoxSherpa voices. See
  `FORK_DECISIONS.md`.

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
