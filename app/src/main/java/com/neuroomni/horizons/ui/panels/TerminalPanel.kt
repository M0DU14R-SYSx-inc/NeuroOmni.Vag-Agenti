package com.neuroomni.horizons.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A real, typeable terminal. Commands are run with the device shell
 * (`/system/bin/sh -c <cmd>`) inside the app's own sandbox — so unprivileged
 * commands like `ls`, `getprop`, `cat`, `ps`, `pm list packages` work, while
 * anything needing root or another app's data is denied by Android, as expected.
 *
 * Not a Termux session (that's a separate app with its own PTY); this is a direct
 * shell-exec console for inspecting the device and the app's own files — handy for
 * confirming the model files actually landed (`ls -la <filesDir>/models`).
 */
@Composable
fun TerminalPanel(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember {
        mutableStateOf(
            "Horizons shell — runs in the app sandbox.\n" +
                "Try: getprop ro.product.model   |   ls -la \$HOME   |   ps\n\n",
        )
    }
    var running by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    fun run(cmd: String) {
        if (cmd.isBlank() || running) return
        output += "$ $cmd\n"
        command = ""
        running = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { execShell(cmd) }
            output += if (result.isEmpty()) "(no output)\n\n" else "$result\n\n"
            running = false
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Terminal", style = MaterialTheme.typography.headlineSmall)
        Text(
            output,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("type a command…") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { run(command) }),
            )
            Button(
                onClick = { run(command) },
                enabled = !running,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(if (running) "…" else "Run") }
        }
    }
}

/** Execute [cmd] via the device shell, merging stderr into stdout. */
private fun execShell(cmd: String): String = runCatching {
    val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
        .redirectErrorStream(true)
        .start()
    val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    process.waitFor()
    text.trimEnd()
}.getOrElse { "error: ${it.message}" }
