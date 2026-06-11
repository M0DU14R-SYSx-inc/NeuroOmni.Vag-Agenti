# Git Hygiene

## Branching

1. **All work merges to `main` via PR.** Agents do not push directly
   to `main`.
2. **One branch per at-bat.** Name pattern: `claude/<adjective>-
   <noun>-<short-id>` or operator-assigned.
3. **Do NOT delete feature branches after merge.** Archive instead:
   rename to `archive/<name>` (or leave as-is and rely on the
   `archive/` filter). History is the record.
4. **Operator action**: Settings → General → "Automatically delete
   head branches" must be **unchecked**.

## Commits

1. Never `--no-verify`.
2. Never `--no-gpg-sign`.
3. Never amend a published commit (use a new commit + force-with-
   lease only with operator approval).
4. Never commit credentials. `debug.keystore` is the documented
   exception (public-by-design for stable APK signatures).

## Destructive ops (require explicit operator approval each time)

  - `git reset --hard`
  - `git push --force` / `--force-with-lease`
  - `git branch -D`
  - `git clean -f`
  - Deleting any branch, tag, or release.

## PRs

  - Open via `mcp__github__create_pull_request`.
  - Merge via `mcp__github__merge_pull_request` once CI is green and
    operator gives the nod.
  - If CI fails after merge to `main`, fix-forward in a new PR — do
    not revert without operator approval.
