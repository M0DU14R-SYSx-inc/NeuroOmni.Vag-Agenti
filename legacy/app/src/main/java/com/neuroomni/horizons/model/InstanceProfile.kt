package com.neuroomni.horizons.model

import androidx.compose.ui.graphics.Color
import com.neuroomni.horizons.ui.theme.AccentBlue
import com.neuroomni.horizons.ui.theme.AccentRed
import com.neuroomni.horizons.ui.theme.AccentYellow

/**
 * Instance profiles gate which providers / tools / filesystem access are available.
 * See Horizons UI Spec §3 and Architecture doc.
 */
enum class InstanceProfile(val displayName: String, val color: Color) {
    Personal("Personal", AccentBlue),   // full access
    RedAgent("Red Agent", AccentRed),   // sandboxed, edge-only, no keys/memory/fs-write
    Collab("Collab", AccentYellow),     // read-only, shared state
}
