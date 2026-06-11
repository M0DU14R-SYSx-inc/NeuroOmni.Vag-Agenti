---
name: horizons-wiki
description: |
  Provides the Horizons project architecture-of-record and current
  session prefix. Load this skill when working on any Horizons
  module (on-device Android stack, cloud orchestrator, NPU/Nexa
  integration, STT/TTS, Router/Diagnostics UI). Skill returns the
  full wiki + rolling session prefix as one bundle so the agent
  can answer "where does X live" and "what was decided about Y"
  without re-searching the codebase.
version: 0.1.0
license: project-private
tags: [horizons, android, nexa, npu, anthropic-caching]
---

# Horizons Wiki Skill

This skill packages two documents as a single context bundle:

1. `CLAUDE_AT_HORIZONS.md` — stable architecture-of-record
2. `PROMPT_PREFIX.md` — rolling per-session state and agent assignments

Both files live at the repo root. The skill is designed for the open
SKILL.md standard (Claude Code, Codex, Cursor, etc.) so the same wiki
is consumable from any compliant tool.

## When to use

  - Starting any Horizons sub-agent (build-runner, wiki-groom,
    diagnostics, code-review). Load this skill *first* before any
    user message so the cacheable prefix sits in the system block.
  - Answering questions about subsystem boundaries, file ownership,
    or design decisions captured in the wiki.

## How to use

The agent host should:

1. Read both files from the project root.
2. Concatenate stable-then-volatile (architecture, then prefix).
3. Pass as the `system` block with
   `cache_control: {type: "ephemeral", ttl: "1h"}` on the last entry.
4. Use this skill's name as a cache-key correlator in logs so cache
   hit/miss can be attributed.

## What NOT to do

  - Do not edit either file mid-session — invalidates the cache and
    forces a 2x re-write.
  - Do not embed agent-specific task instructions in this skill —
    those go in the first user message so they stay out of the
    cached prefix.

## Files referenced

  - `../../CLAUDE_AT_HORIZONS.md`
  - `../../PROMPT_PREFIX.md`
