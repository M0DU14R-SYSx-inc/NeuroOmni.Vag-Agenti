package com.horizons.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = HorizonsPrimary,
    onPrimary = HorizonsOnPrimary,
    secondary = HorizonsHighlight,
    background = HorizonsBackground,
    onBackground = HorizonsOnSurface,
    surface = HorizonsSurface,
    onSurface = HorizonsOnSurface,
    surfaceVariant = HorizonsSurfaceHigh,
    onSurfaceVariant = HorizonsOnSurface
)

@Composable
fun HorizonsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
