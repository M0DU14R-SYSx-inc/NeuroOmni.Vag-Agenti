# Horizons Agent Setup Wizard

> One-page deployment guide. Pick your surface, follow 3 steps.
> Don't think harder than this.

---

## TL;DR — the only two files you ever need to paste

| File | Role | Paste into |
|---|---|---|
| `UNIVERSAL_PREFIX.md` | System prompt — who the agent is, what the project is, rules, file map | System-prompt / context field |
| `UNIVERSAL_LAUNCH.md` | First user message — bootstraps the agent + tells it which milestone to take | First user message |

That's it. The prefix tells the agent to fetch any other docs it needs from raw GitHub URLs.

---

## Pick your surface

### 🟢 Claude Code (web / CLI / IDE plugin)
**Use `SETUP_PROMPT.md` instead of `UNIVERSAL_LAUNCH.md`.** Claude Code-specific (uses its Agent tool, accesses its own MCPs, etc.).

```
1. Open a new Claude Code session pointed at your local clone of the repo
2. Drop SETUP_PROMPT.md content as the first user message
3. Reply "go" when it asks
```

### 🟢 Anthropic Managed Agent (`ant` CLI)
**Use `MANAGED_AGENT_KICKOFF.md`.** It restructures the agent's role from builder → wiki librarian + commit watcher (different from at-bat coding work).

```
1. Set ANTHROPIC_API_KEY in env
2. ant beta:agents update --agent-id <id> --system "$(cat agents/neuralmash-builder.system.md)"
3. ant beta:sessions create --agent <id> --environment-id <env>
4. Send MANAGED_AGENT_KICKOFF.md block as first user.message
```

### 🟢 Codex / Codex CLI / Cursor / ChatGPT / Hermes / DeepSeek / Qwen / any other LLM CLI or web chat
**Use `UNIVERSAL_PREFIX.md` + `UNIVERSAL_LAUNCH.md`.**

```
1. Start a new session/chat
2. Paste UNIVERSAL_PREFIX.md into the system prompt / system message field
3. Edit UNIVERSAL_LAUNCH.md: replace "MX.Y" with the milestone you picked from
   EXECUTION_BOARD.md, replace the "model tier" bold line with your model
4. Paste that as the first user message
5. Wait for "READY." reply, verify it picked the right milestone, reply "proceed"
```

### 🟡 GitHub Copilot Chat (Workspace mode)
**Put `UNIVERSAL_PREFIX.md` in `.github/copilot-instructions.md`** (already in repo if you want, or add it). Then chat with Copilot — it has the prefix baked in. Use `UNIVERSAL_LAUNCH.md` as the first message.

### 🔴 Offline / local model with no internet (Ollama, local llama.cpp, etc.)
**Upload `UNIVERSAL_PREFIX.md` + the docs it references** since the agent can't fetch from raw GitHub URLs. Minimum bundle:

```
UNIVERSAL_PREFIX.md       (system prompt)
UNIVERSAL_LAUNCH.md       (first user message)
EXECUTION_BOARD.md        (so it knows what's available)
PROMPT_PREFIX.md          (the latest pivots — lighthouse rules)
docs/LIGHTHOUSE.md        (deep-dive ref)
```

5 files. Only path that actually needs more than 2.

---

## Pick a milestone

Open `EXECUTION_BOARD.md`. Look at the "Active claims dashboard" first — see what's in-flight, avoid collisions.

Then scan for `status: AVAILABLE` milestones. Match difficulty to your model tier:

| Model | Max difficulty |
|---|---|
| Haiku 4.x / Sonnet-fast / Qwen-Coder small | 1-2 |
| Sonnet 4.x / Codex / Hermes 70B / DeepSeek-Coder | 1-3 |
| Opus 4.6 / GPT-5 / Qwen-Coder-32B | 1-4 |
| Opus 4.8 / O3 / human-in-loop | 1-5 |

Pick one whose `depends_on:` are all `DONE`. Note the milestone ID (e.g. `M1.4`).

---

## Run it

1. Start session, paste prefix + launch (with milestone ID + model tier filled in)
2. Agent replies `READY. ...`
3. You reply `proceed`
4. Agent claims (commits one-line board edit), does the work, pushes, waits for CI green, updates board to DONE
5. Agent ends with a recommendation for the next bat

If agent FAILS or loops, board entry goes to `status: FAILED` with a one-line `failed_notes:` block. You escalate the same milestone to a stronger model — repeat steps 1-5 with a higher-tier agent.

---

## Don't think wizard

```
Q: I just want to start.
A: Paste UNIVERSAL_PREFIX.md as system. Paste UNIVERSAL_LAUNCH.md as first message with MX.Y = a low-difficulty AVAILABLE milestone. Done.

Q: My agent says it can't fetch the URL.
A: It's offline. Upload the 5 files from the "🔴 Offline" section above.

Q: Two agents picked the same milestone.
A: First one to commit the board claim wins. Second one re-checks, picks something else.

Q: Agent gave up.
A: Board entry should be FAILED. Pick a model one tier higher, restart with same milestone ID.

Q: I want to spawn 5 agents at once.
A: Pick 5 milestones with no shared dependencies. Open 5 sessions. Same prefix + launch (different milestone per session). Run.

Q: Agent claimed but never started.
A: After 30 min, edit the board manually — set claimed_by back to null, status back to AVAILABLE. Dispatch fresh.

Q: I want to use this template for a different project.
A: Strip Horizons-specific lines from UNIVERSAL_PREFIX.md (sections 1, 2, 3, 6). Replace with your project's identity, locked stack, gotchas, code layout. Rebuild EXECUTION_BOARD.md from scratch with your milestones. UNIVERSAL_LAUNCH.md needs minimal changes (mostly the GitHub URL in the orientation steps).
```

---

## Direct download URLs (raw GitHub)

After PR #19 merges and `main` is canonical, swap `claude/jolly-lamport-5cJJ4` → `main` in these URLs.

```
UNIVERSAL_PREFIX.md         https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/UNIVERSAL_PREFIX.md
UNIVERSAL_LAUNCH.md         https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/UNIVERSAL_LAUNCH.md
EXECUTION_BOARD.md          https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/EXECUTION_BOARD.md
AGENT_SETUP_WIZARD.md       https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/AGENT_SETUP_WIZARD.md
PROMPT_PREFIX.md            https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/PROMPT_PREFIX.md
SETUP_PROMPT.md             https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/SETUP_PROMPT.md
MANAGED_AGENT_KICKOFF.md    https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/MANAGED_AGENT_KICKOFF.md
HANDOFF.md                  https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/HANDOFF.md
CLAUDE_AT_HORIZONS.md       https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/CLAUDE_AT_HORIZONS.md
docs/LIGHTHOUSE.md          https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main/docs/LIGHTHOUSE.md
```

One-liner to grab the whole kit (run anywhere with `curl`):

```bash
mkdir -p horizons-handoff && cd horizons-handoff
BASE="https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/main"
for f in UNIVERSAL_PREFIX.md UNIVERSAL_LAUNCH.md EXECUTION_BOARD.md \
         AGENT_SETUP_WIZARD.md PROMPT_PREFIX.md SETUP_PROMPT.md \
         MANAGED_AGENT_KICKOFF.md HANDOFF.md CLAUDE_AT_HORIZONS.md; do
  curl -sLO "$BASE/$f"
done
mkdir -p docs && curl -sLo docs/LIGHTHOUSE.md "$BASE/docs/LIGHTHOUSE.md"
```
