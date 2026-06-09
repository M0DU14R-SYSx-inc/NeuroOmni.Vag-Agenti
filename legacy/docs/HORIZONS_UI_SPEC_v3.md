# Horizons UI — Application Specification v3.0

**Last updated:** 2026-05-27
**Target platform:** Native Android (Kotlin, Jetpack Compose)
**Target device:** Motorola Razr Ultra 2025 (Snapdragon 8 Elite)
**Status:** Specification. No code exists yet for the native app. The v0.2.0 React PWA is reference material for UI layout and streaming patterns only — it is NOT the build target.

---

## 1. What Horizons UI Is

Horizons UI is the front-end shell of the N0.V4 system. It is a native Android application that:

- **Listens** — STT via Whisper Tiny on CPU, always-on when app is active
- **Sees** — screen capture + OmniNeural vision for "what's on my screen" queries
- **Speaks** — TTS via Kokoro-82M on Vulkan GPU, streaming, interruptible
- **Executes** — calls APIs, runs CLI commands in Termux, drives other apps, uploads files, pushes to repos
- **Routes** — connects Derek to whichever provider he toggles on (edge, Anthropic, Vertex Claude, Vertex Gemini, CLI tools)
- **Learns** — logs every interaction to JSONL for the apprentice training loop on the Jetson

It is NOT a passive switchboard. It is NOT "just a router." It takes action, produces output, and accomplishes tasks across all three execution layers.

---

## 2. Panels

Four main panels, inherited from the v0.2.0 PWA layout but rebuilt in Jetpack Compose:

### Chat Panel
- Primary interaction surface
- Voice input (mic button or always-listening mode) + text input
- Streaming response display (token-by-token)
- Per-message TTS button (tap to hear any response read aloud)
- Auto-TTS toggle (reads every response automatically)
- Provider indicator showing which toggle is active
- Stream cancel button while response is in-flight

### Router Panel
- Provider toggle selector (see N0.V4 Architecture v3 Section 5 for full list)
- Active provider status (connected/disconnected, latency, last call timestamp)
- Billing indicator (which credit pool the current toggle burns)
- Endpoint configuration (API keys, service account paths, base URLs, model strings)
- Instance profile selector (Personal / Red Agent / Collab)

### Terminal Panel
- Live view of Termux output when shell-automation layer is active
- Command history
- Manual command input (for when Derek wants to type directly into Termux)
- Status indicators for Claude Code CLI and Gemini CLI sessions

### Diagnostics Panel (was "Tools" in v0.2.0)
- Rubik Pi 3 status: CPU/RAM/disk/uptime, Tailscale connection state, USB P2P link state
- Jetson Orin Nano Super status: GPU temp, CUDA utilization, Postgres connections, NVMe usage
- Tailscale mesh health: all nodes, latency between them, last heartbeat
- OmniNeural apprentice status: current adapter version, last training cycle date, accuracy trend
- Last audit log entry from Docker CLI #2
- Active model on each node
- Battery/thermal state of the Razr itself

---

## 3. Instance Profiles

Carried from v0.2.0 PWA, same concept:

### Personal (default)
- Full access to all providers, tools, filesystem, memory
- All API keys available
- Can execute shell commands, drive other apps, modify files
- Color: Blue

### Red Agent
- Sandboxed. No API keys exposed. No memory access. No filesystem write.
- Used for testing prompts, adversarial evaluation, untrusted input processing
- Can only use edge OmniNeural (no cloud calls)
- Color: Red

### Collab
- Shared state. Read-only endpoints.
- Used when Derek wants to show someone the system without giving them write access
- Can read responses but cannot execute actions, push to repos, or modify files
- Color: Yellow

---

## 4. Three Execution Layers

### Layer 1 — API Layer
Direct HTTP calls from the app to programmable endpoints.

**Implementation:** OkHttp or Ktor HTTP client in Kotlin. Supports:
- Anthropic Messages API (streaming SSE via `text/event-stream`)
- Vertex AI (streaming SSE, same format with different auth)
- OpenAI-compatible (streaming SSE — covers AI Studio Gemini, OpenRouter, any compatible host)
- Ollama (NDJSON streaming — for any self-hosted models on Pi/Jetson)

**Auth patterns:**
- Anthropic Direct: `x-api-key` header, key from Android Keystore
- Vertex: GCP service account JSON → JWT → Bearer token. Service account stored in Android Keystore.
- AI Studio: `x-goog-api-key` header, key from Android Keystore
- Ollama: no auth (Tailscale network provides access control)

**Model string abstraction:**
```kotlin
data class ModelConfig(
    val provider: FrontierProvider,
    val modelString: String,     // e.g. "claude-opus-4-7" or "claude-opus-4-7@20260514"
    val displayName: String,     // e.g. "Opus 4.7"
    val maxTokens: Int,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean
)
```

### Layer 2 — Shell-Automation Layer
Runs CLI commands in Termux from the app.

**Implementation:** Android `RUN_COMMAND` intent to Termux. App sends command string, receives stdout/stderr via broadcast receiver or file pipe.

**Used for:**
- Claude Code CLI (`claude` command, authed to Vertex or Anthropic)
- Gemini CLI (`gemini` command, authed to Workspace or Vertex)
- Git operations (`git push`, `git pull`, `gh` CLI)
- File operations on shared storage
- Any Termux-installed tool (Python scripts, curl, ssh to Pi/Jetson)

**Security:** Commands are constructed by the app (not arbitrary user shell input unless in manual Terminal panel mode). Instance profile gates which commands are allowed.

### Layer 3 — Browser-Automation Layer (Phase 2)
Drives consumer web apps on Derek's behalf.

**Implementation (two paths):**
- **WebView automation:** Load target site (claude.ai, NotebookLM, Perplexity) inside an Android WebView component within the Horizons app. Use `evaluateJavascript()` to click buttons, fill text fields, upload files, read responses.
- **Accessibility Service:** For native Android apps. Register Horizons as an Accessibility Service. Read other apps' UI trees via `AccessibilityNodeInfo`. Dispatch taps via `performAction(ACTION_CLICK)` and text via `performAction(ACTION_SET_TEXT)`.

**Used for:**
- "Open Claude master thread, ask: <prompt>" → WebView loads claude.ai, navigates to thread, types prompt, notifies Derek to send
- "Upload files A B C to NotebookLM" → WebView loads NotebookLM, triggers upload dialog, selects files
- "Open Perplexity, search <query>" → launches Perplexity app via intent or WebView

**NOT needed for initial build.** API and Shell layers cover ~70% of use cases. Browser-automation is Phase 2.

---

## 5. Voice Pipeline

### Input (STT)
- **Engine:** Whisper Tiny EN via whisper.cpp Android binding or ONNX Runtime Mobile
- **Hardware:** CPU (ARM Neon)
- **Behavior:** Always-on when app is active. VAD (Silero) detects speech onset, triggers Whisper. Final transcript appears in chat input field or gets sent directly depending on mode.
- **Fallback:** Android system STT (Google) via SpeechRecognizer API if Whisper fails to load

### Output (TTS)
- **Engine:** Kokoro-82M via ONNX Runtime Mobile with Vulkan execution provider
- **Hardware:** Adreno GPU (Vulkan)
- **Behavior:** Text-first by default. TTS is opt-in per message (tap speaker icon) or toggleable to "read all responses." App monitors device thermal/battery state and auto-disables TTS if resources are constrained.
- **Fallback:** Android system TTS (Sherpa-ONNX Kokoro APK if installed as system engine, otherwise Google TTS)
- **Interruption:** Derek speaking triggers VAD → Whisper, which cancels current TTS playback. New input takes priority.

---

## 6. Vision

- **Engine:** OmniNeural-4B (VLM) via Nexa SDK on Hexagon NPU
- **Screen capture:** Preferred — trigger the **Aftiroid screenshot-tile** (intent / Quick Settings tile); the saved image is fed to OmniNeural as image input. `MediaProjection` API is the fallback (re-prompts permission each session).
- **Camera:** Camera2 API for real-world photo capture, feeds to OmniNeural
- **Use cases:** "What's on my screen?", "Read this receipt", "What am I looking at?", photo-based Q&A

---

## 7. Local Storage

### Interaction Logs
- `interaction_logs.jsonl` in app-internal storage
- Every exchange: timestamp, input text, active provider, response preview, action taken, correction if any
- Synced to Jetson nightly via Tailscale for apprentice training loop

### Session State
- Current provider toggle selection
- Active instance profile
- Chat history (current session)
- Panel layout (positions, sizes, minimized/maximized state)

### Endpoint Configuration
- Provider configs (API keys in Android Keystore, base URLs and model strings in encrypted SharedPreferences)
- Tailscale node addresses for Pi and Jetson

### No Cloud Storage
- No Supabase. No Firebase Realtime Database. No cloud sync for chat history.
- All data is local or synced to Derek's own Jetson via Tailscale.

---

## 8. Dependencies (Kotlin/Gradle)

| Dependency | Purpose |
|-----------|---------|
| Jetpack Compose | UI framework |
| OkHttp / Ktor | HTTP client for API layer |
| Nexa SDK (Android) | OmniNeural-4B on Hexagon NPU |
| ONNX Runtime Mobile | Whisper Tiny, Kokoro, Silero VAD |
| Aftiroid screenshot-tile (intent) | Screen capture for vision (preferred) |
| MediaProjection API | Screen capture for vision (fallback) |
| Tasker (intent bridge) | Device automation without a custom Accessibility Service |
| Camera2 API | Real-world photo capture |
| SpeechRecognizer (Android) | Fallback STT |
| TextToSpeech (Android) | Fallback TTS (uses system engine = Sherpa-ONNX Kokoro if installed) |
| Android Keystore | Secure key storage |
| Tailscale (network) | Mesh connectivity to Pi/Jetson (separate app, not a library dep) |

---

## 9. What Carries From the v0.2.0 PWA

The React PWA (v0.2.0, ~800 lines JSX) is dead as a deployment target. But its design decisions carry forward as reference:

| PWA Feature | Carries to Kotlin? | Notes |
|------------|-------------------|-------|
| Four-panel layout | Yes | Same panels, rebuilt in Compose |
| Floating/draggable panels | Maybe | Compose doesn't have native windowing; may simplify to tabbed or split-pane |
| Dark theme (THEME object) | Yes | Same color palette, ported to Compose theme |
| Instance profiles (Personal/Red Agent/Collab) | Yes | Same concept, same access gating |
| Streaming SSE parsing (OpenAI/Anthropic/Ollama) | Yes | Same three adapters, ported to OkHttp/Ktor |
| Web Speech API STT | No | Replaced by Whisper Tiny on-device |
| Browser TTS | No | Replaced by Kokoro on Vulkan |
| Endpoint registry | Yes | Same model-agnostic registry, expanded with Vertex and AI Studio |
| Dock (minimized panels) | Maybe | Depends on Compose layout approach |

---

## 10. Build Approach

Build the app. All features, one project, one build. No phases, no slices, no artificial segmentation. The spec above defines what the app does — build it until it does all of it. Prioritize whatever is blocking Derek most on any given day.

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| v0.1.0 | 2026-04-16 | React PWA scaffold. 402 lines. Four panels, three endpoints, floating window system. All stubs. |
| v0.2.0 | 2026-04-19 | Wired streaming API calls (OpenAI/Anthropic/Ollama SSE), Web Speech STT, instance profiles, per-message TTS. Terminal and tools still stubbed. |
| v2.0 | 2026-04-23 | Redefined as "orchestration switchboard" with four control tiers. Incorrectly described as "never executes" — corrected in v3.0. |
| v3.0 | 2026-05-27 | Full rewrite as native Kotlin/Compose app spec. PWA retired. Added three execution layers (API, Shell, Browser-automation). Added Vertex Claude + AI Studio Gemini + CLI providers. Vision via OmniNeural VLM. Diagnostics panel replaces Tools panel. Corrected "never executes" — Horizons is an active executor. Build approach: just build it. |
| v3.1 | 2026-05-31 | Screen capture: Aftiroid screenshot-tile (intent) preferred, MediaProjection demoted to fallback. Added Tasker intent bridge for device automation. Quality-over-latency principle (Whisper Large ASR, push-to-talk). |
