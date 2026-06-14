# Git hygiene — hard rules

1. **Work on the assigned feature branch.** Never push `main` without
   explicit operator permission.
2. **Never** `--no-verify`, `--no-gpg-sign`, force-push (to anywhere
   shared), or `reset --hard` without confirming with the operator.
   If a hook fails, investigate the cause — don't bypass.
3. **Never commit credentials.** Exception: `release/debug.keystore`
   (public by design for stable APK signatures).
4. **Do NOT delete feature branches after merge.** Archive — rename to
   `archive/<name>`. The branch history is the audit trail.
   - Operator action item: uncheck Settings → General → "Automatically
     delete head branches" in GitHub.
5. **One commit per logical change.** Don't squash multi-agent edits to
   `EXECUTION_BOARD.md` — the commit log is the coordination layer.
6. **Commit message style:** lowercase scope, imperative mood, line ≤72c.
   Examples:
   - `chore(board): G2 claimed by main`
   - `core(nexa): wire VlmWrapper into NexaModelLoader`
   - `wiki: refresh CACHE_PROMPTING.md with 1h TTL break-even math`
7. **`git status` clean at end of every at-bat.** Untracked files either
   get committed or `.gitignore`'d. Stop-hook enforces this.
8. **Pull-requests:** only when the operator asks. Don't open a PR as
   the default close-out — the operator chooses when to merge.
