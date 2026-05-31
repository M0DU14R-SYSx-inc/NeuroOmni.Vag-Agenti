package com.neuroomni.horizons.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.neuroomni.horizons.BuildConfig
import com.neuroomni.horizons.model.EdgeModelFactory
import com.neuroomni.horizons.model.EdgeModelInstaller
import com.neuroomni.horizons.provider.CredentialStore
import com.neuroomni.horizons.provider.EndpointConfig
import com.neuroomni.horizons.provider.ProviderId
import com.neuroomni.horizons.provider.Transport
import com.neuroomni.horizons.ui.theme.AccentGreen
import com.neuroomni.horizons.ui.theme.AccentYellow
import com.neuroomni.horizons.ui.theme.HorizonsOnSurfaceMuted
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Router panel (Spec §3 "Router Panel" / Architecture §5). Derek picks the active
 * provider toggle, sees which billing pool it burns and whether it's configured, and
 * edits the endpoint (API key, base URL, model string) — all persisted to the
 * Keystore-backed [CredentialStore]. The selected toggle is what the Chat panel routes to.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouterPanel(
    activeProvider: ProviderId,
    onSelectProvider: (ProviderId) -> Unit,
    credentialStore: CredentialStore,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Provider Routing",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "The toggle is the routing decision. Pick one — Chat talks directly to it.",
            style = MaterialTheme.typography.bodySmall,
            color = HorizonsOnSurfaceMuted,
        )

        // --- Provider toggle selector --------------------------------------
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderId.entries.forEach { provider ->
                val selectable = provider.implemented || provider.transport == Transport.OnDevice
                FilterChip(
                    selected = provider == activeProvider,
                    onClick = { onSelectProvider(provider) },
                    enabled = selectable,
                    label = { Text(provider.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        // --- Active provider status ----------------------------------------
        // Bumped on save so the status card reflects freshly-stored credentials.
        var reloadTick by remember { mutableStateOf(0) }
        val activeConfig = remember(activeProvider, reloadTick) { credentialStore.load(activeProvider) }
        StatusCard(activeProvider, activeConfig)

        // --- Endpoint configuration (frontier HTTP providers only) ---------
        when {
            activeProvider.isEdge -> EdgeModelCard(credentialStore)
            activeProvider.transport == Transport.TermuxShell -> Text(
                "${activeProvider.displayName} routes through the Termux shell layer — " +
                    "coming in Session 6 (the same path Tasker rides on).",
                style = MaterialTheme.typography.bodyMedium,
                color = HorizonsOnSurfaceMuted,
            )
            !activeProvider.implemented -> Text(
                "${activeProvider.displayName} (Vertex) needs a GCP service-account → JWT flow. " +
                    "Scaffolded; not wired yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = HorizonsOnSurfaceMuted,
            )
            else -> EndpointEditor(
                provider = activeProvider,
                initial = activeConfig,
                onSave = { credentialStore.save(it); reloadTick++ },
            )
        }
    }
}

@Composable
private fun StatusCard(provider: ProviderId, config: EndpointConfig) {
    val (label, color) = when {
        provider.isEdge -> "On-device" to HorizonsOnSurfaceMuted
        !provider.implemented -> "Not wired" to AccentYellow
        config.isConfigured -> "Configured" to AccentGreen
        else -> "Needs setup" to AccentYellow
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledLabelColor = color,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
            LabeledValue("Billing", provider.billingPool.label)
            LabeledValue("Monitor", provider.billingPool.monitorHint)
            if (!provider.isEdge && config.modelString.isNotBlank()) {
                LabeledValue("Model", config.modelString)
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = HorizonsOnSurfaceMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Edge model status + on-device importer (no adb needed): pick the `weights-8-8.nexa`
 * file from Downloads and the app copies it into the models dir [EdgeModelFactory] reads.
 * Surfaces honestly whether this build can actually run it on the NPU.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgeModelCard(credentialStore: CredentialStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Universal Nexa key, entered here and kept in the encrypted store (Architecture §12).
    var nexaKey by remember { mutableStateOf(credentialStore.nexaToken) }
    var keyVisible by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }

    var installedDir by remember { mutableStateOf(EdgeModelFactory.installedModelDir(context)) }
    var copying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    // OmniNeural-4B is a multi-file set, so we import the whole `nexaml/` folder.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        progress = null
        status = "Importing model folder…"
        scope.launch {
            val result = EdgeModelInstaller.installTree(context, uri) { p -> progress = p.fraction }
            copying = false
            result
                .onSuccess {
                    installedDir = EdgeModelFactory.installedModelDir(context)
                    status = "Imported ${it.name}/. Restart the app to load it on the NPU."
                }
                .onFailure { status = "Import failed: ${it.message}" }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Edge runs entirely on-device — OmniNeural-4B on the Hexagon NPU. " +
                "No endpoint, no key, no network.",
            style = MaterialTheme.typography.bodyMedium,
            color = HorizonsOnSurfaceMuted,
        )

        // Honest build capability: the default CI APK is stub-only and can't run the NPU.
        if (BuildConfig.NEXA_ENABLED) {
            LabeledValue("Build", "Nexa-enabled — can run the model on the NPU")
        } else {
            Text(
                "This build is stub-only: it answers with the canned edge stub. To run the " +
                    "real model on the NPU, sideload the Nexa-enabled APK. You can still import " +
                    "the model here so it's staged and ready.",
                style = MaterialTheme.typography.bodySmall,
                color = AccentYellow,
            )
        }

        // Nexa key entry — stored encrypted, applied on next app start.
        OutlinedTextField(
            value = nexaKey,
            onValueChange = { nexaKey = it; keySaved = false },
            label = { Text("Nexa key") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                credentialStore.nexaToken = nexaKey.trim()
                keySaved = true
            }) { Text("Save key") }
            val keyStatus = when {
                keySaved -> "Saved — restart to apply" to AccentGreen
                nexaKey.isNotBlank() -> "Key set" to HorizonsOnSurfaceMuted
                else -> "No key yet" to AccentYellow
            }
            Text(
                keyStatus.first,
                style = MaterialTheme.typography.bodySmall,
                color = keyStatus.second,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        installedDir?.let {
            LabeledValue("Model", "${it.name}/ (${formatSize(dirSize(it))})")
        } ?: LabeledValue("Model", "no nexaml/ folder installed yet")

        if (copying) {
            val frac = progress
            if (frac != null) {
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Button(onClick = { picker.launch(null) }, enabled = !copying) {
            Text(if (installedDir != null) "Replace model folder" else "Import nexaml folder")
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HorizonsOnSurfaceMuted)
        }
    }
}

private fun dirSize(dir: File): Long =
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointEditor(
    provider: ProviderId,
    initial: EndpointConfig,
    onSave: (EndpointConfig) -> Unit,
) {
    // Re-seed every field when the active provider changes.
    var baseUrl by remember(provider) { mutableStateOf(initial.baseUrl) }
    var modelString by remember(provider) { mutableStateOf(initial.modelString) }
    var apiKey by remember(provider) { mutableStateOf(initial.apiKey) }
    var maxTokens by remember(provider) { mutableStateOf(initial.maxTokens.toString()) }
    var keyVisible by remember(provider) { mutableStateOf(false) }
    var savedTick by remember(provider) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; savedTick = false },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = modelString,
            onValueChange = { modelString = it; savedTick = false },
            label = { Text("Model string") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (provider != ProviderId.OllamaCompatible) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; savedTick = false },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = maxTokens,
            onValueChange = { maxTokens = it.filter(Char::isDigit); savedTick = false },
            label = { Text("Max tokens") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                onSave(
                    EndpointConfig(
                        provider = provider,
                        apiKey = apiKey.trim(),
                        baseUrl = baseUrl.trim(),
                        modelString = modelString.trim(),
                        maxTokens = maxTokens.toIntOrNull()?.coerceAtLeast(1) ?: initial.maxTokens,
                    ),
                )
                savedTick = true
            }) { Text("Save endpoint") }
            if (savedTick) {
                Text(
                    "Saved to Keystore",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
