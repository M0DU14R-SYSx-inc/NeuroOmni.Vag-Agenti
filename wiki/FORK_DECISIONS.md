# Fork Decisions

When a path is abandoned, record the trigger, the replacement, and
the archive name of the dead branch. Don't delete the branch —
archive it (see [`../rules/GIT_HYGIENE.md`](../rules/GIT_HYGIENE.md)).

## Format

```
### <Subsystem> — <Old> → <New>
- Decided: <date> by <operator/agent>
- Trigger: <what made us give up>
- Replacement: <chosen path>
- Archived branch: archive/<name>
- Linked failures: FAILURE_LOG.md#<anchor>
```

## Pending fork criteria

### Kokoro TTS — GPU path → standalone node (VoxSherpa)
- **Trigger**: GPU/NPU inference for Kokoro stays blocked after the
  current at-bat closes.
- **Replacement**: run Kokoro as a node (possibly GPU-pointed) using
  VoxSherpa's updated voice files (operator-staged).
- **Archive**: TBD on fork.
- Linked: [`FAILURE_LOG.md`](FAILURE_LOG.md) — "Kokoro TTS —
  phonemizer + GPU routing".

### Moonshine STT — CPU ASR → Parakeet NPU sidekick
- **Trigger**: Moonshine CPU path not landed by EOD 2026-06-11.
- **Replacement**: Parakeet NPU sidekick for the Omni neural chain
  (tried-and-true, was treated as ASR not just STT).
- **Archive**: TBD on fork.
- Linked: [`FAILURE_LOG.md`](FAILURE_LOG.md) — "Moonshine STT —
  int8 ConvInteger ORT_NOT_IMPLEMENTED".

## Closed forks

(none yet)
