package com.horizons.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Pure-Compose radial gradient. Slate gray + teal bloom upper-left.
 * Replaces the XML <shape> drawable to avoid painterResource inflation issues
 * on some Android versions.
 */
@Composable
fun HorizonsBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A4E58),
                        Color(0xFF2D3A43),
                        Color(0xFF222C34)
                    ),
                    center = Offset(380f, 480f),
                    radius = 1400f
                )
            )
    )
}
