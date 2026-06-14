# At-bat protocol — hard rules

An at-bat is one agent's turn at one milestone. End-to-end: claim → work →
commit → release. The rotation pattern keeps each at-bat clean (no
"polish own turd" loop) and parallelizable across disjoint deps.

1. **Read the three pickup files first.** `SOTU.md` → `PROMPT_PREFIX.md`
   → `EXECUTION_BOARD.md`. If SOTU is older than the most recent commit
   date, flag it and ask before assuming it's current.
2. **Claim before touching code.** Edit the dashboard row in
   `EXECUTION_BOARD.md` (`AVAILABLE → CLAIMED`), commit the 1-line diff,
   then start work.
3. **One at-bat = one milestone = one fresh session.** Don't chain.
   The next milestone gets a fresh session so the agent isn't biased
   toward defending its prior choices.
4. **End-of-at-bat close-out (mandatory):**
   1. Tests / lint clean.
   2. Commit + push to the assigned feature branch.
   3. Update the dashboard row (`IN_PROGRESS → DONE` or `→ BLOCKED`).
   4. Draft `SOTU.md` for the next session.
   5. If a blocker surfaced, append to `wiki/FAILURE_LOG.md`.
   6. `git status` clean.
5. **Working tree clean.** Stop-hook enforces. Untracked files either get
   committed or `.gitignore`'d.
6. **No silent rule relaxation.** If a hard rule blocks you, raise it
   with the operator. Don't quietly bypass — the rule exists for a reason.
7. **Parallel at-bats are fine on disjoint deps.** Check the dashboard
   before claiming; if two agents pick the same milestone, the later
   one reroutes.
8. **Don't spawn sub-agents unless the operator asks.** The current
   model is operator-orchestrated.
