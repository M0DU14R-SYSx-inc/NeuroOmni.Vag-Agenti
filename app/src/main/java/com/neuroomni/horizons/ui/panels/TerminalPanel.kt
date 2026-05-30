package com.neuroomni.horizons.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neuroomni.horizons.ui.components.PanelPlaceholder

@Composable
fun TerminalPanel(modifier: Modifier = Modifier) {
    PanelPlaceholder(
        title = "Terminal",
        lines = listOf(
            "Live Termux output when the shell-automation layer is active",
            "Command history",
            "Manual command input (type directly into Termux)",
            "Status indicators for Claude Code CLI and Gemini CLI sessions",
        ),
        modifier = modifier,
    )
}
