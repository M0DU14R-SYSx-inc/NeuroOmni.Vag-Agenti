package com.horizons.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = HorizonsPrimary,
    onPrimary = HorizonsOnPrimary,
    background = HorizonsBackground,
    surface = HorizonsSurface,
    onSurface = HorizonsOnSurface,
    secondary = HorizonsHighlight
)

@Composable
fun HorizonsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
