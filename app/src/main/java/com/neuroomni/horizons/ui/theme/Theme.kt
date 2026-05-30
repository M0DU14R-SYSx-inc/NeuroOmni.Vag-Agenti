package com.neuroomni.horizons.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Horizons is dark-first (Spec §2). We keep a single dark scheme regardless of the
// system setting; the device target runs this app as a dedicated surface.
private val HorizonsDarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = HorizonsBackground,
    secondary = AccentGreen,
    background = HorizonsBackground,
    onBackground = HorizonsOnBackground,
    surface = HorizonsSurface,
    onSurface = HorizonsOnBackground,
    surfaceVariant = HorizonsSurfaceVariant,
    onSurfaceVariant = HorizonsOnSurfaceMuted,
    outline = HorizonsOutline,
    error = AccentRed,
)

@Composable
fun HorizonsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HorizonsDarkColors,
        typography = Typography(),
        content = content,
    )
}
