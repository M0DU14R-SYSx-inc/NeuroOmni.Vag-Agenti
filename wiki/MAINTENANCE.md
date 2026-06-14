# Wiki maintenance

## What changes when

| Change | File | Cadence |
|---|---|---|
| Per-session snapshot | `SOTU.md` | every session close |
| Pointer / rule tweak | `PROMPT_PREFIX.md` | between sessions only |
| Architecture-of-record | `CLAUDE_AT_HORIZONS.md` | between projects |
| Milestone claim/advance | `EXECUTION_BOARD.md` | live (1-line commits) |
| Rule addition / change | `rules/*.md` | between sessions, announce in SOTU |

## Cache-invalidation cost

`PROMPT_PREFIX.md` + `CLAUDE_AT_HORIZONS.md` are the system block. Editing
either mid-session forces a cache rewrite — 1.25x (5m TTL) or 2x (1h TTL).
**Never edit the wiki mid-cached-session.** If a fix is urgent, defer the
wiki edit; the cache hit on subsequent reads dwarfs the edit's value.

## Edit rules

- Wiki files are append-friendly. Reorgs go through `.archive/`.
- When superseding a file, move the old version to
  `.archive/<name>.<date>.md` rather than deleting. The audit trail is the
  point.
- Cross-link, don't duplicate. If a fact lives in two files, one of them
  will rot.

## What stays out of the wiki

- Anything secret (keys, tokens, OAuth state) — those go in
  `EncryptedSharedPreferences` via `AppStateStore`.
- Per-session log spam — that's the operator's chat history, not the wiki.
- Speculative architecture drafts — those go in `docs/` until adopted.
