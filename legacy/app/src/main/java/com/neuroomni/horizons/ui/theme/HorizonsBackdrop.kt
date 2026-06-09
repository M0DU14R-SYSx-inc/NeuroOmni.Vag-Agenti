package com.neuroomni.horizons.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * Coded approximation of the teal liquid-marble wallpaper: a teal field with a
 * bright white marble streak sweeping diagonally, dark troughs, and a scatter of
 * faint bubbles. Drawn behind the panels (Session 5 visual pass).
 *
 * Not a photo — when a licensed image file is available, draw it here instead.
 */
@Composable
fun HorizonsBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawTealField()
        drawMarbleStreak()
        drawTroughs()
        drawBubbles()
    }
}

private fun DrawScope.drawTealField() {
    // Base wash: teal toward the top, deepening to near-black at the bottom.
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color(0xFF11525A),
                0.35f to Color(0xFF0C3B43),
                0.7f to Color(0xFF0C1E26),
                1.0f to Color(0xFF05090C),
            ),
            start = Offset(size.width * 0.15f, 0f),
            end = Offset(size.width * 0.85f, size.height),
        ),
    )
}

private fun DrawScope.drawMarbleStreak() {
    // Bright marble sweep running lower-left -> upper-right, like the photo's highlight.
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.30f to Color(0x00B8C4C2),
                0.48f to Color(0x66D3DCDA),
                0.58f to Color(0x99E4EAE8),
                0.68f to Color(0x55C2CCCA),
                0.82f to Color(0x00A0ACAA),
            ),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
        ),
    )
    // A second, narrower highlight ridge offset to the right for depth.
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.55f to Color(0x00000000),
                0.70f to Color(0x44E8EEEC),
                0.80f to Color(0x00000000),
            ),
            start = Offset(size.width * 0.2f, size.height),
            end = Offset(size.width * 1.1f, size.height * 0.1f),
        ),
    )
}

private fun DrawScope.drawTroughs() {
    // Dark troughs flanking the bright streak, deepening the marble contrast.
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color(0x77081016),
                0.22f to Color(0x00000000),
                0.88f to Color(0x00000000),
                1.0f to Color(0xAA04070A),
            ),
            start = Offset(0f, size.height * 0.2f),
            end = Offset(size.width, size.height),
        ),
    )
}

private fun DrawScope.drawBubbles() {
    // Faint, deterministic scatter of bubbles (seeded so it doesn't shimmer on recompose).
    val rng = Random(seed = 42)
    repeat(48) {
        val cx = rng.nextFloat() * size.width
        val cy = rng.nextFloat() * size.height
        val r = 2f + rng.nextFloat() * 7f
        // soft body
        drawCircle(color = Color(0x14EAF2F0), radius = r, center = Offset(cx, cy))
        // bright rim highlight
        drawCircle(
            color = Color(0x33FFFFFF),
            radius = r,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f),
        )
        // tiny specular dot
        drawCircle(
            color = Color(0x55FFFFFF),
            radius = r * 0.28f,
            center = Offset(cx - r * 0.3f, cy - r * 0.3f),
        )
    }
}
