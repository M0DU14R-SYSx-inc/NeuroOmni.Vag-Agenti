package com.neuroomni.horizons.ui.theme

import androidx.compose.ui.graphics.Color

// Dark palette carried forward from the v0.2.0 PWA THEME object (Horizons UI Spec §9).
// Lightened one step from the original GitHub-dark values for a softer, less
// near-black surface while staying dark-first.
val HorizonsBackground = Color(0xFF1C2128)
val HorizonsSurface = Color(0xFF22272E)
val HorizonsSurfaceVariant = Color(0xFF2D333B)
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
// bright marble streak in the backdrop. Lightened to a softer slate (and slightly
// more translucent) so the panels no longer read as near-black.
val PanelScrim = Color(0xD91C232B)
