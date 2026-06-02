package com.horizons.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.horizons.HorizonsApplication
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

data class ChatTurn(val role: String, val text: String)

@Composable
fun ChatPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as HorizonsApplication
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<ChatTurn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Chat — on-device VLM (${app.engine().backendTag})")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(turns) { t -> Text("${t.role}: ${t.text}") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                placeholder = { Text("Ask Horizons...") }
            )
            Button(
                enabled = input.isNotBlank() && !busy,
                onClick = {
                    val prompt = input.trim()
                    input = ""
                    turns += ChatTurn("you", prompt)
                    val replyIdx = turns.size
                    turns += ChatTurn("vlm", "")
                    busy = true
                    scope.launch {
                        var acc = ""
                        app.engine().generateStream(prompt)
                            .catch { e ->
                                turns[replyIdx] = ChatTurn("vlm", "[error] ${e.message}")
                            }
                            .onCompletion { busy = false }
                            .collect { token ->
                                acc += token
                                turns[replyIdx] = ChatTurn("vlm", acc)
                            }
                    }
                }
            ) { Text(if (busy) "..." else "Send") }
        }
    }
}
