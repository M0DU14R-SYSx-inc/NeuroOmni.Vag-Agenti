# Horizons (N0.V4)

On-device AI assistant for the Motorola Razr Ultra (Snapdragon 8 Elite).

## Monorepo layout

- `:shared` — pure WS message contract used by both apps.
- `:horizons` — the assistant app: Compose dashboard, floating tiles, IME, accessibility, overlay, Nexa VLM on Hexagon NPU, Moonshine STT, Kokoro TTS.
- `:watchdog` — separate app/process: hosts loopback WS server, owns telemetry, crash capture, fallback ladder, survives independently.
- `legacy/` — archived previous tree, reference only.

Build target: Razr Ultra only. No Tailscale, Ollama, multi-device, Whisper, GGUF/MLX, Kubernetes.

## Where to look

- **New session?** Start with [`AGENT_GO.md`](AGENT_GO.md) — single-paste kickoff.
- **Wiki (discovery + maintenance):** [`wiki/README.md`](wiki/README.md).
- **Hard rules (cache, git, at-bat):** [`rules/README.md`](rules/README.md).
- **Live execution board:** [`EXECUTION_BOARD.md`](EXECUTION_BOARD.md).
- **Architecture-of-record:** [`CLAUDE_AT_HORIZONS.md`](CLAUDE_AT_HORIZONS.md).

## Phase status

- Phase 0 (Nexa SDK link): verified green (`ai.nexa:core:0.0.24`, mavenCentral).
- Phase 1 (prove the NPU loop): scaffolded, not yet wired.
