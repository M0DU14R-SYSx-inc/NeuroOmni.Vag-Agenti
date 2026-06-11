package com.horizons.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.horizons.HorizonsApplication
import com.horizons.model.EdgeModelDownloader
import com.horizons.model.EdgeModelFactory
import com.horizons.model.EdgeModelImporter
import com.horizons.model.KokoroDownloader
import com.horizons.model.MoonshineDownloader
import kotlinx.coroutines.launch

@Composable
fun RouterPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as HorizonsApplication
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    var progressFrac by remember { mutableStateOf<Float?>(null) }
    var status by remember {
        mutableStateOf(
            EdgeModelFactory.installedModelDir(ctx)?.let { "Model staged: ${it.absolutePath}" }
                ?: "No model staged."
        )
    }
    var checklist by remember { mutableStateOf(currentChecklist(ctx)) }

    fun refresh() {
        status = EdgeModelFactory.installedModelDir(ctx)?.let { "Model staged: ${it.absolutePath}" }
            ?: "No model staged."
        checklist = currentChecklist(ctx)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; line = "Scanning..."; progressFrac = null
        app.scope.launch {
            EdgeModelImporter.importFromTree(ctx, uri) { p ->
                line = "Copying [${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                progressFrac = p.fraction
            }.onSuccess { r ->
                refresh()
                val staged = checklist.count { it.second }
                line = when {
                    r.candidates == 0 -> "Picked folder appears empty."
                    staged == 0 -> "Found ${r.candidates} files; none matched the ${checklist.size} required."
                    staged == checklist.size -> "All ${checklist.size} staged. Loading engine..."
                    else -> "Staged $staged/${checklist.size}."
                }
                if (staged == checklist.size) { app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}" }
            }.onFailure { line = "Import failed: ${it.message}" }
            busy = false
        }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true; line = "Importing ${uris.size} file(s)..."; progressFrac = null
        app.scope.launch {
            EdgeModelImporter.importFiles(ctx, uris) { p ->
                line = "Copying [${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                progressFrac = p.fraction
            }.onSuccess { r ->
                refresh()
                val staged = checklist.count { it.second }
                line = if (staged == checklist.size) "All ${checklist.size} staged." else "Staged $staged/${checklist.size}."
                if (staged == checklist.size) { app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}" }
            }.onFailure { line = "Import failed: ${it.message}" }
            busy = false
        }
    }

    val engineStatus by app.engineStatus.collectAsState()
    val engineError by app.engineError.collectAsState()
    val sttStatus by app.sttStatus.collectAsState()
    val ttsStatus by app.ttsStatus.collectAsState()
    val cacheStatus by app.cacheStatus.collectAsState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ====== VLM (Nexa NPU) ======
        Text("VLM — on-device", style = MaterialTheme.typography.titleMedium)
        Text("Engine: ${app.engine().backendTag}  ($engineStatus)")
        engineError?.let { Text("ENGINE ERROR: $it") }
        Text(status)

        if (busy || line.isNotBlank()) {
            Text(line)
            if (busy) {
                val f = progressFrac
                if (f != null) LinearProgressIndicator(progress = { f.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                busy = true; line = "Starting HF download..."; progressFrac = null
                app.scope.launch {
                    EdgeModelDownloader.download(ctx) { p ->
                        line = "[${p.fileIndex}/${p.fileCount}] ${p.currentFile}"; progressFrac = p.fraction
                    }.onSuccess {
                        refresh(); line = "Download complete. Loading engine..."
                        app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}"
                    }.onFailure { line = "Download failed: ${it.message}" }
                    busy = false
                }
            }) { Text("HF") }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = {
                @Suppress("UNCHECKED_CAST")
                (pickFolder as androidx.activity.result.ActivityResultLauncher<android.net.Uri?>).launch(null)
            }) { Text("Folder") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text("Files") }
            OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { app.reloadEngineAsync() }) { Text("Reload engine") }
        }

        Text("Checklist (${checklist.count { it.second }}/${checklist.size}):", style = MaterialTheme.typography.titleSmall)
        checklist.forEach { (name, present) -> Text((if (present) "  v  " else "  -  ") + name) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Moonshine STT ======
        Text("STT — Moonshine", style = MaterialTheme.typography.titleMedium)
        Text("Status: $sttStatus")
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; line = "Downloading Moonshine (~67 MB)..."
            // app.scope is SupervisorJob on the Application — survives tab
            // switches. Using rememberCoroutineScope() cancelled the
            // download the moment the user navigated away from Router.
            app.scope.launch {
                runCatching {
                    MoonshineDownloader.download(ctx) { p ->
                        line = "[stt ${p.fileIndex}/${p.fileCount}] ${p.currentFile}"; progressFrac = p.fraction
                    }.onSuccess { app.tryLoadStt(); line = "Moonshine: ${app.sttStatus.value}" }
                        .onFailure { line = "Moonshine download failed: ${it.javaClass.simpleName}: ${it.message}" }
                }.onFailure { line = "Moonshine download crashed: ${it.javaClass.simpleName}: ${it.message}" }
                busy = false
            }
        }) { Text("Download Moonshine STT") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Kokoro TTS ======
        Text("TTS — Kokoro (~108 MB w/ all 55 voices)", style = MaterialTheme.typography.titleMedium)
        Text("Status: $ttsStatus")
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; line = "Downloading Kokoro (~108 MB)..."
            app.scope.launch {
                runCatching {
                    KokoroDownloader.download(ctx) { p ->
                        line = "[tts ${p.fileIndex}/${p.fileCount}] ${p.currentFile}"; progressFrac = p.fraction
                    }.onSuccess { app.tryLoadTts(); line = "Kokoro: ${app.ttsStatus.value}" }
                        .onFailure { line = "Kokoro download failed: ${it.javaClass.simpleName}: ${it.message}" }
                }.onFailure { line = "Kokoro download crashed: ${it.javaClass.simpleName}: ${it.message}" }
                busy = false
            }
        }) { Text("Download Kokoro TTS") }

        // Voice picker — visible once Kokoro is staged. Switch is instant
        // (just unloads/reloads the in-memory engine; no re-download).
        if (com.horizons.model.KokoroDownloader.installedDir(ctx) != null) {
            var voiceMenuOpen by remember { mutableStateOf(false) }
            val currentVoice = remember(ttsStatus) { app.kokoroVoice() }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Voice: ", modifier = Modifier.padding(end = 4.dp))
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { voiceMenuOpen = true }) {
                        Text(currentVoice)
                        androidx.compose.material3.Icon(
                            Icons.Filled.ArrowDropDown, "pick voice"
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = voiceMenuOpen,
                        onDismissRequest = { voiceMenuOpen = false }
                    ) {
                        com.horizons.model.KokoroDownloader.ALL_VOICES.forEach { v ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(v) },
                                onClick = { app.setKokoroVoice(v); voiceMenuOpen = false }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Cloud fallback keys ======
        Text("Cloud (failover)", style = MaterialTheme.typography.titleMedium)
        KeyRow(app, "OpenRouter API key", "openrouter.key")
        KeyRow(app, "Hugging Face token (optional)", "hf.token")
        KeyRow(app, "Nexa coin (optional)", "nexa.token")
        Text(
            "When VLM isn't loaded, Chat routes to OpenRouter using these credentials. " +
                "Default model: qwen/qwen-2.5-72b-instruct with claude-3.5-sonnet + gemini-2.0-flash fallbacks.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Anthropic prompt cache (wiki prefix) ======
        Text("Prompt cache (Anthropic)", style = MaterialTheme.typography.titleMedium)
        KeyRow(app, "Anthropic API key", "anthropic.key")
        Text("Cache: $cacheStatus", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Pre-warm fires a 1-token Claude call with the CLAUDE_AT_HORIZONS.md " +
                "wiki as the system block so the cache is written before any sub-agent " +
                "fan-out. 1h TTL: write costs 2x, reads 0.1x. Verify via the status line.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { app.preWarmAnthropic() }) {
                Text("Pre-warm (1h)")
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                app.preWarmAnthropic(com.horizons.provider.AnthropicDirectClient.CacheTtl.FIVE_MIN)
            }) { Text("Pre-warm (5m)") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyRow(app: HorizonsApplication, label: String, key: String) {
    val stored = app.credentials.get(key) ?: ""
    var value by remember { mutableStateOf(stored) }
    var saved by remember { mutableStateOf(app.credentials.has(key) && stored.isNotBlank()) }
    var reveal by remember { mutableStateOf(false) }
    val isSecret = saved && !reveal && stored.isNotBlank()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = { value = it; saved = false; reveal = true },
            label = { Text(if (saved) "$label ✓ saved" else label) },
            singleLine = true,
            visualTransformation = if (isSecret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                                   else androidx.compose.ui.text.input.VisualTransformation.None,
            trailingIcon = {
                if (saved) {
                    TextButton(onClick = { reveal = !reveal }) {
                        Text(if (reveal) "hide" else "show")
                    }
                }
            }
        )
        TextButton(enabled = value.isNotBlank() && !saved, onClick = {
            app.credentials.put(key, value.trim())
            saved = true
            reveal = false
            // Side-effects per-key. nexa.token gates NPU license activation
            // and must be re-exported as an env var + engine reloaded.
            if (key == "nexa.token") {
                app.applyNexaToken()
                app.reloadEngineAsync()
            }
        }) { Text(if (saved) "saved" else "save") }
    }
}

private fun currentChecklist(ctx: android.content.Context): List<Pair<String, Boolean>> {
    val dir = EdgeModelFactory.installedModelDir(ctx) ?: java.io.File(
        ctx.getExternalFilesDir(EdgeModelFactory.MODELS_DIR) ?: ctx.filesDir,
        EdgeModelDownloader.MODEL_DIR_NAME
    )
    return EdgeModelImporter.WANTED.sorted().map { name ->
        name to java.io.File(dir, name).isFile
    }
}
