# Wiki — discovery index

This folder is an **index + maintenance docs**, not duplicated content.
Files in the wiki point at the real artifacts at repo root or under
`docs/` so existing raw-GitHub URLs keep working.

## What lives where

| Need | Read |
|---|---|
| State of the union (per-session) | `../SOTU.md` |
| Session prefix (pointers + rules) | `../PROMPT_PREFIX.md` |
| Live milestone list | `../EXECUTION_BOARD.md` |
| Architecture wiki (stable) | `../CLAUDE_AT_HORIZONS.md` |
| Greenfield rebuild scope | `../GREENFIELD_PLAN.md` |
| Hard rules | `../rules/README.md` |
| Project memory skill | `../skills/project-memory/SKILL.md` |
| Wiki bundle skill (Anthropic cache pre-warm) | `../skills/horizons-wiki/SKILL.md` |
| Deep-dive references | `../docs/` |
| Managed-agent system prompts | `../agents/` |
| Operator handoff doc | `../HANDOFF.md` |
| Agent setup wizard | `../AGENT_SETUP_WIZARD.md` |

## Maintenance docs in this folder

- `MAINTENANCE.md` — how to keep the wiki honest (cache invalidation cost,
  edit cadence, what counts as a stable vs volatile change).
- `CACHE_PROMPTING.md` — operator/agent usage directions for Anthropic
  prompt caching (5m vs 1h TTL, pre-warm flow, verification).
- `EXECUTION_LOG.md` — directions for the live execution board; rules for
  what counts as a log entry, append-only convention.
- `AGENT_SKILLS.md` — how the SKILL.md standard is used here and how to
  add a new skill.
- `FAILURE_LOG.md` — append-only ledger of recurring blockers + attempted
  fixes (Moonshine, Kokoro, GCP auth, NPU+GPU concurrency).
- `FORK_DECISIONS.md` — when to abandon a path; trigger + replacement +
  archived branch name.

## Precedence

If wiki guidance conflicts with `rules/`, **rules win**. Wiki is map +
maintenance; rules are the contract.
