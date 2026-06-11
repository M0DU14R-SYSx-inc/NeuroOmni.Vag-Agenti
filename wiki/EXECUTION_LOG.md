# Execution Board — Maintenance

The live artifact is [`../EXECUTION_BOARD.md`](../EXECUTION_BOARD.md).
This doc explains how to update it without trampling other at-bats.

## State machine

```
todo  →  claimed  →  working  →  done
                              ↘  blocked  →  (back to working or fork)
```

One commit per state change. Don't squash multi-agent edits — the log
*is* the history.

## Claim → release protocol

1. **Claim**: edit the milestone row, set status = `claimed`, stamp
   your agent id + UTC time.
2. **Working**: bump to `working` on first real edit. If you sit on
   `claimed` >30min without `working`, release it.
3. **Done**: flip to `done` + link to merged PR.
4. **Blocked**: flip to `blocked`, append one-liner to
   [`FAILURE_LOG.md`](FAILURE_LOG.md). If it's been blocked twice,
   open a fork decision in [`FORK_DECISIONS.md`](FORK_DECISIONS.md).

## Don't

  - Squash multi-agent commits to the board.
  - Reorder closed milestones (history is append-only).
  - Mark `done` without a merged PR link.
  - Claim something you can't start within the at-bat.

## Archive policy

When a layer fully closes, move its rows under an `## Archive` header
at the bottom of the board. Don't delete.
