# Horizons — Rolling Session Prefix

**Purpose.** This file is the volatile per-session cacheable prefix sent
as the Anthropic `system` block on every Claude call routed through
the Horizons orchestrator (direct API or Vertex/anthropic). It pairs
with `CLAUDE_AT_HORIZONS.md` (the stable architecture-of-record):

  - `CLAUDE_AT_HORIZONS.md` — slowly-changing, edited between projects
  - `PROMPT_PREFIX.md` — refreshed pre-session, frozen during a session

The two are concatenated in this order (stable → volatile) so the
stable doc occupies the longest stable byte-prefix and earns the
deepest cache hit window. Edits to this file should be batched
between sessions; mid-session edits invalidate the cache and force a
re-write at 1.25x (5min TTL) or 2x (1hr TTL).

---

## Caching strategy

  - Wire: `system` block array with `cache_control: {type: ephemeral, ttl: "1h"}`
    on the last block. 1h TTL chosen for bursty session pattern (writes
    cost 2x but pay off after ~3 reads).
  - Breakpoint budget: 4 markers max per request. Layout:
      1. (tools, if any)
      2. system block (this file + wiki)
      3. (optional) history-summary breakpoint
      4. (reserved) mid-conversation breakpoint
  - Pre-warm: one `max_tokens: 1` call is fired before any sub-agent
    fan-out so the cache is written before parallel reads begin
    (Anthropic: cache entry only becomes available after the first
    response begins streaming).
  - Verification: `AnthropicDirectClient.lastUsage` /
    `VertexClient.lastUsage` expose `cacheCreationTokens` and
    `cacheReadTokens`. `isCacheHit` returns true when reads > 0.

## Session state — current as of latest commit

Today's wins (2026-06-05):

  - `AnthropicDirectClient` rewritten with `systemPrompt` parameter,
    `cache_control: ephemeral` markers, `lastUsage` exposure, and a
    `preWarm()` suspend function.
  - `VertexClient` (anthropic publisher) gained the same caching
    surface so Claude-on-Vertex inherits the strategy.
  - `CLAUDE_AT_HORIZONS.md` bundled into `horizons/src/main/assets/` so
    it ships in the APK as the static prefix source-of-truth.
  - `HorizonsApplication.wikiSystemPrompt` lazy-loads the asset and
    feeds it into the orchestrator via `systemPromptSupplier`.
  - `Orchestrator` gained `systemPromptSupplier: () -> String` and
    threads it into `ProviderFactory.build`. Pre-existing constructor
    mismatch with `ProviderLibrary` is fixed.
  - `ProviderFactory.buildAnthropic` and `buildVertex(anthropic)` now
    inject the system prompt; Gemini path left untouched (Google uses
    implicit server-side caching, no breakpoint to wire).

What still needs to happen next:

  - Pre-warm trigger choice (auto-on-boot vs Router button vs lazy).
  - Cache hit/miss surface in Diagnostics panel using `lastUsage`.
  - `InteractionLogger` schema extended with cache fields so every
    Claude response logs cache_creation / cache_read counts.
  - Method 2 (compile/export workflow): an end-of-session agent that
    grooms the conversation into a fresh `PROMPT_PREFIX.md` for next
    boot. This is the "lost 15 min of context" mitigation.

## Sub-agent assignments (4-agent fan-out template)

Every sub-agent reads the **identical** system bytes (this file +
`CLAUDE_AT_HORIZONS.md`). Per-agent role/task divergence happens in
the first user message, which is *not* cached. This is the design
that makes one cache write serve N parallel reads.

| Slot | Role | Primary scope | Tools the agent expects |
|------|------|---------------|-------------------------|
| 1 | build-runner | Compile, lint, test, CI watch | bash, read, grep, glob |
| 2 | wiki-groom | Edit `PROMPT_PREFIX.md` between sessions | read, edit, write |
| 3 | diagnostics | Inspect logs, surface cache stats, regressions | read, grep, glob |
| 4 | code-review | Review diffs before push | read, grep |

Slot composition is a stub — refine after first dispatch.

## Hard rules for every sub-agent

  - Working branch: `claude/jolly-lamport-5cJJ4`. Never push to `main`
    without explicit user permission.
  - Never commit credentials. `debug.keystore` is the documented
    exception (public-by-design for stable APK signatures).
  - Use the `--no-verify` flag only if the user asks. If a hook fails,
    fix the underlying cause.
  - Edits to `CLAUDE_AT_HORIZONS.md` and this file must be batched
    between sessions, not during.
  - On-device fleet: Moonshine for STT, Kokoro for TTS, OmniNeural-4B
    on Hexagon NPU v79 via Nexa SDK. No Python sidecar, no Vulkan, no
    Ollama, no `nexa serve`, no LiteLLM proxy.
  - Cloud failover: OpenRouter is the singular auto-failover. Vertex,
    Anthropic direct, AI Studio are explicit-pick only.

