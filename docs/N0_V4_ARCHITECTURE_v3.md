# N0.V4 — NeuroOmni / VagAgenti Architecture v3.0

**Last updated:** 2026-05-27
**Operator:** Derek LeGrand
**Status:** Active specification. Supersedes v2.0.

---

## 1. System Identity

N0.V4 (NeuroOmni / VagAgenti) is a personal AI orchestrator running as a native Android application on the Razr Ultra 2025. It is NOT a cloud service, NOT a PWA, NOT a browser extension. It is a Kotlin/Jetpack Compose APK installed directly on the device.

The system has three jobs:
1. **Voice interface** — hear Derek (STT), speak back (TTS), see the screen (vision)
2. **Provider routing** — connect to whichever frontier model or local model Derek toggles on, then get out of the way
3. **Apprentice learning** — log interactions, surface patterns, get smarter at dispatch over time via fine-tuning

---

## 2. Hardware Nodes (All Acquired)

### Primary Edge — Razr Ultra 2025
- **SoC:** Snapdragon 8 Elite (SM8750)
- **NPU:** Hexagon NPU (45 TOPS)
- **GPU:** Adreno (Vulkan capable)
- **CPU:** Kryo (ARM Neon)
- **RAM:** 12 GB
- **USB:** USB-C 2.0 only (480 Mbps — NOT USB 3.x)
- **Wi-Fi:** 802.11 a/b/g/n/ac/6e/7
- **Display Alt Mode:** None (no DisplayPort over USB-C). Supports Motorola Ready For (wireless Miracast).
- **Role:** Runs the N0.V4 app. All on-device inference. Primary user interface.

### Secondary Hub — Rubik Pi 3
- **SoC:** Qualcomm Dragonwing QCS6490
- **NPU:** Hexagon 770 (12 TOPS)
- **GPU:** Adreno 643L
- **Network:** Gigabit Ethernet + Wi-Fi
- **OS:** Ubuntu 24.04 Noble on Qualcomm kernel
- **Role:** Tool execution, audit mirror, wiki host, Docker containers. Secondary NPU experiments (Nexa Linux ARM64 — to be validated, not blocking).

### Primary Hub — Jetson Orin Nano Super
- **GPU:** 1024 CUDA cores (67 TOPS)
- **RAM:** 8 GB unified LPDDR5
- **Storage:** Samsung 980 500GB Gen3 NVMe (ordered)
- **Network:** Gigabit Ethernet
- **OS:** JetPack 6.2.2 (must flash 5.1.3 first → firmware update → re-flash 6.2.2)
- **Role:** Heavy compute, Postgres + pgvector, PTL FastAPI server, Docker CLI #1 + #2 audit loops, model hot-swap vault, LoRA fine-tuning for OmniNeural apprentice loop.

### Auxiliary Nodes
- **S21 Ultra:** Vision node + diagnostics mirror (~20 TOPS). Fish Speech training node when TTS pipeline ready.
- **S9 FE Tablet:** VNC/wireless debug interface. Primary touch interface for development.
- **A9:** Wireless keyboard only.

---

## 3. Network Architecture

### Primary Transport: Tailscale over Wi-Fi / Ethernet

The Razr's USB-C is 2.0 only (480 Mbps). Wi-Fi 6E on the same phone delivers 1-2 Gbps real-world. Wireless is faster than wired on this specific device. Primary transport for all nodes is Tailscale over Wi-Fi (Razr) and Ethernet (Pi, Jetson).

### Secondary Transport: USB P2P Link (Razr ↔ Pi)

Android USB tethering (RNDIS) over USB-C creates a dedicated wired network link between the Razr and Rubik Pi 3. The Pi's Ubuntu kernel supports `CONFIG_USB_NET_RNDIS_HOST` natively. Plug Razr into Pi's USB-A port, toggle USB tethering in Android settings, Pi gets a `usb0` interface at 480 Mbps.

This is NOT the primary transport (Wi-Fi is faster). It serves specific roles:

- **Guaranteed delivery for model/adapter transfers.** Pulling a 2.5GB GGUF or pushing a LoRA adapter over USB is slower but rock-solid — no Wi-Fi contention, no router dependency, no drops mid-transfer.
- **Emergency SSH lifeline.** If the Pi's Wi-Fi config breaks (see Rubik Pi failure log), USB tethering from the phone is the recovery path. Zero-config: plug in, toggle tethering, SSH to `usb0` IP.
- **Zero-infrastructure pairing.** Works with no router, no Tailscale auth, no Wi-Fi password. Cable + toggle = connected.
- **Tool output piping.** Direct pipe for Claude Code output from Pi to phone, no router hop.
- **Simultaneous charging.** Phone charges from Pi's USB port while maintaining the data link.

USB P2P is Razr ↔ Pi only. The Jetson Orin Nano requires a kernel recompile to enable `CONFIG_USB_NET_RNDIS_HOST` on JetPack 6 — not worth the effort given that Pi and Jetson are on the same wired gigabit switch anyway. Razr reaches Jetson through Pi if USB P2P is the active link.

```
                    ┌──────────────────────────┐
                    │  Razr Ultra 2025         │
                    │  N0.V4 App + Termux      │
                    └──┬─────────────┬─────────┘
                       │             │
            USB P2P    │             │ Tailscale
            (tethering)│             │ (Wi-Fi 6E / cellular)
                       │             │
            ┌──────────▼──┐   ┌──────▼──────────┐
            │ Rubik Pi 3  │   │ Jetson Orin     │
            │ QCS6490     │   │ Nano Super      │
            │ Gigabit ETH │◄─►│ Gigabit ETH     │
            │             │   │                 │
            └─────────────┘   └─────────────────┘
                  wired gigabit LAN (same switch)
```

### Desk Setup (when at monitor)
- Jetson → HDMI (or DisplayPort) → primary monitor (development work)
- Razr propped on desk or on charger, N0.V4 app open showing diagnostics panel (Pi status, Jetson status, Tailscale mesh health, last audit entry, active models per node)
- No second monitor needed — the phone IS the diagnostics display
- Pi + Jetson wired to same gigabit switch
- Optional: Razr plugged into Pi via USB-C for P2P link + charging simultaneously

### Resilience Matrix

| Condition | What works | What degrades |
|-----------|-----------|---------------|
| All online, home (Wi-Fi + wired) | Full stack. Tailscale mesh. Pi+Jetson wired gigabit. | Nothing |
| All online, USB P2P active | Full stack. Razr↔Pi direct. Pi↔Jetson wired. Razr→Jetson routes through Pi. | Slightly higher latency to Jetson (extra hop) |
| Razr only, no Wi-Fi, no USB | Edge mode: OmniNeural, Whisper, Kokoro. Local JSONL logging. | No frontier, no Jetson compute, no Pi tools |
| Razr + cellular | Edge + Vertex/Anthropic API via cellular. Tailscale to home nodes if relay configured. | Latency on frontier calls |
| Pi Wi-Fi down | Razr USB tethers to Pi for emergency SSH + recovery. Jetson still on wired ETH. | Pi loses Tailscale until Wi-Fi restored or USB link established |
| Pi fully down | Jetson handles compute. Razr handles edge. Audit mirror offline. | No secondary tool execution, no USB P2P option |
| Jetson down | Razr edge + direct Vertex/Anthropic from phone. Pi handles light tools. | No heavy compute, no Postgres, no training loop |

---

## 4. On-Device Model Stack (Razr)

**Inference placement is split by hardware, not by a blanket rule.**

| Model | Where it runs | Why |
|-------|--------------|-----|
| OmniNeural-4B (Stack C) | **In-app (Nexa SDK)** — mandatory | Only the in-app SDK can reach the **Hexagon NPU**. Termux is CPU-only Linux and cannot touch the NPU; running it there loses the entire point. |
| Whisper Large-v3 (Stack A) | **Termux** (`whisper.cpp` + `termux-microphone-record`) | CPU-bound anyway. Termux avoids hand-writing Android NDK/JNI bindings and reuses the existing `RUN_COMMAND` bridge. Quality-over-latency makes CPU acceptable. |
| Kokoro TTS (Stack E) | Either — in-app (Vulkan) **or** Termux (`termux-tts-speak`) | In-app gets GPU/Vulkan; Termux is simpler. Both documented below. |
| VAD / wake word (Stack B) | In-app, **optional** | Tiny always-on; skipped while push-to-talk is the activation model. |

> Rationale: NPU/GPU-accelerated models earn their in-app complexity; CPU models do
> not, so route them through Termux to cut app-dev work. This revises the earlier
> "nothing runs in Termux for inference" stance, which was too absolute.

> **Design principle — quality over latency.** At every layer (ASR model size, TTS
> voice, frontier model choice) prefer the higher-quality option even when it is
> slower. Output that streams faster than a human can read or hear it is wasted
> speed; rich, accurate, human-paced inference is the goal. Do not "optimize" a
> heavier model down to a lighter one for speed alone without explicit sign-off.

### Stack A — Speech-to-Text
- **Model:** Whisper Large-v3 (or distil-large-v3). *Not* Tiny — quality-first per
  the principle above; Derek dislikes Google/FUTO ASR quality and accepts the
  slower, heavier model for materially better transcription.
- **Runtime:** `whisper.cpp` in **Termux** (preferred — `pkg install`, no JNI),
  driven via `RUN_COMMAND` intent; `termux-microphone-record` captures audio, the
  app reads back the transcript. In-app whisper.cpp binding remains a fallback.
- **Hardware:** CPU (ARM Neon) — acceptable since quality > latency, no cloud, no Google
- **Activation:** Push-to-talk **mic button** (no always-on wake word required —
  Derek is fine pressing a button; wake word / VAD remains optional, Stack B).
- **Role:** Voice input, fully offline and private.

### Stack B — Wake Word / VAD
- **Model:** Silero VAD + optional openWakeWord
- **Runtime:** ONNX Runtime Mobile
- **Hardware:** CPU (near-zero power)
- **Role:** Detect speech onset, trigger Whisper. Prevent processing silence.
- **Status:** Optional / deferred — push-to-talk mic button (Stack A) is the primary
  activation model. Wake word is a later convenience, not required for the build.

### Stack C — Language Model (Edge)
- **Model:** OmniNeural-4B (VLM — text + vision)
- **Runtime:** Nexa SDK (Android Kotlin library)
- **Hardware:** Hexagon NPU
- **Role:** On-device reasoning, intent parsing, meta-prompt building, vision (screen reading, photo analysis). NOT the universal dispatcher — see Section 6.
- **Auth:** NEXA_TOKEN required (free signup at nexa.ai)

### Stack D — Prompt Translation Layer
- **Model:** Haiku (via Vertex) or Gemini Flash (via AI Studio)
- **Runtime:** HTTP client in the app
- **Hardware:** Cloud
- **Role:** Clean up raw STT output before sending to frontier. Optional — skip when edge-only.

### Stack E — Text-to-Speech
- **Model:** Kokoro-82M (multi-language, int8 quantized)
- **Runtime:** ONNX Runtime Mobile with Vulkan execution provider
- **Hardware:** Adreno GPU (Vulkan)
- **Role:** Neural voice output. Streaming, low-latency.
- **System TTS alternative:** Sherpa-ONNX APK installs Kokoro as Android system TTS engine. Any app that calls Android TTS (including `termux-tts-speak`) gets Kokoro voice.

---

## 5. Provider Routing (Toggles)

The toggle IS the routing decision. Derek sets it, then talks directly to whatever's behind it. OmniNeural does NOT intercept or re-route every request — it handles STT and mechanical glue only.

### Available Providers

| Toggle | Path | Auth | Billing |
|--------|------|------|---------|
| Edge (offline default) | OmniNeural-4B on Hexagon NPU | N/A | Free (battery) |
| Anthropic Direct | Phone → api.anthropic.com | Anthropic Console API key (`x-api-key` header) | Anthropic credits (pay-per-token) |
| Vertex Claude | Phone → Vertex endpoint (or Phone → Tailscale → Jetson PTL → Vertex) | GCP service account | GCP credits |
| Vertex Gemini | Phone → Vertex endpoint | Same GCP service account | GCP credits |
| AI Studio Gemini | Phone → generativelanguage.googleapis.com | AI Studio API key (`x-goog-api-key`) | GCP credits (same pool as Vertex if same billing project) |
| Claude Code CLI | Phone → Termux RUN_COMMAND intent → claude CLI | gcloud ADC + `CLAUDE_CODE_USE_VERTEX=1` | GCP credits |
| Gemini CLI (Workspace) | Phone → Termux RUN_COMMAND intent → gemini CLI | Workspace OAuth | Workspace allowance |
| Gemini CLI (Vertex) | Phone → Termux RUN_COMMAND intent → gemini CLI | `GOOGLE_GENAI_USE_VERTEXAI=true` | GCP credits |

### Kotlin Implementation

```kotlin
sealed class FrontierProvider {
    object Edge : FrontierProvider()  // OmniNeural local
    data class AnthropicDirect(val apiKey: String) : FrontierProvider()
    data class VertexClaude(val serviceAccount: ServiceAccountJson) : FrontierProvider()
    data class VertexGemini(val serviceAccount: ServiceAccountJson, val model: String) : FrontierProvider()
    data class AIStudioGemini(val apiKey: String, val model: String) : FrontierProvider()
    data class TermuxCLI(val command: String, val envVars: Map<String, String>) : FrontierProvider()
}
```

### Billing Pools

| Pool | What draws from it | How to monitor |
|------|-------------------|----------------|
| GCP credits ($1200+) | Vertex Claude, Vertex Gemini, AI Studio Gemini, Claude Code via Vertex, Gemini CLI via Vertex | GCP Console → Billing |
| Anthropic Console | Anthropic Direct API calls | console.anthropic.com |
| Workspace allowance | Gemini CLI via Workspace OAuth | Google Workspace admin |
| Free (battery) | OmniNeural edge, Whisper, Kokoro, VAD | N/A |

### Non-Programmable Subscriptions (for interactive use only, NOT usable from the app)

| Subscription | Where it works | Monthly cost |
|-------------|---------------|-------------|
| Claude Pro | claude.ai web/app only | $20 |
| Gemini Advanced / Google One AI Premium | gemini.google.com app only | $20 |

---

## 6. OmniNeural's Role (Corrected)

OmniNeural is NOT a universal dispatcher that intercepts every message. Its role is:

1. **STT layer** — converts voice to text via Whisper, feeds to the active provider
2. **TTS layer** — takes text output from the active provider, feeds to Kokoro for speech
3. **Mechanical glue** — when the active provider says "upload file X" or "open app Y," OmniNeural translates that into Android actions. **Preferred mechanism: fire an Intent to Tasker** (mature automation app already installed) rather than hand-building an Accessibility Service. Tasker performs the system action and returns a result; Termux `RUN_COMMAND` and Intent launches cover the rest. A custom Accessibility Service is a last resort only for actions Tasker can't reach.
4. **Active dispatcher ONLY when:**
   - Edge mode is toggled on (no internet)
   - Derek explicitly asks "which tool should I use for this?"
   - Derek gives an ambiguous command that doesn't map to the active toggle

Over time, OmniNeural learns Derek's patterns and handles more dispatch locally. Early on, it's mostly a pass-through to frontier.

---

## 7. Apprentice Training Loop

### Logging
Every interaction logs to `interaction_logs.jsonl` in app-internal storage:
- Timestamp
- Input (text after STT)
- Active provider toggle
- Provider response (truncated to first 500 chars)
- Action taken (if any)
- Derek's correction (if any)

### Sharpening Cadence (Performance-Triggered)
- Weeks 1–4: 5-day cycles
- Weeks 5–8: 10-day cycles
- Week 9+: 20-day cycles
- Deployable at 8 weeks

### Pipeline
1. Interaction logs accumulate on Razr
2. Sync to Jetson via Tailscale (nightly cron or manual)
3. Docker CLI #2 on Jetson reviews logs for patterns (failure, correction, success)
4. Approved patterns → LoRA training dataset
5. Vertex AI LoRA fine-tunes OmniNeural-4B adapter
6. Adapter pushed back to Razr via Tailscale
7. App hot-swaps the adapter on next restart

### Docker CLI #1 — Execution Auditor (Jetson)
- Watches PTL server requests/responses
- Logs latency, token counts, error rates
- Flags specification drift (model responding outside expected behavior)

### Docker CLI #2 — Quality Auditor (Jetson)
- Reviews interaction logs for:
  - Sycophantic confirmation (model agrees when it shouldn't)
  - Silent failure (model reports success, output is wrong)
  - Contradiction (model says X, later says not-X)
- Outputs audit report as structured JSONL
- Feeds into sharpening cadence decision

---

## 8. Jetson Hub Services

### Postgres + pgvector
- Vector similarity search over Derek's knowledge base
- Stores wiki entries, interaction log embeddings, failure log embeddings
- Accessible via Tailscale from all nodes

### PTL FastAPI Server
- Prompt Translation Layer: receives raw prompts from any node, routes to appropriate Vertex model, returns response
- Handles service-account auth centrally (one JWT flow, all nodes benefit)
- Exposes `/v1/chat/completions` (OpenAI-compatible) so any client can talk to it

### Model Hot-Swap Vault (NVMe)
Priority models for the Samsung 980 500GB:

| Model | Size (Q4) | Use Case |
|-------|-----------|----------|
| Llama 3.1 8B | ~4.5GB | General reasoning |
| Qwen3-4B | ~2.5GB | Fast inference |
| LFM2.5-Audio-1.5B | ~1.2GB | Unified speech (evaluate) |
| Voxtral 4B | ~2.5GB | TTS benchmark |
| MiniCPM-o 2.6 (8B) | ~4.5GB | Multimodal when docked |
| NeMo Canary-1B | ~1GB | ASR + AST |

---

## 9. Three Execution Layers in the N0.V4 App

### Layer 1 — API Layer
HTTP calls to programmable endpoints. Toggle selects which. OkHttp/Ktor client in the Kotlin app. Handles streaming SSE for real-time token delivery.

### Layer 2 — Shell-Automation Layer
Runs CLI commands in Termux via Android's `RUN_COMMAND` intent. Captures stdout. Returns results to the app UI.

Used for: Claude Code CLI, Gemini CLI, git, gh, any Termux-installed tool — **and
Termux-hosted inference (Whisper Large ASR via `whisper.cpp`, see Stack A).**

### Layer 2.5 — Device-Automation Layer (Tasker + intents)
Off-loads Android system automation to installed apps via Intents instead of a
hand-built Accessibility Service:
- **Tasker bridge:** App → Intent → Tasker task → system action (taps, app launches,
  toggles, file ops) → result returned. Replaces most of the Accessibility work.
- **Screen capture for vision:** Trigger the **Aftiroid screenshot-tile** (Quick
  Settings tile / intent) to grab the current screen; the saved image is fed to
  OmniNeural as image input. Avoids `MediaProjection`'s per-session permission nag.
- Both are low-effort, reliable shortcuts around the two most fragile pieces of the
  original spec (Accessibility taps and MediaProjection capture).

### Layer 3 — Browser-Automation Layer (Phase 2)
Drives consumer web apps on Derek's behalf:
- **WebView automation:** Load claude.ai / NotebookLM / Perplexity inside an in-app WebView. OmniNeural uses JavaScript injection to click, type, upload.
- **Accessibility Service:** For native Android apps (Claude app, Perplexity app, Gemini app). Reads UI tree, dispatches taps/text.

NOT needed for initial build. The API and Shell layers cover ~70% of use cases.

---

## 10. Example Interactions

| Derek says | Toggle | What happens |
|-----------|--------|-------------|
| "Add today's markdown to my GitHub repo" | Vertex Claude (Claude Code CLI) | OmniNeural parses intent → Termux RUN_COMMAND → Claude Code CLI with GitHub MCP → push happens → result spoken back |
| "What's the best way to structure this project?" | Vertex Claude (API) | OmniNeural converts voice → text → sends to Vertex Claude endpoint → streams response → Kokoro speaks it |
| "Open NotebookLM, upload these files, build a knowledge graph" | Any (browser-automation, Phase 2) | OmniNeural opens NotebookLM in WebView → uploads files → enters prompt → notifies Derek when staged |
| "Take today's session prompts and add them as static artifacts in Claude" | Edge + browser-automation (Phase 2) | OmniNeural opens claude.ai → uploads files → types prompt → pings Derek to hit send |
| Random edge question with no internet | Edge | OmniNeural handles entirely on-device via Hexagon NPU |

---

## 11. Failure Taxonomy (Nate B Jones Framework)

| Failure Type | Definition | Detection |
|-------------|-----------|-----------|
| Context Degradation | Session loses critical information across turns or sessions | Docker CLI #2 compares early vs late session outputs |
| Specification Drift | Implementation silently diverges from spec | Audit scripts compare actual model/runtime vs architecture doc |
| Sycophantic Confirmation | Model agrees with Derek when it shouldn't | Docker CLI #2 flags agreement-without-evidence patterns |
| Tool Selection Error | Wrong tool chosen for the task | Failure log entry when tool produces unexpected output type |
| Cascading Failure | One failure propagates into subsequent attempts | Failure log tracks dependency chains |
| Silent Failure | System reports success but output is wrong | Docker CLI #1 validates output against expected schema |

---

## 12. Security

- GCP service account JSON: stored in Android Keystore (hardware-backed on Snapdragon 8 Elite), never in plaintext in APK
- Anthropic API key: same — Android Keystore
- Tailscale auth: managed by Tailscale app, device-level auth
- NEXA_TOKEN: app-internal SharedPreferences (encrypted)
- No keys in git repos. No keys in build artifacts. No keys in logs.
- Derek is final yes/no on all sends, deletions, and external actions.

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| v1.0 | 2026-04 | Original Gemini-authored spec |
| v2.0 | 2026-05 | Audited and corrected: PaliGemma→OmniNeural, chipset fixed, prompt translation layer added, test cases added, failure risk table |
| v3.0 | 2026-05-27 | PWA→native Kotlin app. TFLite→Nexa SDK. CPU ONNX→Vulkan for Kokoro. USB-C ribbon as primary→Tailscale as primary (USB 2.0 on Razr). Restored USB P2P (RNDIS tethering) Razr↔Pi as secondary link for guaranteed delivery, emergency SSH, zero-config pairing. Desk setup: phone as diagnostics display, no second monitor. Added Vertex Claude as primary frontier. Added AI Studio same-credit-pool. Corrected OmniNeural's role (not universal dispatcher). Jetson confirmed acquired. Added three execution layers. Added provider toggle matrix. Added billing pool breakdown. |
| v3.1 | 2026-05-31 | Added quality-over-latency design principle. Stack A ASR → Whisper Large-v3 (not Tiny), push-to-talk primary (wake word/VAD optional). Split inference placement by hardware: OmniNeural in-app (NPU-only), Whisper ASR via Termux whisper.cpp — revises the prior "nothing runs in Termux for inference" line. Added Layer 2.5 device-automation (Tasker intent bridge + Aftiroid screenshot-tile for vision), replacing hand-built Accessibility/MediaProjection as the primary path. |
