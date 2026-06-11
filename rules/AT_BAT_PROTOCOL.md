# At-Bat Protocol

The rotating-at-bat pattern. Same model takes turns at full-spectrum
work — no split "builder" / "fixer" roles (those inherit each other's
blind spots).

## Rules

1. **Each at-bat is full-spectrum**: implement + build + verify +
   commit + open PR. Don't hand off mid-task.
2. **Fresh session per at-bat.** A long session that hits a wall
   tends to "polish its own turd" — archive it and spawn a new
   sub-agent (see `sub-agent.agent.yaml`).
3. **Claim before working.** Update
   [`../EXECUTION_BOARD.md`](../EXECUTION_BOARD.md) → `claimed`
   before the first code edit. See
   [`../wiki/EXECUTION_LOG.md`](../wiki/EXECUTION_LOG.md) for the
   state machine.
4. **Release on stall.** If you sit on `claimed` >30 min without
   moving to `working`, release the row.
5. **Two blocks = fork.** If a milestone gets blocked twice, open a
   row in [`../wiki/FORK_DECISIONS.md`](../wiki/FORK_DECISIONS.md)
   and surface it to the operator.
6. **Log failures.** Every blocker gets an append in
   [`../wiki/FAILURE_LOG.md`](../wiki/FAILURE_LOG.md) with the fix
   tried + outcome.

## Don't

  - Split build vs. fix between sessions.
  - Carry a stale session past one wall-hit.
  - Mark `done` without a merged PR link.
  - Open a fresh issue for a known failure — append to the existing
    `FAILURE_LOG.md` entry instead.
