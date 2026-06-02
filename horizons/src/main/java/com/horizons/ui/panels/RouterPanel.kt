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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; line = "Scanning folder..."; progressFrac = null
        scope.launch {
            EdgeModelImporter.importFromTree(ctx, uri) { p ->
                line = "Copying [${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                progressFrac = p.fraction
            }.onSuccess { r ->
                line = "Copied ${r.copied.size}/14. " +
                    if (r.missing.isEmpty()) "Loading engine..." else "Still missing: ${r.missing.size}"
                refresh()
                if (r.missing.isEmpty()) { app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}" }
            }.onFailure { line = "Import failed: ${it.message}" }
            busy = false
        }
    }

    val pickFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true; line = "Importing ${uris.size} file(s)..."; progressFrac = null
        scope.launch {
            EdgeModelImporter.importFiles(ctx, uris) { p ->
                line = "Copying [${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                progressFrac = p.fraction
            }.onSuccess { r ->
                line = "Copied ${r.copied.size} file(s). " +
                    if (r.missing.isEmpty()) "Loading engine..." else "Still missing: ${r.missing.size}"
                refresh()
                if (r.missing.isEmpty()) { app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}" }
            }.onFailure { line = "Import failed: ${it.message}" }
            busy = false
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Router — provider library")
        Text("Engine: ${app.engine().backendTag}")
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
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; line = "Starting HF download..."; progressFrac = null
                    scope.launch {
                        EdgeModelDownloader.download(ctx) { p ->
                            line = "[${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                            progressFrac = p.fraction
                        }.onSuccess {
                            refresh(); line = "Download complete. Loading engine..."
                            app.reloadEngine(); line = "Engine ready: ${app.engine().backendTag}"
                        }.onFailure { line = "Download failed: ${it.message}" }
                        busy = false
                    }
                }
            ) { Text("Download from HF") }

            OutlinedButton(
                enabled = !busy,
                onClick = { pickFolder.launch(EdgeModelImporter.DOWNLOADS_TREE_URI) }
            ) { Text("Import folder") }

            OutlinedButton(
                enabled = !busy,
                onClick = { pickFiles.launch(arrayOf("*/*")) }
            ) { Text("Import files") }
        }

        Text("File checklist:")
        LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            items(checklist) { (name, present) ->
                Text((if (present) "  v  " else "  -  ") + name)
            }
        }

        // Phase 6: Keys section (Nexa, HF, OpenRouter, Vertex, AI Studio, Anthropic).
        // Phase 6: Provider library list (add/edit/delete NamedBackend entries).
    }
}

private fun currentChecklist(ctx: android.content.Context): List<Pair<String, Boolean>> {
    val dir = EdgeModelFactory.installedModelDir(ctx) ?: java.io.File(
        ctx.getExternalFilesDir(EdgeModelFactory.MODELS_DIR) ?: ctx.filesDir,
        EdgeModelDownloader.MODEL_DIR_NAME
    )
    return EdgeModelImporter.WANTED.sorted().map { name ->
        name to java.io.File(dir, name).let { it.isFile && it.length() > 0 }
    }
}
