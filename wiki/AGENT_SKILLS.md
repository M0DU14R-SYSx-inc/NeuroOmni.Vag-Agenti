# Agent skills — standard + how to add one

Horizons uses the Anthropic Skills standard (SKILL.md frontmatter +
Markdown body) for vendor-portable agent memory. A skill is the runtime
context block; the wiki is the human-readable source.

## Current skills

| Skill | Purpose | When to invoke |
|---|---|---|
| `skills/horizons-wiki/SKILL.md` | Bundles wiki + prefix as one cacheable block | Full architecture context needed (legacy bundler) |
| `skills/project-memory/SKILL.md` | Bundles the three pickup files + wiki | New agent landing, multi-tile coordination, greenfield work |

## Frontmatter shape

```yaml
---
name: <kebab-case-name>
description: |
  When to use it. When NOT to use it. Keep ≤ 3 sentences.
allowed-tools:
  - Read
  - Grep
  - Glob
---
```

`description` is the agent-facing trigger. Write it so a reading agent
can decide "does my at-bat need this skill" without invoking it.

## Body shape

Three blocks:

1. **What to read** — ordered list of files + 1-line each on why.
2. **What this skill is for / NOT for** — paired sections.
3. **Maintenance protocol** — who edits, when, what invalidates the cache.

## Adding a new skill

1. `mkdir skills/<name> && cd skills/<name>`
2. Write `SKILL.md` per the shapes above.
3. List it in this file's table.
4. If the skill bundles cacheable prefix content, mark it in the wiki's
   `CACHE_PROMPTING.md` so operators know its cache impact.

## What skills are NOT for

- One-off task instructions — those go in the agent's first user message.
- Secrets — use `AppStateStore`.
- Long architecture content — link the wiki, don't inline it.
