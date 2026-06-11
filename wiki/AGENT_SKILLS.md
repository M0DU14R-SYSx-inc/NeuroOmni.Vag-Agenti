# Agent Skills (Memory)

Skills are the agent's persistent memory: cacheable context bundles
that load before any user message so sub-agents don't re-derive
project state.

## Standard

Open SKILL.md standard — Claude Code, Codex, Cursor all consume the
same file. Each skill is a folder under `skills/<name>/SKILL.md` with
YAML frontmatter (name, description, version) and a Markdown body.

## Current skills

  - [`../skills/horizons-wiki/SKILL.md`](../skills/horizons-wiki/SKILL.md)
    — bundles `CLAUDE_AT_HORIZONS.md` + `PROMPT_PREFIX.md` as one
    cacheable system block.

## Loading order

1. Host reads `SKILL.md`, follows file references.
2. Concatenates stable-then-volatile (architecture → prefix).
3. Passes as `system` block with `cache_control: {type: "ephemeral",
   ttl: "1h"}` on the last entry.
4. Logs the skill name as cache-key correlator.

## Adding a new skill

  1. `mkdir skills/<name> && touch skills/<name>/SKILL.md`.
  2. Frontmatter: name, description, version, tags.
  3. Body: when to use, how to use, what NOT to do, files referenced.
  4. Add the skill to the agent template (`sub-agent.agent.yaml`
    `skills:` list) and re-deploy.
  5. Add a row to [`README.md`](README.md).

## Don't

  - Embed agent-specific task instructions in a skill (those belong in
    the first user message; keep cached prefix stable).
  - Edit a skill mid-session (forces 2x cache rewrite).
  - Stack skills that duplicate each other — pick one canonical.
