package com.horizons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.horizons.ui.panels.ChatPanel
import com.horizons.ui.panels.DiagnosticsPanel
import com.horizons.ui.panels.RouterPanel
import com.horizons.ui.panels.TerminalPanel
import com.horizons.ui.theme.HorizonsBackdrop
import com.horizons.ui.theme.HorizonsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HorizonsTheme { Dashboard() } }
    }
}

enum class Panel(val label: String) { Chat("Chat"), Router("Router"), Terminal("Terminal"), Diagnostics("Diag"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard() {
    var panel by remember { mutableStateOf(Panel.Chat) }
    Box(Modifier.fillMaxSize()) {
        HorizonsBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Horizons — ${panel.label}") },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xE635414A)
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xE635414A)) {
                    NavigationBarItem(
                        selected = panel == Panel.Chat,
                        onClick = { panel = Panel.Chat },
                        icon = { Icon(Icons.Filled.Chat, "Chat") },
                        label = { Text("Chat") }
                    )
                    NavigationBarItem(
                        selected = panel == Panel.Router,
                        onClick = { panel = Panel.Router },
                        icon = { Icon(Icons.Filled.Tune, "Router") },
                        label = { Text("Router") }
                    )
                    NavigationBarItem(
                        selected = panel == Panel.Terminal,
                        onClick = { panel = Panel.Terminal },
                        icon = { Icon(Icons.Filled.Terminal, "Terminal") },
                        label = { Text("Terminal") }
                    )
                    NavigationBarItem(
                        selected = panel == Panel.Diagnostics,
                        onClick = { panel = Panel.Diagnostics },
                        icon = { Icon(Icons.Filled.MonitorHeart, "Diag") },
                        label = { Text("Diag") }
                    )
                    NavigationBarItem(
                        selected = panel == Panel.Settings,
                        onClick = { panel = Panel.Settings },
                        icon = { Icon(Icons.Filled.Settings, "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (panel) {
                    Panel.Chat -> ChatPanel(Modifier.fillMaxSize())
                    Panel.Router -> RouterPanel(Modifier.fillMaxSize())
                    Panel.Terminal -> TerminalPanel(Modifier.fillMaxSize())
                    Panel.Diagnostics -> DiagnosticsPanel(Modifier.fillMaxSize())
                    Panel.Settings -> Text("Settings — Phase 2/7 stubbed in", Modifier.padding(16.dp))
                }
            }
        }
    }
}
