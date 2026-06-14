# State of the Union — Horizons

> **Pickup file #1.** Read this first every session. One screen. Standalone —
> a fresh agent can run with this file alone.

**Snapshot:** 2026-06-14 18:00 UTC · branch `claude/jolly-lamport-5cJJ4`

> ⚠️ **HARD CUT COMPLETED.** The legacy code is **archived**, not coexisting.
> Everything that ever referenced sherpa-onnx, Moonshine, Kokoro,
> `EdgeModelFactory`, `Orchestrator`, `ProviderLibrary`, the old `ChatPanel`
> screenshot button, `KeyRow` `remember{}` phantom-save, or the `Watchdog`
> loopback WS lives under `.archive/legacy-src/` and **must not be referenced**.
> If you find yourself reading `.archive/`, stop — you're looking at dead code.
>
> **LIVE STACK:** Parakeet (NPU) + VoxSherpa (system TTS) for voice;
> OmniNeural-4B (NPU) + Gemma-4-E4B-IT (GPU) for text/vision. Cloud lives
> in a separate app behind a capability adapter.

---

## What exists on this branch RIGHT NOW

Buildable APK (`gradle :horizons:assembleDebug` clean as of this snapshot):

- `com.horizons.HorizonsApplication` — initializes `NexaModelLoader` on
  start, exposes `engine: NexaEngine?` and `loadEngine(spec)` for the UI.
- `com.horizons.MainActivity` — placeholder Compose screen showing
  `engineStatus`. **Intentional minimal UI** until the design artifact lands.
- `com.horizons.core.nexa.*` — `NexaEngine`, `NexaModelSpec`,
  `NexaModelLoader`, `LiveNexaVlmEngine` (VlmWrapper-backed),
  `LiveNexaAsrEngine` (AsrWrapper-backed). Real, decompile-verified.
- `com.horizons.core.state.AppStateStore` — single
  `StateFlow<Map<String,String>>` over EncryptedSharedPreferences.
- `com.horizons.core.voice.SystemTtsClient` — VoxSherpa bridge.
- `com.horizons.core.screen.ScreenshotCapture` — MediaProjection + 1024px
  downscale + JPEG q=85 (no longer wired to UI; ready for new pipeline).
- `com.horizons.core.log.{CrashRecorder, InteractionLogger}`.
- `com.horizons.core.shell.TaskerBridge` — Termux `RUN_COMMAND`.

Manifest is stripped to one launcher activity. All FGS/accessibility/IME
services were archived because their backing code was archived; they
re-land per-feature as new wiring arrives.

## What does NOT exist yet (be honest with the user)

The app installs and shows engine status. It does **not** yet have:

- Chat tile that sends text to the loaded engine.
- Screen-Q&A pipeline (MediaProjection foreground service consent flow +
  Accessibility tree extraction + `engine.infer(image attachment)`).
- Voice loop (mic capture → Parakeet → engine → VoxSherpa).
- Model downloader / picker UI for OmniNeural / Gemma / Parakeet weights.
- Per-tile terminal.
- Cloud-frontend adapter.

These are the next at-bats. See `EXECUTION_BOARD.md`.

## What you do as the next agent

1. Read `PROMPT_PREFIX.md` for rules. Read `EXECUTION_BOARD.md` for
   claims. Pick **one** milestone, claim it, do it, commit, push, PR,
   merge. Repeat fresh-session per at-bat.
2. **Do not** read `.archive/` for guidance. Do not paraphrase from it.
   Do not re-introduce anything in the DEAD STACK warning above.
3. Decompile the Nexa AAR first if you're writing SDK code
   (`rules/AAR_DECOMPILE.md`). Bytecode is ground truth.
4. At session close: bump this file. Bump only the dated line + the
   "What does NOT exist yet" + "Recommended next at-bat" blocks. Don't
   add sections.

## Recommended next at-bat

**G9 — Chat tile (text-only)** is the smallest functional slice that
gets the operator something to click. Path:
`MainActivity` adds a chat Compose surface → text field +
"Load OmniNeural" button → `app.loadEngine(omnineural-spec)` →
type prompt → `engine.stream(NexaInput(text))` → collect into a Text
composable. Real model on NPU. ~45 min.

After G9: G10 (screen Q&A — MediaProjection foreground service + image
attachment to NexaInput), G11 (voice loop — Parakeet downloader +
mic capture → infer → VoxSherpa).

## Branch + merge

- Working branch: `claude/jolly-lamport-5cJJ4`.
- Main is up to date through PR #37.
- Never push `main` directly. Never delete a feature branch
  (archive as `archive/<name>`).
- Full rules: `rules/`.

---

**Standalone handoff check:** if you opened this file in a fresh session
with no other context, you should know (a) the live stack is the 9
boundaries, (b) the legacy code is archived and forbidden, (c) the next
at-bat is G9 chat tile, (d) where to look for rules. That's the whole
contract.
