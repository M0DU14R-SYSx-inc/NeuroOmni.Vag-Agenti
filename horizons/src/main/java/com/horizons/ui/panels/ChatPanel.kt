package com.horizons.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatPanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Chat — brainstorm + plan + execute with on-device VLM")
        // Phase 1: wire NexaVlmEngine.generateStream
        // Phase 2: mic button (Moonshine push-to-talk -> input field)
        // Phase 4: auto Kokoro read-back of VLM replies (live mode by default)
    }
}
