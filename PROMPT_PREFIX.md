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

Tonight's wins (2026-06-05 → 2026-06-06):

Code shipped to `claude/jolly-lamport-5cJJ4`:

  - `AnthropicDirectClient` with `systemPrompt`, `cache_control: ephemeral`,
    `lastUsage` (cacheCreation/cacheRead tokens), and `preWarm()`.
  - `VertexClient` (anthropic publisher) gained the same caching surface.
  - `CLAUDE_AT_HORIZONS.md` bundled into `horizons/src/main/assets/`.
  - `HorizonsApplication.wikiSystemPrompt` lazy-loads the asset.
  - `HorizonsApplication.preWarmAnthropic()` + `cacheStatus` StateFlow
    (states: idle, warming…, `write Nt (1h)`, `hit Nt (read)`, errors).
  - `Orchestrator` takes `systemPromptSupplier: () -> String` and threads
    it through `ProviderFactory.build`. Pre-existing ctor mismatch fixed.
  - `ProviderFactory` injects systemPrompt into Anthropic + Vertex/anthropic.
  - `InteractionLogger.logResponse` schema extended with
    `cache_creation_tokens` / `cache_read_tokens` JSONL fields.
  - `RouterPanel` got: Anthropic API key row, Pre-warm (1h) / Pre-warm (5m)
    buttons, live cache status line.
  - `AudioRecorder.kt`: removed `@Synchronized` on suspend funs that was
    failing CI (AtomicBoolean `running` is the actual guard).

Cloud agent (Anthropic managed-agents 2026-04-01):

  - Agent ID `agent_01RaU3nbhVGcFi9ZRcCinT9r` is at **v6**.
  - System prompt source: `agents/neuralmash-builder.system.md`.
    Edit it, then `ant beta:agents update --version <N>` against piped YAML
    `system: "@file://agents/neuralmash-builder.system.md"`.
  - MCPs stripped (github/drive/bigquery were unauthenticated; re-add once
    a vault is wired).
  - Built-in `agent_toolset_20260401` retained.
  - Environment: `env_01Srnj2osSiRfd1AGBaxDoVH` (`neuralmash-edge-moe-env`,
    unrestricted networking, $0.08/hr active).
  - Pre/post manifest snapshots in `agents/.snapshots/`.

Verified cache behaviour (Anthropic platform):

  - 2-turn session: turn 1 wrote 6956t; turn 2 read 6956t (0.1x cost).
  - Cross-session: brand-new session on same agent hit 3382t on first
    turn (server-side prefix cache survives between sessions).
  - Managed-agents service defaults to `ephemeral_5m` cache tier, NOT
    `ephemeral_1h`. Override only matters for our direct API path; the
    managed-agents path is platform-managed.

New artifacts in repo root:

  - `skills/horizons-wiki/SKILL.md` — open SKILL.md standard wrapper.
  - `agents/build-runner.yaml` — managed-agents YAML stub (slot 1).
  - `agents/neuralmash-builder.system.md` — source-of-truth for deployed
    agent's system prompt.
  - `agents/.snapshots/` — pre/post YAML snapshots.

What still needs to happen next:

  - **Rotate the Anthropic API key** leaked earlier in chat (still live).
  - Custom MCP design (long-term: hosted on Cloud Run, OAuth out to GCP,
    bridges Horizons ↔ console; deferred until awake-planning session).
  - Vault wiring on Anthropic console if managed-agent MCPs are wanted
    back (github/drive/bigquery).
  - Sub-agent slots 2/3/4 (wiki-groom, diagnostics, code-review) — only
    slot 1 has YAML; the others are blank.
  - Surface `cacheStatus` in a real Diagnostics panel (issue #10).
  - Wire `AnthropicDirectClient.lastUsage` into `InteractionLogger`
    calls on each chat response (logger has the fields, no call site).
  - Method 2 (compile/export workflow): end-of-session agent that
    grooms the conversation into a fresh `PROMPT_PREFIX.md` for next
    boot.
  - CI: bumping Node 20 actions to Node 24 before Sept 16 2026 deprecation.

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

