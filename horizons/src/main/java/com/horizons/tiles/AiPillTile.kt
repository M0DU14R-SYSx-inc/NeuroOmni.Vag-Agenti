package com.horizons.tiles

// Tile #2: AI-mode pill. Single tile, two tap zones.
// Left half = Meta-prompt (magenta accent on tap), right half = Bash command (teal accent on tap).
// STT -> VLM.buildMetaPrompt(target = ChatBox or BashCommand) -> inject via Accessibility.
object AiPillTile {
    const val ID = "tile_ai_pill"
    enum class Half { Meta, Bash }
}
