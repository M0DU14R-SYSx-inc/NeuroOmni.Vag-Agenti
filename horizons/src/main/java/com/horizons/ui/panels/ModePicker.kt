package com.horizons.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.horizons.Panel

@Composable
fun ModePicker(modifier: Modifier = Modifier, onPick: (Panel) -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Mode picker")
        Button(onClick = { onPick(Panel.Chat) }) { Text("Chat") }
        Button(onClick = { onPick(Panel.Router) }) { Text("Router") }
        Button(onClick = { onPick(Panel.Terminal) }) { Text("Terminal") }
    }
}
