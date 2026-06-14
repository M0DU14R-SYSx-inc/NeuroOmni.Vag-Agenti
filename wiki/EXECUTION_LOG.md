# Execution log — protocol

The live execution log is `EXECUTION_BOARD.md` at repo root. This file is
the **protocol** for it, not the log itself.

## What counts as a log entry

- Milestone claim (status `AVAILABLE → CLAIMED`).
- Milestone advance (`CLAIMED → IN_PROGRESS → DONE`).
- Blocker raised (`IN_PROGRESS → BLOCKED` with a `blocker:` note).
- Failure handoff (`IN_PROGRESS → FAILED` with `failed_notes:` block,
  re-enters `AVAILABLE` for a stronger model).

## Append-only convention

- Don't rewrite history. Move superseded board sections to
  `.archive/EXECUTION_BOARD.<date>.md`.
- One commit per state change. Don't squash multi-agent edits — the
  commit log is the audit trail.
- Commit message: `chore(board): G2 claimed by main` (or similar).

## Multi-agent coordination

- Read the dashboard before claiming. If two agents pick up the same
  milestone, the later one re-routes — don't race.
- Disjoint deps run in parallel. The greenfield board is structured so
  G2 + G4 + G5 + G6 can run concurrently after G1.

## Archive policy

When a milestone family closes (e.g. all G* DONE), archive that section
of the board into `.archive/EXECUTION_BOARD.<milestone-family>.md` and
start a fresh board for the next family.

## Cross-link with failure log

If a milestone hits a known blocker, link the relevant `FAILURE_LOG.md`
entry from the `blocker:` note rather than re-explaining.
