# `com.horizons.core` — greenfield rebuild root

Everything under this package follows the 9-boundary stack and the Truman
Show principle. See `/GREENFIELD_PLAN.md` for the rationale.

## Packages

- `nexa/` — model loader, opaque engine handle, spec. No type labels.
- `state/` — `AppStateStore` single source of truth (replaces the
  `remember`-local credential pattern).

## Coming in follow-up commits

- `voice/SystemTtsClient.kt` — ported from `audio/`.
- `screen/ScreenshotCapture.kt` — ported from `screen/`.
- `log/CrashRecorder.kt`, `log/InteractionLogger.kt` — ported from `logging/`.
- `shell/TaskerBridge.kt` — ported from `tasker/`.
- `a11y/` — accessibility service skeleton.

Old packages stay live until the new wiring lands so HEAD keeps building.
