# Horizons UI — N0.V4 (NeuroOmni / VagAgenti)

Native **Android** app (Kotlin + Jetpack Compose) — the front-end shell of the N0.V4
personal AI orchestrator. Target device: **Motorola Razr Ultra 2025** (Snapdragon 8 Elite).

> This is **not** a web app or PWA. It is a Kotlin/Compose APK doing on-device inference.
> The retired v0.2.0 React PWA is reference-only. See [`docs/`](docs/) for the full specs.

## Specs

- [`docs/N0_V4_ARCHITECTURE_v3.md`](docs/N0_V4_ARCHITECTURE_v3.md) — system architecture, nodes, routing, training loop
- [`docs/HORIZONS_UI_SPEC_v3.md`](docs/HORIZONS_UI_SPEC_v3.md) — UI panels, execution layers, voice/vision pipeline

## The shape of the app

**Four panels** (Spec §2): Chat · Router · Terminal · Diagnostics
**Three execution layers** (Spec §4): API (HTTP/SSE) · Shell (Termux `RUN_COMMAND`) · Browser-automation (Phase 2)
**Instance profiles** (Spec §3): Personal · Red Agent · Collab

## Build

CI builds a debug APK on every push (`.github/workflows/android.yml`) and uploads it as
the **`NeuroOmni-debug-apk`** artifact. To build locally (Gradle 8.11.1, JDK 17):

```bash
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

| Tool | Version |
|------|---------|
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.8.0 |
| Kotlin | 2.1.0 |
| Compose BOM | 2024.12.01 |
| compileSdk / targetSdk | 35 |
| minSdk | 27 |

## Build progression

- **Session 1** — project scaffold + CI; empty app launches.
- **Session 2** — Horizons UI shell: four panels, dark theme, instance-profile state, Chat input + provider toggle.
- **Session 3** — `EdgeModel` interface + `StubEdgeModel`; Chat streams tokens from the selected edge model.

No secrets live in this repo. API keys / service accounts live in the Android Keystore on-device
(Architecture §12).
