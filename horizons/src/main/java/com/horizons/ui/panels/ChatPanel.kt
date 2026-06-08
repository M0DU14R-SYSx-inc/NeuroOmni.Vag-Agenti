package com.horizons.ui.panels

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
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

    // AUTO = orchestrator default routing (NPU → failover → openrouter).
    // Any other value is a NamedBackend.id passed as orchestrator.stream(forcedToolId).
    var forcedBackendId by remember { mutableStateOf<String?>(null) }
    var backendMenuOpen by remember { mutableStateOf(false) }
    val backends = remember { app.providerLibrary.load() }
    val activeLabel = when (val id = forcedBackendId) {
        null -> "auto (${app.engine().backendTag})"
        else -> backends.firstOrNull { it.id == id }?.displayName ?: id
    }

    // Thinking toggle for the NPU VLM. Only meaningful when the active backend
    // is the NexaVlmEngine — cloud backends ignore it. Updated state pushed
    // straight into the engine so the next chat template call sees it.
    var thinking by remember { mutableStateOf(false) }

    fun doSend(prompt: String) {
        if (prompt.isBlank()) return
        val stagedImage = app.pendingImagePath.value
        turns += ChatTurn("you", prompt)
        val replyIdx = turns.size
        turns += ChatTurn("...", "")
        busy = true
        app.setPendingImagePath(null)
        scope.launch {
            var acc = ""
            app.orchestrator.stream(prompt, imagePath = stagedImage, forcedToolId = forcedBackendId)
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

    fun triggerMicToggle() {
        scope.launch {
            val r = app.micController.toggle()
            val text = r.getOrNull()
            if (!text.isNullOrBlank()) doSend(text)
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) triggerMicToggle() }

    // Awaits the FGS readySignal so we don't race the
    // SecurityException("FGS required") trap on API 34+.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = app.screenshotCapture.onConsentResult(result.resultCode, result.data)
        if (!granted) return@rememberLauncherForActivityResult
        scope.launch {
            val ready = ScreenCaptureService.start(ctx, result.resultCode, result.data!!)
            runCatching { ready.await() }
            app.screenshotCapture.captureToFile()
                .onSuccess { f -> app.setPendingImagePath(f.absolutePath) }
                .onFailure { /* indicator stays absent */ }
        }
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Chat — ", modifier = Modifier.padding(end = 4.dp))
            Box {
                OutlinedButton(onClick = { backendMenuOpen = true }) {
                    Text(activeLabel)
                    Icon(Icons.Filled.ArrowDropDown, "pick backend")
                }
                DropdownMenu(expanded = backendMenuOpen, onDismissRequest = { backendMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("auto (${app.engine().backendTag} / failover)") },
                        onClick = { forcedBackendId = null; backendMenuOpen = false }
                    )
                    backends.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.displayName) },
                            onClick = { forcedBackendId = b.id; backendMenuOpen = false }
                        )
                    }
                    if (backends.isEmpty()) {
                        DropdownMenuItem(
                            enabled = false,
                            text = { Text("(no named backends — add via Router)") },
                            onClick = {}
                        )
                    }
                }
            }
        }
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
                    val have = ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (have) triggerMicToggle()
                    else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                onClick = { consentLauncher.launch(app.screenshotCapture.prepareConsentIntent()) }
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
            IconButton(onClick = {
                thinking = !thinking
                (app.engine() as? com.horizons.model.NexaVlmEngine)?.setThinking(thinking)
            }) {
                Icon(
                    if (thinking) Icons.Filled.Psychology else Icons.Outlined.Psychology,
                    if (thinking) "Thinking on" else "Thinking off"
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
                onClick = { val p = input.trim(); input = ""; doSend(p) }
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
