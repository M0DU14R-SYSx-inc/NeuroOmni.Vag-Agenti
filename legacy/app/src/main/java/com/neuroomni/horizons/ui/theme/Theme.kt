package com.neuroomni.horizons.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.neuroomni.horizons.R

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

// Roboto Mono as the single app-wide typeface (bundled in res/font, variable weight).
private val RobotoMono = FontFamily(Font(R.font.roboto_mono))

// Apply Roboto Mono to every Material3 text style while keeping the default sizes/weights.
private val HorizonsTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = RobotoMono),
        displayMedium = displayMedium.copy(fontFamily = RobotoMono),
        displaySmall = displaySmall.copy(fontFamily = RobotoMono),
        headlineLarge = headlineLarge.copy(fontFamily = RobotoMono),
        headlineMedium = headlineMedium.copy(fontFamily = RobotoMono),
        headlineSmall = headlineSmall.copy(fontFamily = RobotoMono),
        titleLarge = titleLarge.copy(fontFamily = RobotoMono),
        titleMedium = titleMedium.copy(fontFamily = RobotoMono),
        titleSmall = titleSmall.copy(fontFamily = RobotoMono),
        bodyLarge = bodyLarge.copy(fontFamily = RobotoMono),
        bodyMedium = bodyMedium.copy(fontFamily = RobotoMono),
        bodySmall = bodySmall.copy(fontFamily = RobotoMono),
        labelLarge = labelLarge.copy(fontFamily = RobotoMono),
        labelMedium = labelMedium.copy(fontFamily = RobotoMono),
        labelSmall = labelSmall.copy(fontFamily = RobotoMono),
    )
}

@Composable
fun HorizonsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HorizonsDarkColors,
        typography = HorizonsTypography,
        content = content,
    )
}
