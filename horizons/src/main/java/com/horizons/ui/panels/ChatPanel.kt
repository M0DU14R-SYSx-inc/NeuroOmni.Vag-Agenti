package com.horizons.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.horizons.audio.MicCaptureController
import com.horizons.screen.ScreenCaptureService
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
    var autoSpeak by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val micState by app.micController.state.collectAsState()
    val pendingImagePath by app.pendingImagePath.collectAsState()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = app.screenshotCapture.onConsentResult(result.resultCode, result.data)
        if (!granted) return@rememberLauncherForActivityResult
        // FGS must be up BEFORE getMediaProjection (API 34+).
        ScreenCaptureService.start(ctx, result.resultCode, result.data!!)
        scope.launch {
            // Small delay so FGS reaches foreground state before projection acquisition.
            kotlinx.coroutines.delay(400)
            app.screenshotCapture.captureToFile()
                .onSuccess { f -> app.setPendingImagePath(f.absolutePath) }
                .onFailure { /* swallow — indicator stays absent */ }
        }
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    fun send(prompt: String) {
        if (prompt.isBlank()) return
        val stagedImage = app.pendingImagePath.value
        turns += ChatTurn("you", prompt)
        val replyIdx = turns.size
        turns += ChatTurn("...", "")
        busy = true
        // Consume the staged screenshot once — the next send is a clean text turn.
        app.setPendingImagePath(null)
        scope.launch {
            var acc = ""
            app.orchestrator.stream(prompt, imagePath = stagedImage)
                .catch { e ->
                    turns[replyIdx] = ChatTurn("error", "${e.javaClass.simpleName}: ${e.message}")
                }
                .onCompletion { cause ->
                    busy = false
                    if (cause == null && autoSpeak && acc.isNotBlank()) {
                        scope.launch { runCatching { app.speaker.speak(acc) } }
                    }
                }
                .collect { token ->
                    acc += token
                    turns[replyIdx] = ChatTurn("reply", acc)
                }
        }
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Chat — ${app.engine().backendTag} (or cloud fallback)")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(turns) { t -> Text("${t.role}: ${t.text}") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = {
                    scope.launch {
                        val r = app.micController.toggle()
                        val text = r.getOrNull()
                        if (!text.isNullOrBlank()) send(text)
                    }
                },
                enabled = !busy && micState !is MicCaptureController.State.Transcribing
            ) {
                val recording = micState is MicCaptureController.State.Recording
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    if (recording) "Stop" else "Mic"
                )
            }
            IconButton(
                enabled = !busy,
                onClick = {
                    // If consent already persisted this process, captureToFile() will succeed
                    // without re-prompting. Easiest path: always launch the consent intent;
                    // Android's MediaProjection requires fresh consent per capture session
                    // on API 34+, so caching the grant client-side is moot.
                    consentLauncher.launch(app.screenshotCapture.prepareConsentIntent())
                }
            ) {
                Icon(Icons.Filled.CameraAlt, "Screenshot")
            }
            IconButton(onClick = {
                autoSpeak = !autoSpeak
                if (!autoSpeak) app.speaker.stop()
            }) {
                Icon(
                    if (autoSpeak) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    if (autoSpeak) "Auto-speak on" else "Auto-speak off"
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                placeholder = { Text("Ask Horizons...") }
            )
            Button(
                enabled = input.isNotBlank() && !busy,
                onClick = { val p = input.trim(); input = ""; send(p) }
            ) { Text(if (busy) "..." else "Send") }
        }
        if (pendingImagePath != null) {
            Text("screenshot pending — will be attached to next send")
        }
        if (micState is MicCaptureController.State.Error) {
            Text("mic: ${(micState as MicCaptureController.State.Error).msg}")
        } else if (micState is MicCaptureController.State.Transcribing) {
            Text("transcribing…")
        }
    }
}
