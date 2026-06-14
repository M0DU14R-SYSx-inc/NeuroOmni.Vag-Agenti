package com.horizons.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val Scheme = darkColorScheme(
    primary = HorizonsPrimary,
    onPrimary = HorizonsOnPrimary,
    primaryContainer = HorizonsSurfaceHigh,
    onPrimaryContainer = HorizonsOnSurface,
    secondary = HorizonsHighlight,
    onSecondary = HorizonsOnPrimary,
    background = HorizonsBackground,
    onBackground = HorizonsOnSurface,
    surface = HorizonsSurface,
    onSurface = HorizonsOnSurface,
    surfaceVariant = HorizonsSurfaceHigh,
    onSurfaceVariant = HorizonsOnSurface,
    surfaceContainer = HorizonsSurface,
    surfaceContainerHigh = HorizonsSurfaceHigh,
    surfaceContainerHighest = HorizonsSurfaceHigh,
    surfaceContainerLow = HorizonsBackground,
    surfaceContainerLowest = HorizonsBackground,
    outline = HorizonsHighlight,
    outlineVariant = HorizonsSurfaceHigh
)

@Composable
fun HorizonsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        CompositionLocalProvider(LocalContentColor provides HorizonsOnSurface, content = content)
    }
}
