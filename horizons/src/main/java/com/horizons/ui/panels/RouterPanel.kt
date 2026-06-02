package com.horizons.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.launch

@Composable
fun RouterPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as HorizonsApplication
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressFrac by remember { mutableStateOf<Float?>(null) }
    var status by remember {
        mutableStateOf(
            EdgeModelFactory.installedModelDir(ctx)?.let { "Model staged: ${it.absolutePath}" }
                ?: "No model staged."
        )
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Router — provider library")
        Text("Engine: ${app.engine().backendTag}")
        Text(status)

        if (downloading) {
            Text(progressText)
            val frac = progressFrac
            if (frac != null) {
                LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Button(
            enabled = !downloading,
            onClick = {
                downloading = true
                progressText = "Starting download..."
                progressFrac = null
                scope.launch {
                    val result = EdgeModelDownloader.download(ctx) { p ->
                        progressText = "[${p.fileIndex}/${p.fileCount}] ${p.currentFile}"
                        progressFrac = p.fraction
                    }
                    downloading = false
                    result.onSuccess { dest ->
                        status = "Model staged: ${dest.absolutePath}"
                        progressText = "Download complete. Loading engine..."
                        app.reloadEngine()
                        progressText = "Engine ready: ${app.engine().backendTag}"
                    }.onFailure { e ->
                        progressText = "Download failed: ${e.message}"
                    }
                }
            }
        ) { Text("Download OmniNeural-4B-mobile") }

        // Phase 6: Keys section (Nexa, HuggingFace, OpenRouter, Vertex, AI Studio, Anthropic).
        // Phase 6: Provider library list (add/edit/delete NamedBackend entries).
    }
}
