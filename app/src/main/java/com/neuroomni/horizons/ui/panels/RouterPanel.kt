package com.neuroomni.horizons.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neuroomni.horizons.ui.components.PanelPlaceholder

@Composable
fun RouterPanel(modifier: Modifier = Modifier) {
    PanelPlaceholder(
        title = "Router",
        lines = listOf(
            "Provider toggle selector (Edge, Anthropic, Vertex Claude/Gemini, AI Studio, CLI)",
            "Active provider status: connected / latency / last call",
            "Billing indicator: which credit pool the current toggle burns",
            "Endpoint config: API keys, service-account paths, base URLs, model strings",
            "Instance profile selector (Personal / Red Agent / Collab)",
        ),
        modifier = modifier,
    )
}
