# Glossary

One-liners for terms that show up in code, prompts, and the wiki. For a
narrow-scope sub-agent without the full wiki bundle, this is the cheat
sheet.

## Stack — the 9 boundaries

| # | Term | One-liner |
|---|---|---|
| 1 | **Nexa Studio** | Operator's curation tool for picking + staging models. Off-device. |
| 2 | **Nexa SDK / CLI** | Kotlin in-process Android SDK; entry point for loading models. |
| 3 | **Nexa Server** | OpenAI-compatible HTTP server. On Android = in-process, not a daemon. |
| 4 | **Nexa ML** | Nexa's runtime / compute layer below the SDK. |
| 5 | **Qualcomm QNN SDK** | NPU + GPU compute. Reached through Nexa's plugin layer. |
| 6 | **Android Accessibility Service API** | Android system surface for overlay + text injection. |
| 7 | **Gemma-4-E4B-IT** | Google MLLM. ~4B effective params. Released April 2026. GPU resident on Adreno via Nexa `cpu_gpu` plugin (GGUF). |
| 8 | **OmniNeural-4B** + **Parakeet TDT/ASR** | NPU residents (Hexagon). `plugin_id="npu"`. Co-resident. |
| 9 | **VoxSherpa** | `com.CodeBySonu.VoxSherpa`. Registers as Android system TTS engine. Already installed. CPU. |

## Hardware

| Term | Means |
|---|---|
| **Razr Ultra 2025** | Target device. Motorola foldable. Snapdragon 8 Elite, 16 GB RAM. |
| **Snapdragon 8 Elite** | Qualcomm SoC. Hexagon NPU v79 + Adreno GPU + Kryo Oryon CPU (8 cores). |
| **Hexagon NPU v79** | NPU silicon. Hosts boundary 8 via `plugin_id="npu"`. |
| **Adreno GPU** | GPU silicon. Hosts boundary 7 via `plugin_id="cpu_gpu"`, `device_id="dev0"`. |

## Concepts

| Term | Means |
|---|---|
| **Truman Show principle** | Models perform; they don't know cloud / router / type labels exist. |
| **9-boundary stack** | The ordered runtime layer list. No omissions, no workarounds. |
| **Three control surfaces** | (A) on-device runtimes, (B) cloud frontends, (C) per-tile terminals. |
| **Three pickup files** | `SOTU.md` → `PROMPT_PREFIX.md` → `EXECUTION_BOARD.md`. Session-open contract. |
| **At-bat** | One agent's turn at one milestone. Fresh session each. See `rules/AT_BAT_PROTOCOL.md`. |
| **Hot-swap** | Operator toggles a model; capability adapter unloads + loads; callers keep their handle. |
| **Per-tile terminal** | Every UI tile embeds a shell so the operator can patch the tile's code in-app. |
| **Phantom-save bug** | UI holding `remember{mutableStateOf(stored)}` diverges from persisted store. Killed by `AppStateStore`. |
| **Capability adapter** | Indirection that returns `NexaEngine`-shaped handles whether the backend is local or remote. |

## Code surfaces (post-greenfield)

| Path | Means |
|---|---|
| `com.horizons.core.nexa` | Opaque model loader + engine + spec. No type labels. |
| `com.horizons.core.state` | `AppStateStore` — single `StateFlow<Map>` source of truth. |
| `com.horizons.core.voice` | Salvaged: `SystemTtsClient` (VoxSherpa bridge). |
| `com.horizons.core.screen` | Salvaged: `ScreenshotCapture` (1024px downscale + JPEG q=85). |
| `com.horizons.core.log` | Salvaged: `CrashRecorder`, `InteractionLogger`. |
| `com.horizons.core.shell` | Salvaged: `TaskerBridge` (Termux RUN_COMMAND). |
| `com.horizons.core.a11y` | Salvaged: accessibility service skeleton (boundary 6). |

## Caching

| Term | Means |
|---|---|
| **5m TTL** | `ephemeral_5m`. 1.25x write, 0.1x read. For single session ≤5 min between turns. |
| **1h TTL** | `ephemeral_1h`. 2x write, 0.1x read. Break-even ~3 reads. For sub-agent fan-out. |
| **Pre-warm** | 1-token call to write the cache before sub-agent fan-out reads it. |
| **Breakpoint budget** | 4 `cache_control` markers per request max. |

## Files

| File | Pickup order | Role |
|---|---|---|
| `SOTU.md` | #1 | Per-session state of the union. |
| `PROMPT_PREFIX.md` | #2 | Streamlined pointers + rules. |
| `EXECUTION_BOARD.md` | #3 | Live G-milestone list + claims dashboard. |
| `CLAUDE_AT_HORIZONS.md` | reference | Stable architecture wiki. |
| `GREENFIELD_PLAN.md` | reference | Rebuild scope, salvage / scrap lists. |
| `DECISIONS.md` | reference | ADR log — the *why*. |
| `OPEN_QUESTIONS.md` | reference | Inbox for blockers awaiting operator answers. |
| `GLOSSARY.md` | reference | This file. |
| `wiki/` | reference | Discovery index + maintenance + failure / fork logs. |
| `rules/` | contract | Hard rules. Beats wiki guidance when in conflict. |
| `skills/project-memory/SKILL.md` | reference | Bundles project memory as one cache block. |
