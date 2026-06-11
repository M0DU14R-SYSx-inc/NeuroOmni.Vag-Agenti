package com.horizons.watchdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class WatchdogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF2DD4D9),
                background = Color(0xFF050709),
                surface = Color(0xFF0A0F12)
            )) { Dashboard() }
        }
    }
}

@Composable
private fun Dashboard() {
    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Text("Watchdog — telemetry + fallback ladder + crash capture", Modifier.padding(inner))
        // Phase 9: real diagnostics surface (NPU/GPU/CPU temp, t/s, memory, failure-type counters from Horizons),
        // crash report viewer, and manual hot-swap controls.
    }
}
