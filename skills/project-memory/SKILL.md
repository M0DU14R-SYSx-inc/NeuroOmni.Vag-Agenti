---
name: project-memory
description: |
  Bundles Horizons project memory — SOTU, prompt prefix, execution board,
  and the architecture wiki — as a single cacheable context block. Use when
  the at-bat needs full project context (architecture decisions, multi-tile
  coordination, greenfield rebuild work). Skip when the at-bat is a narrow
  isolated change (single-file fix, lint cleanup) — read only SOTU then.
allowed-tools:
  - Read
  - Grep
  - Glob
---

# Project memory — Horizons

This skill is the **canonical project memory bundle**. It loads the three
session pickup files plus the stable wiki as one block so agents that need
full context get it in one cache hit.

## What to read (in this order)

1. `SOTU.md` — current state of the union. Always read first. One screen.
2. `PROMPT_PREFIX.md` — pointers + rules. No inline content.
3. `EXECUTION_BOARD.md` — live milestones. Find your claim here.
4. `CLAUDE_AT_HORIZONS.md` — stable architecture wiki. The 9 boundaries,
   Truman Show, three control surfaces, state management.
5. `GREENFIELD_PLAN.md` — rebuild scope, salvage list, scrap list.

## What this skill is for

- Fresh agent landing in the repo, needs to know what's going on.
- Multi-tile coordination (changes that touch state, terminal, model layer).
- Greenfield work — porting salvage files, wiring Nexa SDK, building
  per-tile terminal, building the cloud-frontend adapter.

## What this skill is NOT for

- Single-file lint cleanup. Read SOTU only.
- Doc-only edits that don't touch architecture. Read SOTU + the doc.
- One-off CI fix. Read SOTU + the failing CI log.

## Maintenance protocol

- The skill itself doesn't get edited per-session. The files it points at do.
- If a new pickup file is added (e.g. `DECISIONS.md`), update §"What to read".
- Cache: this skill's content is cacheable. Edits invalidate the cache —
  batch between sessions.

## The three pickup files (operator-maintained)

| File | Updated when | Cadence |
|---|---|---|
| `SOTU.md` | End of every session | per-session |
| `PROMPT_PREFIX.md` | Rules / pointers change | between sessions |
| `EXECUTION_BOARD.md` | Milestone claim/advance | per-edit |

If you arrive in a session and SOTU is older than the most recent commit
date, flag it — the prior agent skipped close-out.
