# Next-Session Setup Prompt

Paste the block between the dashes below as the **first message** of
the next Claude Code (web) or Claude Code (CLI) session opened against
this repository. It bootstraps the agent with the wiki, the working
rules, and what's queued for the session — without burning context on
re-derivation.

If you also want to drive the deployed Anthropic managed-agent
(`agent_01RaU3nbhVGcFi9ZRcCinT9r`), the same block works after `ant
beta:sessions create` — just pass it as the first user message.

---

```
You are picking up Horizons mid-build. Before responding, read these three
files in order and confirm you've read them by quoting one decision from
each that you find load-bearing:

  1. HANDOFF.md
  2. CLAUDE_AT_HORIZONS.md
  3. PROMPT_PREFIX.md

(If any file is missing, stop and report it — do NOT proceed without all
three.)

Working branch: claude/jolly-lamport-5cJJ4. Never push main without my
explicit say-so. No --no-verify. Don't add features, abstractions, or
scaffolding the task didn't ask for.

The previous session ended with kitchen-wiring pass 1 shipped at commit
05b7706 — voice in (Moonshine), voice out (Kokoro), TaskerBridge,
DiagnosticsPanel, ChatPanel mic button + auto-speak. CI green at that
commit.

What's queued for THIS session (do them in this order — do NOT touch
later items until the earlier one passes one full "at-bat" cycle):

  A. Verify on-device. The user installs the latest APK and walks
     through: model staged → engine loads → chat → mic captures →
     Moonshine returns text → orchestrator streams a reply → Kokoro
     auto-speaks it. Report what they observe in Diag tab. Anything
     that doesn't work, fix it.
  B. Termux round-trip: prove `TermuxBridge` actually fires
     com.termux.RUN_COMMAND and reads back the result. Write a smoke
     test or add a "Send `ls`" button to TerminalPanel that surfaces
     the output and any error.
  C. Tasker outbound smoke-test: a button in DiagnosticsPanel that
     calls `app.tasker.runTask("HorizonsTest")` so the user can
     confirm with a known Tasker profile.
  D. Screenshot button → VLM. This needs Activity-scoped
     MediaProjection consent (see ScreenshotCapture.kt's
     prepareConsentIntent/onConsentResult contract). Wire the
     consent ActivityResultLauncher in MainActivity, expose a
     pendingImagePath: StateFlow<String?> on HorizonsApplication,
     then add a screenshot button to ChatPanel that captures and
     sets pendingImagePath; the next Send passes it as imagePath to
     orchestrator.stream(prompt, imagePath).

"At-bat" rules: each layer gets one agent attempt + one fresh-agent
re-attempt cold (no shared context). If they converge, ship. If they
diverge, the divergence is the bug — investigate. NO single agent
both builds AND grades its own work. Rotate roles.

Tooling available in this environment:
  - bash, git, kotlin source reading
  - Anthropic ant CLI at /root/go/bin/ant (env var ANTHROPIC_API_KEY
    set by user via Claude Code web env-vars panel)
  - github MCP via mcp__github__* tools (load via ToolSearch as needed)

Initial response format: after quoting one load-bearing decision from
each of the three docs above, state the layer you're picking up
(A/B/C/D) and the first concrete action you'll take. No prose,
no preamble.
```

---

## How to use this with the cloud agent (optional)

```bash
source ~/.config/ant_env
export PATH="$PATH:$(go env GOPATH)/bin"

SESSION=$(ant beta:sessions create \
  --agent agent_01RaU3nbhVGcFi9ZRcCinT9r \
  --environment-id env_01Srnj2osSiRfd1AGBaxDoVH \
  --title "Horizons — kitchen wiring pass 2" \
  --transform id --format raw)

# Paste the block above into the content text below.
ant beta:sessions:events send --session-id "$SESSION" --event "$(cat <<'YAML'
{
  type: user.message,
  content: [{type: text, text: "<paste block here>"}]
}
YAML
)"

# Stream the response
ant beta:sessions:events stream --session-id "$SESSION"
```

After the session is done:

```bash
ant beta:sessions archive --session-id "$SESSION"
```

Sessions bill at $0.08/hr active. Archive when idle.

## Cache verification on next deploy

If you re-deploy the agent system prompt mid-session for any reason,
expect a full cache miss + re-write. To check after a deploy:

```bash
# Send a tiny message
ant beta:sessions:events send --session-id "$SESSION" --event '{
  type: user.message,
  content: [{type: text, text: "ack"}]
}' --format raw

# Pull the span.model_request_end event and read model_usage
ant beta:sessions:events list --session-id "$SESSION" --format jsonl \
  | grep model_request_end | python3 -c "
import sys, json
for line in sys.stdin:
    e = json.loads(line)
    u = e.get('model_usage', {})
    print(f\"in={u.get('input_tokens')} out={u.get('output_tokens')} write={u.get('cache_creation_input_tokens')} read={u.get('cache_read_input_tokens')}\")
"
```

`cache_read_input_tokens > 0` means the cache hit. First turn after a
deploy will be a write (~3-4k tokens). Subsequent turns within TTL
should read.
