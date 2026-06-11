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

### Kokoro TTS — in-app CPU → standalone GPU node
- **Trigger**: in-app sherpa-onnx CPU synthesis proves too slow on
  device (latency to first audio chunk > ~2s for short replies).
- **Replacement**: run Kokoro as a separate node (possibly
  GPU-pointed) — requires a sherpa rebuild with a GPU provider, not
  an app change.
- **Archive**: TBD on fork.
- Linked: [`FAILURE_LOG.md`](FAILURE_LOG.md) — "Kokoro TTS —
  phonemizer + GPU routing".

### Moonshine STT — sherpa CPU → Parakeet NPU sidekick
- **Trigger**: sherpa-onnx Moonshine fails on-device verification or
  transcription quality/latency is unacceptable.
- **Replacement**: Parakeet (NeMo transducer) — with the sherpa
  runtime now in place this is a **config + model-archive swap**
  (OfflineModelConfig.transducer / nemo), not a code rewrite.
- **Archive**: TBD on fork.
- Linked: [`FAILURE_LOG.md`](FAILURE_LOG.md) — "Moonshine STT —
  int8 ConvInteger ORT_NOT_IMPLEMENTED".

## Closed forks

### Voice runtime — hand-rolled ORT-Java engines → sherpa-onnx
- Decided: 2026-06-11 by side-panel agent (operator green-lit the
  VoxSherpa direction).
- Trigger: Moonshine's hand-rolled seq2seq decode never produced a
  working on-device transcription (see FAILURE_LOG); Kokoro's
  speak() was a stub blocked on an espeak-ng JNI port nobody had
  bandwidth to write.
- Replacement: sherpa-onnx AAR v1.13.2 — OfflineRecognizer
  (Moonshine base int8) + OfflineTts (Kokoro v1.0 multi-lang,
  espeak-ng bundled). Maven onnxruntime-android dependency dropped;
  sherpa's ORT 1.24.3 is the single packaged runtime.
- Archived branch: none — old engines were replaced in place on
  `claude/sherpa-voice-stack` (pre-rewrite code remains in git
  history at tag-commit `5bc487d` and earlier).
- Linked failures: both Moonshine and Kokoro entries in
  [`FAILURE_LOG.md`](FAILURE_LOG.md).
