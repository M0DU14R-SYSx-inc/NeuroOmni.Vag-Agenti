package com.horizons.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        line = "Importing from picked folder..."
        progressFrac = null
        scope.launch {
            val res = EdgeModelImporter.importFromTree(ctx, uri)
            res.onSuccess { r ->
                line = if (r.missing.isEmpty()) {
                    "Imported ${r.copied.size}/14 files. Loading engine..."
                } else {
                    "Imported ${r.copied.size}/14. Missing: ${r.missing.joinToString()}"
                }
                if (r.missing.isEmpty()) {
                    app.reloadEngine()
                    line = "Engine ready: ${app.engine().backendTag}"
                }
                status = "Model staged: ${r.destDir.absolutePath}"
            }.onFailure { e ->
                line = "Import failed: ${e.message}"
            }
            busy = false
        }
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Router — provider library")
        Text("Engine: ${app.engine().backendTag}")
        Text(status)

        if (busy) {
            Text(line)
            val frac = progressFrac
            if (frac != null) {
                LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (line.isNotBlank()) {
            Text(line)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    line = "Starting download..."
                    progressFrac = null
                    scope.launch {
                        val result = EdgeModelDownloader.download(ctx) { p ->
                            line = "[${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                            progressFrac = p.fraction
                        }
                        result.onSuccess { dest ->
                            status = "Model staged: ${dest.absolutePath}"
                            line = "Download complete. Loading engine..."
                            app.reloadEngine()
                            line = "Engine ready: ${app.engine().backendTag}"
                        }.onFailure { e ->
                            line = "Download failed: ${e.message}"
                        }
                        busy = false
                    }
                }
            ) { Text("Download from HF") }

            OutlinedButton(
                enabled = !busy,
                onClick = { pickFolder.launch(null) }
            ) { Text("Import from folder") }
        }

        // Phase 6: Keys section (Nexa, HuggingFace, OpenRouter, Vertex, AI Studio, Anthropic).
        // Phase 6: Provider library list (add/edit/delete NamedBackend entries).
    }
}
