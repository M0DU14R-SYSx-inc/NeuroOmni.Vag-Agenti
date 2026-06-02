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
// Dark slate gray per operator reference (chat surface tone, NOT near-black).
val HorizonsBackground = Color(0xFF222C34)
val HorizonsSurface = Color(0xFF35414A)      // elevated cards / nav bar / top bar
val HorizonsSurfaceHigh = Color(0xFF42505A)  // for buttons / chips / dialogs
val HorizonsOnSurface = Color(0xFFF1F8FA)
val HorizonsHighlight = TealAccentBright

// Tile state colors (idle = backplate, active = teal; mic uses green on record, read-back uses teal on speak).
val TileIdle = IconBackplate
val TileActiveGreen = Color(0xFF3DD17A)
val TileTerminalTeal = TealAccent
val TileMetaPromptAccent = TealAccentBright
val TileLightbulbActive = ActionYellow
