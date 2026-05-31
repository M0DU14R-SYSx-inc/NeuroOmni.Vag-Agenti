package com.neuroomni.horizons.ui.theme

import androidx.compose.ui.graphics.Color

// Dark palette carried forward from the v0.2.0 PWA THEME object (Horizons UI Spec §9).
val HorizonsBackground = Color(0xFF0D1117)
val HorizonsSurface = Color(0xFF161B22)
val HorizonsSurfaceVariant = Color(0xFF21262D)
val HorizonsOnBackground = Color(0xFFE6EDF3)
val HorizonsOnSurfaceMuted = Color(0xFF8B949E)
val HorizonsOutline = Color(0xFF30363D)

// Accent + instance-profile colors (Spec §3).
val AccentBlue = Color(0xFF58A6FF)   // Personal
val AccentRed = Color(0xFFF85149)    // Red Agent
val AccentYellow = Color(0xFFD29922) // Collab
val AccentGreen = Color(0xFF3FB950)

// Teal liquid-marble app background, evoking the wallpaper. Used as a diagonal
// gradient behind the panels. Swap in a real photo drawable here later if desired.
val TealDeep = Color(0xFF0B3A40)
val TealMid = Color(0xFF0D1B26)
val TealAbyss = Color(0xFF060A0D)

// Dark slate scrim that panel content sits on, so text stays readable over the
// bright marble streak in the backdrop. Solid and a touch darker than the
// background (#0D1117) so the panel reads as a distinct surface against the
// teal backdrop without disappearing into near-black.
val PanelScrim = Color(0xFF090C11)
