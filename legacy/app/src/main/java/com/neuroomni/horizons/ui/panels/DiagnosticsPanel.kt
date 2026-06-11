package com.neuroomni.horizons.ui.panels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neuroomni.horizons.ui.components.PanelPlaceholder

@Composable
fun DiagnosticsPanel(modifier: Modifier = Modifier) {
    PanelPlaceholder(
        title = "Diagnostics",
        lines = listOf(
            "Rubik Pi 3: CPU/RAM/disk/uptime, Tailscale state, USB P2P link state",
            "Jetson Orin Nano: GPU temp, CUDA util, Postgres connections, NVMe usage",
            "Tailscale mesh health: all nodes, latency, last heartbeat",
            "OmniNeural apprentice: adapter version, last training cycle, accuracy trend",
            "Last audit log entry from Docker CLI #2",
            "Active model on each node",
            "Razr battery / thermal state",
        ),
        modifier = modifier,
    )
}
