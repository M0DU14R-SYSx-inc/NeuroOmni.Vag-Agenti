# Horizons (N0.V4)

On-device AI assistant for the Motorola Razr Ultra 2025 (Snapdragon 8 Elite,
Hexagon NPU v79, Adreno GPU). Currently in the **greenfield rebuild** —
see `GREENFIELD_PLAN.md`.

## Where to look (session pickup, in this order)

1. **`SOTU.md`** — State of the Union. One screen. What just happened,
   what's next, what's stuck. Always read this first.
2. **`PROMPT_PREFIX.md`** — streamlined pointers + rules. No inline content.
3. **`EXECUTION_BOARD.md`** — live milestone list with claims dashboard.

## Reference (load only when the task needs it)

- `CLAUDE_AT_HORIZONS.md` — stable architecture wiki (the 9 boundaries,
  Truman Show, three control surfaces, state management).
- `GREENFIELD_PLAN.md` — rebuild scope, salvage list, scrap list.
- `wiki/` — discovery index + maintenance docs + failure / fork logs.
- `rules/` — hard rules (cache, git hygiene, at-bat protocol).
- `skills/project-memory/SKILL.md` — bundles project memory as one cache block.
- `docs/`, `agents/`, `legacy/` — deep-dive references + managed-agent
  prompts + archived previous tree.

## Architecture in one line

**9-boundary stack, Truman Show:** Nexa Studio → Nexa SDK → Nexa Server →
Nexa ML → Qualcomm QNN → Android Accessibility Service → Gemma-4-E4B-IT
(GPU) → OmniNeural-4B + Parakeet TDT/ASR (NPU) → VoxSherpa (system TTS).
Models perform; they never know cloud / router / backends exist.

## Build target

Razr Ultra 2025 only. arm64-v8a. No Tailscale, Ollama, Vulkan, Python
sidecar, multi-device.
