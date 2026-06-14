# Rules — hard contract

Rules are the contract. **Rules > wiki guidance** when in conflict.

| Rule file | Scope |
|---|---|
| `CACHE_PROMPT_RULES.md` | Anthropic prompt caching (TTL, breakpoint budget, edit cadence) |
| `GIT_HYGIENE.md` | Branch policy, commit safety, branch preservation |
| `AT_BAT_PROTOCOL.md` | How an agent claims, works, and hands off a milestone |

## Precedence

1. Hard rules in this folder.
2. Pickup-file directives (`SOTU.md`, `PROMPT_PREFIX.md`, `EXECUTION_BOARD.md`).
3. Wiki guidance (`CLAUDE_AT_HORIZONS.md`, `wiki/*`).
4. Inline comments, ad-hoc convention.

If a rule needs to change, the change goes through the operator. Don't
quietly relax a rule mid-session.
