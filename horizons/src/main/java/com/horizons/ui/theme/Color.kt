package com.horizons.ui.theme

import androidx.compose.ui.graphics.Color

// Unified palette per operator spec: near-black icon backplate + teal accent.
// Liquid-marble teal/black backdrop is the dashboard background image.

val IconBackplate = Color(0xFF050709)      // launcher/tile backplate only
val TealAccent = Color(0xFF2DD4D9)
val TealAccentBright = Color(0xFF4FE7EC)
val ActionYellow = Color(0xFFF5C518)

val HorizonsPrimary = TealAccent
val HorizonsOnPrimary = Color(0xFF001517)
val HorizonsBackground = Color(0xFF0E1518)  // lifted off pure black so panels read
val HorizonsSurface = Color(0xFF18242A)
val HorizonsOnSurface = Color(0xFFE6F6F8)
val HorizonsHighlight = TealAccentBright

// Tile state colors (idle = backplate, active = teal; mic uses green on record, read-back uses teal on speak).
val TileIdle = IconBackplate
val TileActiveGreen = Color(0xFF3DD17A)
val TileTerminalTeal = TealAccent
val TileMetaPromptAccent = TealAccentBright
val TileLightbulbActive = ActionYellow
