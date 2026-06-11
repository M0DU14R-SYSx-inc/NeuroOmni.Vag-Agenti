# Wiki Maintenance

How to keep the wiki, prefix, and execution board honest without
invalidating mid-session caches.

## Stable vs. volatile

  - `CLAUDE_AT_HORIZONS.md` — **stable**. Architecture-of-record.
    Editing it invalidates every cached prefix in flight (2x rewrite
    cost). Only touch on milestone close or architectural change.
  - `PROMPT_PREFIX.md` — **rolling**. Per-session state, agent
    assignments, hot decisions. Update at session boundaries, not
    mid-at-bat.
  - `EXECUTION_BOARD.md` — **live**. One commit per state change
    (claim → working → done). Multi-agent edits must not be squashed.

## Cache-safe edit rule

Never edit `CLAUDE_AT_HORIZONS.md` or `PROMPT_PREFIX.md` while a
cached session is hot. If you must, bump TTL afterwards via Router →
Pre-warm so the next sub-agent fan-out hits the new content. See
[`CACHE_PROMPTING.md`](CACHE_PROMPTING.md).

## What goes where

| Change type | File |
|---|---|
| New subsystem / file ownership | `CLAUDE_AT_HORIZONS.md` |
| Agent rotation, current at-bat, hot decisions | `PROMPT_PREFIX.md` |
| Claim/release/done on a milestone | `EXECUTION_BOARD.md` |
| Repeated failure + attempted fixes | `wiki/FAILURE_LOG.md` |
| Decision to abandon a path | `wiki/FORK_DECISIONS.md` |
| Hard rule change | `rules/<file>.md` |

## Index drift check

When you add a new root or `docs/` doc, add a row to
[`README.md`](README.md). Stale indexes are worse than no index.
