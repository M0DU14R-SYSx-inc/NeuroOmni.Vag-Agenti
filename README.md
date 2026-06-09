# Horizons (N0.V4)

On-device AI assistant for the Motorola Razr Ultra (Snapdragon 8 Elite).

## Monorepo layout

- `:shared` — pure WS message contract used by both apps.
- `:horizons` — the assistant app: Compose dashboard, floating tiles, IME, accessibility, overlay, Nexa VLM on Hexagon NPU, Moonshine STT, Kokoro TTS.
- `:watchdog` — separate app/process: hosts loopback WS server, owns telemetry, crash capture, fallback ladder, survives independently.
- `legacy/` — archived previous tree, reference only.

Build target: Razr Ultra only. No Tailscale, Ollama, multi-device, Whisper, GGUF/MLX, Kubernetes.

## Phase status

- Phase 0 (Nexa SDK link): verified green (`ai.nexa:core:0.0.24`, mavenCentral).
- Phase 1 (prove the NPU loop): scaffolded, not yet wired.
