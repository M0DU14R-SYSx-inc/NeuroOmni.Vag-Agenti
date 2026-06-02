package com.horizons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.horizons.ui.panels.ChatPanel
import com.horizons.ui.panels.ModePicker
import com.horizons.ui.panels.RouterPanel
import com.horizons.ui.panels.TerminalPanel
import com.horizons.ui.theme.HorizonsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HorizonsTheme {
                Dashboard()
            }
        }
    }
}

enum class Panel { Chat, Router, Terminal, Mode }

@Composable
private fun Dashboard() {
    var panel by remember { mutableStateOf(Panel.Chat) }
    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        when (panel) {
            Panel.Chat -> ChatPanel(Modifier.padding(inner))
            Panel.Router -> RouterPanel(Modifier.padding(inner))
            Panel.Terminal -> TerminalPanel(Modifier.padding(inner))
            Panel.Mode -> ModePicker(Modifier.padding(inner)) { panel = it }
        }
    }
}
