package com.horizons.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TerminalPanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Terminal — Termux interface")
        // Phase 7 (or sooner): persistent input bar + scrollable history.
        // Bridge to Termux RUN_COMMAND via TermuxBridge.
        // Mic in this panel defaults to Bash STT mode.
    }
}
