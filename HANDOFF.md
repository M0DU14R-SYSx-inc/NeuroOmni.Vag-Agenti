# Horizons Handoff Package

Read this **first**. It tells you (the next agent, or the next session
of this one) everything you need to pick up where the last session
left off without re-deriving context.

## 1. What this project is

Horizons is an on-device AI assistant for the Motorola Razr Ultra 2025
(Snapdragon 8 Elite, Hexagon NPU v79). Locked decisions:

  - VLM: OmniNeural-4B-mobile on Hexagon NPU via Nexa SDK 0.0.24
  - STT: Moonshine (onnx-community/moonshine-base-ONNX, int8)
  - TTS: Kokoro (onnx-community/Kokoro-82M-v1.0-ONNX, q8f16, am_adam)
  - Cloud failover: OpenRouter (singular auto). Vertex / Anthropic
    direct / AI Studio are explicit-pick only.
  - NO Python sidecar, NO Vulkan, NO Ollama, NO `nexa serve`,
    NO LiteLLM proxy.
  - ABI: arm64-v8a only.
  - Working branch: `main`. Never push `main`
    without explicit user permission.

## 2. The two wikis

  - `CLAUDE_AT_HORIZONS.md` — stable architecture-of-record. Module
    boundaries, file ownership, locked decisions, prior bug learnings.
    Edit it **between sessions**, not during.
  - `PROMPT_PREFIX.md` — rolling per-session state. "Last session's
    wins," "what's open," sub-agent assignment table. Refreshed
    pre-session, frozen during a session.

Both ship as Android assets via `horizons/src/main/assets/` (only
`CLAUDE_AT_HORIZONS.md` is bundled today; `PROMPT_PREFIX.md` would be
the second asset once we groom it for prefix injection).

`HorizonsApplication.wikiSystemPrompt` lazy-loads the asset. The
`Orchestrator` threads it through `ProviderFactory.build` as the
`systemPrompt` for any `AnthropicMessages` / `Vertex(anthropic)` call,
where it's sent as a `system` block with
`cache_control: {type: "ephemeral", ttl: "1h"}`.

## 3. Cloud agent state (Anthropic managed-agents-2026-04-01)

  - **Agent ID:** `agent_01RaU3nbhVGcFi9ZRcCinT9r` (NeuralMash Edge MOE Builder)
  - **Current version:** see `agents/.snapshots/neuralmash-builder-post-update.yaml`
  - **System prompt source:** `agents/neuralmash-builder.system.md`
  - **MCPs:** stripped (github/drive/bigquery were unauthenticated).
    Re-add via `ant beta:agents update --mcp-server ...` once a vault
    is wired on console.anthropic.com.
  - **Built-in tools:** `agent_toolset_20260401` (bash, file ops, etc.)
  - **Environment ID:** `env_01Srnj2osSiRfd1AGBaxDoVH`
    (`neuralmash-edge-moe-env`, unrestricted networking, $0.08/hr)

To re-deploy after editing the system prompt:

```bash
source ~/.config/ant_env
export PATH="$PATH:$(go env GOPATH)/bin"
cd <repo-root>
CURRENT=$(ant beta:agents retrieve --agent-id agent_01RaU3nbhVGcFi9ZRcCinT9r --format raw --transform 'version')
ant beta:agents update --agent-id agent_01RaU3nbhVGcFi9ZRcCinT9r --version $CURRENT --format raw --transform 'version' <<YAML
system: "@file://agents/neuralmash-builder.system.md"
YAML
```

The `@file://` interpolation only works inside piped YAML, not in
flag values. Don't try `--system @file://...` directly — it'll land
literal.

## 4. Anthropic prompt caching — operational rules

  - 4 cache_control markers max per request. Layout: tools → system
    → messages, strict left-to-right.
  - Default TTL on our direct path: `1h` (2x write cost, but pays off
    after ~3 reads — bursty session pattern).
  - Managed-agents service defaults to **5m** tier regardless of
    what we set on direct calls. Different cache, different cost
    profile. Don't conflate them.
  - Edit wikis only between sessions. Mid-session edit = full
    re-write at 2x.
  - Pre-warm: one `max_tokens: 1` call BEFORE any sub-agent fan-out.
    `HorizonsApplication.preWarmAnthropic()` does this. RouterPanel
    has Pre-warm (1h) and Pre-warm (5m) buttons.
  - Verify: `AnthropicDirectClient.lastUsage.isCacheHit` /
    `VertexClient.lastUsage.isCacheHit`. Logged via
    `InteractionLogger.logResponse(... cacheCreationTokens, cacheReadTokens)`.

## 5. The "up to bat" process (per layer)

User's request: avoid the failure-loop compounding. Per layer (e.g.
"voice in pipeline"):

  1. **Bat 1 — builder** writes the implementation.
  2. **Bat 2 — adversary** loads the APK and exercises it cold,
     reports what broke (no context from Bat 1's reasoning).
  3. **Bat 3 — fixer** patches just the breaks Bat 2 reported.
  4. Loop until adversary reports green, then advance to next layer.

Critical rule: a bat does not polish their own turd. The adversary
must be a fresh sub-agent, not the builder. The fixer must not be
the adversary. Rotate roles per layer to avoid investment bias.

## 6. Kitchen wiring before sky layer

Priority order for THIS phase (until everything passes one full
loop):

  1. Verify OmniNeural-4B actually boots on the Razr (user-on-device).
  2. Voice IN: mic button → AudioRecorder → Moonshine → input field.
  3. Voice OUT: response → Kokoro → AudioTrack speaker.
  4. Termux round-trip: send `ls`, see output come back.
  5. Tasker bridge: outbound intent fire-and-forget.
  6. Screenshot button → VLM with imagePath.
  7. Diagnostics panel: cache, engine, STT, TTS status visible.

Sky-layer stuff (cloud MCPs, custom MCP design, managed-agents
fan-out) is DEFERRED until kitchen wiring passes.

## 7. Hard rules — non-negotiable

  - Never push `main` without explicit permission.
  - Never commit credentials. `debug.keystore` is the documented
    exception (public-by-design for stable APK signatures).
  - Never use `--no-verify` to bypass hooks. Fix the underlying cause.
  - Never destructive git ops (`reset --hard`, `push --force`,
    `branch -D`, `clean -f`) without confirming with the user.
  - No piecemealing. Multi-part work fans out in parallel via
    Agent tool calls in a single message.
  - No new abstractions or scaffolding the task didn't ask for.
    Three similar lines beats a premature abstraction.
  - Default to no comments. Only WHY-non-obvious gets a one-liner.

## 8. Known issues to flag for the user

  - **Leaked Anthropic API key from chat is still live.** Earlier
    session-2 paste embedded `sk-ant-api03-Ztnn...AyX8wAA`. User
    must rotate at console.anthropic.com → API Keys.
  - Managed-agents MCPs were stripped because the user's vault isn't
    populated. Re-add after vault wiring.
  - CI Node 20 actions deprecation: Sept 16 2026. Bump
    `actions/checkout@v4`, `actions/setup-java@v4`,
    `android-actions/setup-android@v3`, `actions/cache@v4` to v5/v6
    or set `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true`.

## 9. Files & locations cheat sheet

```
agents/
  build-runner.yaml                  # managed-agents YAML stub (slot 1)
  neuralmash-builder.system.md       # deployed system prompt source
  .snapshots/*.yaml                  # pre/post deploy manifests

skills/
  horizons-wiki/SKILL.md             # open SKILL.md wrapper for the wiki

horizons/src/main/assets/
  CLAUDE_AT_HORIZONS.md              # stable wiki, bundled in APK

horizons/src/main/java/com/horizons/
  HorizonsApplication.kt             # wikiSystemPrompt, preWarmAnthropic, cacheStatus
  orchestrator/Orchestrator.kt       # systemPromptSupplier thread-through
  provider/AnthropicDirectClient.kt  # cache_control + lastUsage + preWarm()
  provider/VertexClient.kt           # same caching surface (anthropic publisher)
  provider/ProviderFactory.kt        # injects systemPrompt into anthropic builds
  provider/ProviderLibrary.kt        # NamedBackend JSON CRUD
  provider/CredentialStore.kt        # encrypted store
  logging/InteractionLogger.kt       # logResponse has cache_creation/cache_read fields
  audio/AudioRecorder.kt             # PCM16 capture (mic→ShortArray)
  audio/ScreenshotCapture.kt         # MediaProjection screenshot helper
  model/MoonshineSttEngine.kt        # ONNX STT
  model/KokoroTtsEngine.kt           # ONNX TTS (am_adam)
  model/NexaVlmEngine.kt             # Nexa SDK NPU wrapper
  termux/TermuxBridge.kt             # RUN_COMMAND intent bridge
  ui/panels/RouterPanel.kt           # CRUD + downloads + pre-warm
  ui/panels/ChatPanel.kt             # main chat surface (mic/screenshot TBD)
  ui/panels/TerminalPanel.kt
```

PRs/branches the user cares about: `main` is
the only one. CI builds APK via `.github/workflows/build-apk.yml`
and publishes to `latest-debug` release.
