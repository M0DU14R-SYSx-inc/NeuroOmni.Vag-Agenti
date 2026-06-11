# Horizons Wiki

Index of the living documentation. Files stay at their canonical
locations (root, `docs/`, `skills/`); this folder points at them so a
fresh agent or operator has one map instead of twelve.

## Where things live

| What | Canonical path | When to read |
|---|---|---|
| Single-paste agent kickoff | [`../AGENT_GO.md`](../AGENT_GO.md) | First thing any new session does |
| Stable architecture-of-record | [`../CLAUDE_AT_HORIZONS.md`](../CLAUDE_AT_HORIZONS.md) | Subsystem boundaries, file ownership |
| Rolling session prefix | [`../PROMPT_PREFIX.md`](../PROMPT_PREFIX.md) | Current session state, agent assignments |
| Live execution board | [`../EXECUTION_BOARD.md`](../EXECUTION_BOARD.md) | Claim → work → release every at-bat |
| Handoff protocol | [`../HANDOFF.md`](../HANDOFF.md) | Cross-session continuity rules |
| Deep-dive reference | [`../docs/LIGHTHOUSE.md`](../docs/LIGHTHOUSE.md) | ORT EP rules, weak-link analysis |
| Operator setup (Termux/Tasker) | [`../docs/TASKER_AND_TERMUX.md`](../docs/TASKER_AND_TERMUX.md) | First-time device wiring |
| Agent skills bundle | [`../skills/horizons-wiki/SKILL.md`](../skills/horizons-wiki/SKILL.md) | SKILL.md standard: wiki+prefix as one cacheable block |
| Sub-agent template | [`../sub-agent.agent.yaml`](../sub-agent.agent.yaml) | Spawning at-bat sub-agents |

## Wiki-local docs (live here)

  - [`MAINTENANCE.md`](MAINTENANCE.md) — how to keep the wiki + prefix + board honest
  - [`CACHE_PROMPTING.md`](CACHE_PROMPTING.md) — Anthropic prompt-cache usage directions
  - [`EXECUTION_LOG.md`](EXECUTION_LOG.md) — how to update the live execution board
  - [`AGENT_SKILLS.md`](AGENT_SKILLS.md) — SKILL.md standard, how skills get loaded
  - [`FAILURE_LOG.md`](FAILURE_LOG.md) — append-only ledger of repeated blockers + attempted fixes
  - [`FORK_DECISIONS.md`](FORK_DECISIONS.md) — when to abandon a path, what replaced it

## See also

  - Hard rules: [`../rules/README.md`](../rules/README.md). Rules beat
    wiki guidance when in conflict.
