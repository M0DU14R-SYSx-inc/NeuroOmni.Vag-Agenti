# Cache Prompt Rules (Anthropic)

Hard rules for every Claude API call made by Horizons or its
sub-agents. Usage/how-to lives in
[`../wiki/CACHE_PROMPTING.md`](../wiki/CACHE_PROMPTING.md).

## Rules

1. **Layout is `tools → system → messages`.** Any other order
   silently misses the cache.
2. **Maximum 4 `cache_control` markers per request.** Anthropic API
   limit. Place markers at the end of stable boundaries (after tools,
   after system, after the last cached user turn).
3. **Marker type:** `{type: "ephemeral", ttl: "5m" | "1h"}`. No other
   TTL is supported.
4. **Never edit a cached prefix mid-session.** Editing
   `CLAUDE_AT_HORIZONS.md` or `PROMPT_PREFIX.md` while a session is
   hot forces a 2x rewrite. Push prefix changes at session
   boundaries; pre-warm fresh.
5. **Pre-warm before fan-out.** Operator fires Router → Pre-warm
   before spawning sub-agents so the first sub-agent doesn't pay
   write cost.
6. **Sub-agent task instructions go in the first user message**, not
   in the cached system block — task-specific text invalidates reuse
   across sub-agents.
7. **Log the skill name as cache-key correlator** so hit/miss can be
   attributed in `cacheStatus`.

## Cost reference (do not edit; quoted from Anthropic docs)

  - Write: 1.25x base for 5m TTL, 2x base for 1h TTL.
  - Read: 0.1x base.
  - Break-even: ~2 reads (5m) / ~10 reads (1h).
