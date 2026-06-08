# Horizons Universal Launch Prompt

> **Use this as the first user message** to a fresh coding agent
> (Claude, Codex, Cursor, Hermes, DeepSeek, Qwen, ChatGPT, etc.) after
> giving it `UNIVERSAL_PREFIX.md` as system context.
>
> Block between the `---` lines is the paste. Edit the **bold** parts
> per dispatch (which milestone, which model tier).

---

```
You're joining the Horizons project mid-build. The UNIVERSAL_PREFIX.md
in your system context is binding — re-read sections 4, 7, and 8 if
you're unclear on rules, build status check, or the at-bat protocol.

Before touching any code, run these orientation steps and reply with
the answers:

1. Fetch and read EXECUTION_BOARD.md:
   https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/EXECUTION_BOARD.md

2. Fetch and read the top "Architecture pivot" section of PROMPT_PREFIX.md:
   https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/PROMPT_PREFIX.md

3. Check the latest CI run status for branch claude/jolly-lamport-5cJJ4
   via whatever GitHub access you have (gh CLI, MCP, raw API). Confirm
   the latest commit's build is green (or note if it's red — that means
   we have a regression to fix BEFORE new work).

Then reply with exactly this format:

   READY.
   Latest commit: <SHA> (<status>)
   Top open milestone matching my tier: <MX.Y> (<difficulty>)
   Claiming: <MX.Y> — <one-line plan>

Wait for operator confirmation before claiming. After confirmation:
- Update EXECUTION_BOARD.md (your row in the dashboard +
  the milestone's status/claimed_by lines), commit with message
  "chore(board): MX.Y claimed by <your-handle>"
- Then do the work per the spec
- Push to claude/jolly-lamport-5cJJ4
- Wait for CI green
- Update EXECUTION_BOARD.md to DONE, commit + push
- Reply with summary + recommended next milestone for the next bat

**Target milestone for this dispatch (operator-set):** **MX.Y**
**Your model tier:** **Sonnet 4.x | Opus 4.8 | Hermes-3 | Qwen-Coder | Codex | other:____**

Constraints:
- Working branch claude/jolly-lamport-5cJJ4 only
- No --no-verify, no force-push, no destructive git ops without asking
- Burn budget: under 10 tool calls per at-bat (hard stop 15)
- If you hit a failure loop, set milestone status to FAILED with
  one-sentence diagnosis and stop. Operator will escalate to next tier.

Go.
```

---

## How to use

1. **Start a new session** with whichever model.
2. Drop `UNIVERSAL_PREFIX.md` as the system prompt (or the first chunk
   of context). Most surfaces let you paste it once.
3. Paste the block above as the **first user message**, after editing:
   - **MX.Y** → the milestone you want them to take (consult `EXECUTION_BOARD.md`)
   - **model tier line** → which model you're actually using
4. Wait for the `READY.` reply. Verify their proposed milestone is the
   one you intended.
5. Reply `proceed` (or correct them if they grabbed the wrong one).
6. They run. When they push their final commit + DONE update, you can
   review the PR/branch state.

## Per-surface notes

| Surface | Where prefix goes | Notes |
|---|---|---|
| Claude Code (CLI / IDE / web) | `--system-prompt` flag or first message before code | Use SETUP_PROMPT.md instead — has Claude-Code-specific bootstrap |
| Anthropic managed-agent (`ant`) | System prompt field on `ant beta:agents update` | Use MANAGED_AGENT_KICKOFF.md instead — has librarian role override |
| Codex / Codex CLI | System message | Works as-is |
| Cursor | `.cursorrules` or chat system message | Works as-is |
| ChatGPT custom GPT | Instructions field | Works as-is |
| Hermes / DeepSeek / Qwen / local models | System prompt | Works as-is |
| GitHub Copilot Chat | Workspace `copilot-instructions.md` | Drop UNIVERSAL_PREFIX.md content in there |

## Direct download URLs (raw GitHub)

Save these to a folder on your machine for offline reference:

```
UNIVERSAL_PREFIX.md            https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/UNIVERSAL_PREFIX.md
UNIVERSAL_LAUNCH.md            https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/UNIVERSAL_LAUNCH.md
EXECUTION_BOARD.md             https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/EXECUTION_BOARD.md
PROMPT_PREFIX.md               https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/PROMPT_PREFIX.md
CLAUDE_AT_HORIZONS.md          https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/CLAUDE_AT_HORIZONS.md
HANDOFF.md                     https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/HANDOFF.md
SETUP_PROMPT.md                https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/SETUP_PROMPT.md
MANAGED_AGENT_KICKOFF.md       https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/MANAGED_AGENT_KICKOFF.md
docs/LIGHTHOUSE.md             https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/docs/LIGHTHOUSE.md
agents/neuralmash-builder.system.md   https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/agents/neuralmash-builder.system.md
agents/sub-agent.system.md     https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/agents/sub-agent.system.md
sub-agent.agent.yaml           https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4/sub-agent.agent.yaml
```

Bash one-liner to grab them all into `./horizons-handoff/`:

```bash
mkdir -p horizons-handoff && cd horizons-handoff
BASE="https://raw.githubusercontent.com/M0DU14R-SYSx-inc/NeuroOmni.Vag-Agenti/claude/jolly-lamport-5cJJ4"
for f in UNIVERSAL_PREFIX.md UNIVERSAL_LAUNCH.md EXECUTION_BOARD.md \
         PROMPT_PREFIX.md CLAUDE_AT_HORIZONS.md HANDOFF.md \
         SETUP_PROMPT.md MANAGED_AGENT_KICKOFF.md sub-agent.agent.yaml; do
  curl -sLO "$BASE/$f"
done
mkdir -p docs agents
curl -sLo docs/LIGHTHOUSE.md "$BASE/docs/LIGHTHOUSE.md"
curl -sLo agents/neuralmash-builder.system.md "$BASE/agents/neuralmash-builder.system.md"
curl -sLo agents/sub-agent.system.md "$BASE/agents/sub-agent.system.md"
ls -la
```

Run that in any terminal (PC, Mac, Cloud Shell, Termux) and you have
the whole handoff kit locally.
