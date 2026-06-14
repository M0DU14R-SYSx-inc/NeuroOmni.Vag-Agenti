# Cache prompting — operator + agent directions

Anthropic prompt caching cuts repeat-read cost by 10x. Horizons relies on
it for sub-agent fan-out (one cache write, N parallel reads).

## TTL trade-offs

| TTL | Write cost | Read cost | Use when |
|---|---|---|---|
| 5m (`ephemeral_5m`) | 1.25x | 0.1x | Single chat session, no fan-out, ≤5 min between turns |
| 1h (`ephemeral_1h`) | 2x | 0.1x | Sub-agent fan-out, multi-turn over an hour |

Break-even for 1h TTL: ~3 reads. Below that, pay the 5m premium instead.

## Pre-warm flow

1. Agent or Router tile fires a 1-token call (`max_tokens: 1`) with the
   system block fully assembled.
2. Anthropic's cache only becomes available **after the first response
   starts streaming** — without pre-warm, parallel sub-agents all miss.
3. Verify hit via `cacheStatus` flow in Router/Diagnostics:
   - `write Nt (1h)` — cache was written.
   - `hit Nt (read)` — cache was read.
   - `no cache activity` — prefix below the 1024-token minimum, or
     `cache_control` not on the right block.

## Wire shape

- `system` block as an array of `text` blocks.
- `cache_control: {type: ephemeral, ttl: "1h"}` on the **last block** of
  the prefix you want cached. Everything before it is the cache key.
- ≤ 4 `cache_control` markers per request total. Layout:
  1. tools (if any)
  2. system block (wiki + prefix)
  3. (optional) history summary
  4. (reserved) mid-conversation break

## Hard rules

See `rules/CACHE_PROMPT_RULES.md` for the rule contract. Highlights:

- Never edit cached prefix mid-session.
- Pre-warm **before** sub-agent fan-out, not after.
- One cache write serves N parallel reads — design fan-out around it.

## Verification

`AnthropicDirectClient.lastUsage` (and `VertexClient.lastUsage` for the
publisher path) expose `cacheCreationTokens` and `cacheReadTokens`.
`isCacheHit` returns true when reads > 0. Router tile surfaces these
live so the operator can confirm without parsing JSON.
