# Cache prompting — hard rules

For directions + trade-offs see `wiki/CACHE_PROMPTING.md`. This file is the
contract.

1. **Max 4 `cache_control` markers per request.** Layout:
   tools → system block → history-summary → reserved mid-conversation.
2. **`cache_control` lands on the last block of the prefix to cache.**
   Everything before is the key.
3. **Pre-warm before sub-agent fan-out.** One `max_tokens: 1` call so the
   cache is written before parallel reads begin. Anthropic's cache only
   becomes available after the first response begins streaming — without
   pre-warm, parallel sub-agents all miss.
4. **Never edit cached prefix mid-session.** Wiki + prompt prefix edits
   are batched between sessions. Mid-session edits invalidate at 1.25x
   (5m TTL) or 2x (1h TTL).
5. **TTL selection:** 5m for single sessions ≤5 min between turns. 1h for
   sub-agent fan-out or multi-turn over an hour. Below ~3 reads, 1h
   doesn't pay back.
6. **Verify hits.** Read `lastUsage.cacheReadTokens > 0` after each call.
   Surface the value in the Router/Diagnostics tile so the operator can
   confirm without parsing JSON.
7. **Cache key minimum is 1024 tokens.** Below that, no cache activity
   even if `cache_control` is set.
