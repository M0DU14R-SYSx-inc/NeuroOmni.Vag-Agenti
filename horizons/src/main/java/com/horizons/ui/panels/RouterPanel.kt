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
        scope.launch {
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
        scope.launch {
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
                scope.launch {
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
            scope.launch {
                MoonshineDownloader.download(ctx) { p ->
                    line = "[stt ${p.fileIndex}/${p.fileCount}] ${p.currentFile}"; progressFrac = p.fraction
                }.onSuccess { app.tryLoadStt(); line = "Moonshine: ${app.sttStatus.value}" }
                    .onFailure { line = "Moonshine download failed: ${it.message}" }
                busy = false
            }
        }) { Text("Download Moonshine STT") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Kokoro TTS ======
        Text("TTS — Kokoro (am_adam)", style = MaterialTheme.typography.titleMedium)
        Text("Status: $ttsStatus")
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; line = "Downloading Kokoro (~87 MB)..."
            scope.launch {
                KokoroDownloader.download(ctx) { p ->
                    line = "[tts ${p.fileIndex}/${p.fileCount}] ${p.currentFile}"; progressFrac = p.fraction
                }.onSuccess { app.tryLoadTts(); line = "Kokoro: ${app.ttsStatus.value}" }
                    .onFailure { line = "Kokoro download failed: ${it.message}" }
                busy = false
            }
        }) { Text("Download Kokoro TTS") }

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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyRow(app: HorizonsApplication, label: String, key: String) {
    var value by remember { mutableStateOf(app.credentials.get(key) ?: "") }
    var saved by remember { mutableStateOf(app.credentials.has(key)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = { value = it; saved = false },
            label = { Text(label) },
            singleLine = true
        )
        TextButton(enabled = value.isNotBlank() && !saved, onClick = {
            app.credentials.put(key, value.trim()); saved = true
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
