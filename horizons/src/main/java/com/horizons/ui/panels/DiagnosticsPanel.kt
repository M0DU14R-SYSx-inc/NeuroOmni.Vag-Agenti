package com.horizons.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.horizons.HorizonsApplication

@Composable
fun DiagnosticsPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as HorizonsApplication

    val engineStatus by app.engineStatus.collectAsState()
    val engineError by app.engineError.collectAsState()
    val sttStatus by app.sttStatus.collectAsState()
    val ttsStatus by app.ttsStatus.collectAsState()
    val cacheStatus by app.cacheStatus.collectAsState()

    // InteractionLogger is constructed per-call elsewhere; mirror that here.
    val logger = remember(ctx) { com.horizons.logging.InteractionLogger(ctx) }
    var refreshCounter by remember { mutableStateOf(0) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(refreshCounter) {
        lines = runCatching { logger.tail(30) }.getOrDefault(emptyList())
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ====== Engines ======
        Text("Engines", style = MaterialTheme.typography.titleMedium)
        Text("VLM: ${app.engine().backendTag}  ($engineStatus)")
        engineError?.let { Text("ENGINE ERROR: $it") }
        Text("STT: $sttStatus")
        Text("TTS: $ttsStatus")

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Anthropic prompt cache ======
        Text("Anthropic prompt cache", style = MaterialTheme.typography.titleMedium)
        Text("Cache: $cacheStatus", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Refresh just bumps the counter to force recompose / log reload.
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { refreshCounter++ }) {
                Text("Refresh")
            }
            Button(modifier = Modifier.weight(1f), onClick = { app.preWarmAnthropic() }) {
                Text("Pre-warm now")
            }
        }
        Text(
            "Pre-warm fires a 1-token call with the wiki as system block so the next " +
                "real call hits. 1h TTL is the default.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Recent log lines ======
        Text("Recent log lines", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshCounter++ }) { Text("Reload") }
            Text("(${lines.size} lines)", style = MaterialTheme.typography.bodySmall)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (lines.isEmpty()) {
                Text("No log lines yet today.", style = MaterialTheme.typography.bodySmall)
            } else {
                lines.forEach { ln ->
                    Text(
                        ln,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // ====== Build info ======
        Text("Build info", style = MaterialTheme.typography.titleMedium)
        val pkg = ctx.packageName
        val info = remember(pkg) {
            runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
        }
        val versionName = info?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = info?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode.toString()
            else it.versionCode.toString()
        } ?: "?"
        // ApplicationInfo.FLAG_DEBUGGABLE is the cheapest debug/release signal without BuildConfig.
        val isDebug = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Text("Package: $pkg")
        Text("Version: $versionName ($versionCode)")
        Text("Build type: ${if (isDebug) "debug" else "release"}")

        Spacer(Modifier.height(24.dp))
    }
}
