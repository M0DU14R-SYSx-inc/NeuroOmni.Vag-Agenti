# Horizons — Session Prefix

> **Streamlined.** Pointer-first. The wiki is loaded by reference, not inlined.
> Agents that need full architecture detail follow the link; one-shot agents
> with a narrow scope don't pay for context they won't use.
>
> Read order: this file → `SOTU.md` → linked target if the task needs it.

---

## The three pickup files (read these, in order, every session)

1. **`SOTU.md`** — State of the Union. One screen. What just happened, what's
   next, what's stuck. The freshest signal.
2. **`PROMPT_PREFIX.md`** (this file) — pointers, rules, scope-limiting cues.
3. **`EXECUTION_BOARD.md`** — the active milestone list + claims dashboard.

Anything else is loaded **only if the task needs it**. The most useful
secondary references:

- `OPEN_QUESTIONS.md` — inbox of blockers awaiting operator answers. If
  your at-bat touches one, escalate; don't guess.
- `DECISIONS.md` — ADR-style *why* log.
- `GLOSSARY.md` — one-liners for terms, paths, models. Cheap context.

## The 9-boundary stack (Truman Show)

In order, no omissions, no workarounds:

1. Nexa Studio · 2. Nexa SDK/CLI · 3. Nexa Server (in-process on device) ·
4. Nexa ML · 5. Qualcomm QNN SDK · 6. Android Accessibility Service API ·
7. Gemma-4-E4B-IT (GPU) · 8. OmniNeural-4B + Parakeet TDT/ASR (NPU) ·
9. VoxSherpa (system TTS, CPU node, already installed).

Models inside Horizons **do not** know cloud/router/backends exist. Cloud
frontends, CLI access, API access, and model hot-swap live in adjacent
control surfaces (per-tile terminal, Router tile, separate cloud app).

Full detail: `CLAUDE_AT_HORIZONS.md`. The 9 are also enumerated in
`GREENFIELD_PLAN.md` with the rebuild scope.

## Hard rules (precedence: rules > wiki guidance)

- All work on the assigned feature branch (this session:
  `claude/jolly-lamport-5cJJ4`). **Never** push `main` without explicit
  operator permission.
- **Never** `--no-verify`, `--no-gpg-sign`, `push --force`, or `reset --hard`
  without confirming. Investigate hook failures; don't bypass.
- **Never** commit credentials. Exception: `release/debug.keystore` (public by
  design for stable signatures).
- Do not delete feature branches after merge — archive (rename to `archive/<name>`).
- Edits to `CLAUDE_AT_HORIZONS.md` and this file are **batched between
  sessions**, not during. Mid-session edits invalidate the prompt cache.
- Truman Show: model code never branches on "is this a VLM / STT / TTS" or
  "what backend am I." Capability surfaces only.

Full rule docs: `rules/` (CACHE_PROMPT_RULES, GIT_HYGIENE, AT_BAT_PROTOCOL).

## Caching

- `system` block array, last block carries `cache_control: {type: ephemeral, ttl: "1h"}`.
- ≤ 4 breakpoints: tools → system → history-summary → reserved.
- Pre-warm before sub-agent fan-out (1-token call so cache lands before reads).
- Verify hit via `cacheStatus` flow in Router/Diagnostics tile.

## What NOT to load unless the task needs it

`CLAUDE_AT_HORIZONS.md`, `GREENFIELD_PLAN.md`, `docs/`, `wiki/*`, `legacy/`,
`agents/*`. Pull them in only when the at-bat is about that area.

---

**Maintenance:** edit between sessions only. If you must change rules
mid-session, do it after handoff — current cache-write is wasted on rewrite.
