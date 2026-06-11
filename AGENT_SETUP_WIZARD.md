# Horizons Agent Setup Wizard

> One page. Pick your row. Paste one file. Reply "go" when asked. Done.

---

## 🎯 The brainless table

Pick the row matching the tool you're using:

| If you're using... | Paste this one file | Then |
|---|---|---|
| **Any LLM you've never used before for this project** (Codex, Cursor, ChatGPT, Hermes, DeepSeek, Qwen, Gemini, local model — anything) | **`AGENT_GO.md`** as the first message | Wait for `READY.` reply, say `go` |
| Claude Code (web, CLI, IDE plugin) | **`SETUP_PROMPT.md`** as the first message | Reply `go` when asked |
| Anthropic Managed Agent (`ant` CLI) | **`MANAGED_AGENT_KICKOFF.md`** block via `ant beta:sessions:events send` | Reply `go` |

That's it. No system prompt to set separately. No milestone ID to fill in. No model tier dropdown. The agent self-orients, self-picks a milestone matching its capability, asks permission, then works.

**You do exactly two things: paste once, say "go" once.**

---

## 🌐 Direct download URLs

Save these somewhere local for offline access:

```
AGENT_GO.md               https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/AGENT_GO.md
SETUP_PROMPT.md           https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/SETUP_PROMPT.md
MANAGED_AGENT_KICKOFF.md  https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/MANAGED_AGENT_KICKOFF.md
```

One-liner to grab all three:

```bash
mkdir -p horizons-handoff && cd horizons-handoff
BASE="https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main"
for f in AGENT_GO.md SETUP_PROMPT.md MANAGED_AGENT_KICKOFF.md; do curl -sLO "$BASE/$f"; done
```

---

## 🔍 If anything goes wrong

| Symptom | Fix |
|---|---|
| Agent says it can't fetch the URL (offline model) | Also paste these alongside AGENT_GO: `EXECUTION_BOARD.md`, `PROMPT_PREFIX.md`. That's the offline bundle. |
| Two agents claimed the same milestone | First-to-commit-board wins. Tell the loser to pick another. |
| Agent gave up / spinning | Board entry says FAILED — restart with one tier stronger model, same milestone ID. |
| Agent claimed but never started | After ~30 min, manually edit board: `status: AVAILABLE`, `claimed_by: null`, push, dispatch fresh. |
| You want 5 agents in parallel | Open 5 sessions. Paste AGENT_GO in each. The board's `files:` list per milestone tells you which combos don't collide. |
| AGENT_GO doesn't fit in the session's context limit | Use the shorter `SETUP_PROMPT.md` instead — for Claude Code surfaces — or split AGENT_GO into prefix+launch (`UNIVERSAL_PREFIX.md` + `UNIVERSAL_LAUNCH.md`). |

---

## 🧰 Power-user / template-reuse note

The full toolkit is bigger than the 3 files above. You only ever need more if:

- **You're using this whole pattern for a different project.** Strip the Horizons-specific content from `AGENT_GO.md` (sections 2, 8, 9) and rebuild `EXECUTION_BOARD.md` with your milestones. Everything else is reusable as-is.
- **You're debugging the handoff machinery itself.** The full set:
  - `UNIVERSAL_PREFIX.md` + `UNIVERSAL_LAUNCH.md` — the original 2-paste split (AGENT_GO collapses them)
  - `HANDOFF.md` — full project history briefing
  - `CLAUDE_AT_HORIZONS.md` + `PROMPT_PREFIX.md` — architecture wiki + rolling state
  - `docs/LIGHTHOUSE.md` — deep-dive reference
  - `agents/neuralmash-builder.system.md`, `agents/sub-agent.system.md`, `sub-agent.agent.yaml` — managed-agent + at-bat YAML config

Grab the whole kit with one curl:

```bash
mkdir -p horizons-handoff && cd horizons-handoff
BASE="https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main"
for f in AGENT_GO.md SETUP_PROMPT.md MANAGED_AGENT_KICKOFF.md \
         UNIVERSAL_PREFIX.md UNIVERSAL_LAUNCH.md AGENT_SETUP_WIZARD.md \
         EXECUTION_BOARD.md PROMPT_PREFIX.md CLAUDE_AT_HORIZONS.md \
         HANDOFF.md sub-agent.agent.yaml; do
  curl -sLO "$BASE/$f"
done
mkdir -p docs agents
curl -sLo docs/LIGHTHOUSE.md "$BASE/docs/LIGHTHOUSE.md"
curl -sLo agents/neuralmash-builder.system.md "$BASE/agents/neuralmash-builder.system.md"
curl -sLo agents/sub-agent.system.md "$BASE/agents/sub-agent.system.md"
ls -la
```

But again — for 90% of sessions you only need `AGENT_GO.md`. Don't overthink it.
