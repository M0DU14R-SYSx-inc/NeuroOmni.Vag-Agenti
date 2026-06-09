package com.horizons.tiles

// Tile #1: Mic (plain Dictation).
// Idle = slate, Recording = green. Moonshine STT -> focused field via current IME or AccessibilityService.
// Lives inside FloatingOverlayService; this file is the per-tile view + behavior contract.
object MicTile {
    const val ID = "tile_mic_dictation"
}
