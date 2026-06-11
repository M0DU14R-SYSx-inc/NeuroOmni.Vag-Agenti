# Anthropic Prompt Cache — Usage

How to use the cache for cross-session sub-agent fan-out. Hard rules
live in [`../rules/CACHE_PROMPT_RULES.md`](../rules/CACHE_PROMPT_RULES.md).

## What gets cached

The system block: `CLAUDE_AT_HORIZONS.md` (stable architecture) +
`PROMPT_PREFIX.md` (rolling session prefix), bundled by
`skills/horizons-wiki/SKILL.md`.

## Pre-warm flow

1. Operator: Router panel → **Pre-warm (1h)** or **Pre-warm (5m)**.
2. App fires a 1-token Claude call with the wiki as the `system` block,
   `cache_control: {type: "ephemeral", ttl: "1h" | "5m"}` on the last
   entry.
3. Diagnostics panel `cacheStatus` flips to written / hit.
4. Sub-agent fan-out for the next hour (or 5m) pays read prices.

## TTL choice

  - **5m** — single sprint, ≤3 sub-agents, prefix likely to change
    after.
  - **1h** — overnight at-bat rotation, prefix locked.
  - Write cost: 1.25x (5m) / 2x (1h). Read: 0.1x. Break-even at ~2
    reads (5m) or ~10 reads (1h).

## Verify a hit

Router → cache status shows last write timestamp + read count.
Diagnostics → mirrors the same with build/version. If a hit isn't
landing, you almost certainly edited the prefix mid-session — see
[`MAINTENANCE.md`](MAINTENANCE.md) cache-safe edit rule.

## Don't

  - More than 4 `cache_control` markers (Anthropic API limit).
  - Layout other than `tools → system → messages`.
  - Edit the cached prefix mid-session.
  - Pre-warm with a stale wiki (push first, then warm).
