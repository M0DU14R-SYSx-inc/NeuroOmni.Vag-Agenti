package com.horizons.ui.theme

import androidx.compose.ui.graphics.Color

// Unified palette per operator spec: near-black icon backplate + teal accent.
// Liquid-marble teal/black backdrop is the dashboard background image.

val IconBackplate = Color(0xFF050709)      // near-black, same on every launcher/tile icon
val TealAccent = Color(0xFF2DD4D9)         // the Tasker/Weather teal
val TealAccentBright = Color(0xFF4FE7EC)   // hover/active highlight
val ActionYellow = Color(0xFFF5C518)       // lightning-flash when bulb/diagnostic activates

val HorizonsPrimary = TealAccent
val HorizonsOnPrimary = IconBackplate
val HorizonsBackground = Color(0xFF06090B)
val HorizonsSurface = Color(0xFF0A0F12)
val HorizonsOnSurface = Color(0xFFD8E8EB)
val HorizonsHighlight = TealAccentBright

// Tile state colors (idle = backplate, active = teal; mic uses green on record, read-back uses teal on speak).
val TileIdle = IconBackplate
val TileActiveGreen = Color(0xFF3DD17A)
val TileTerminalTeal = TealAccent
val TileMetaPromptAccent = TealAccentBright
val TileLightbulbActive = ActionYellow
