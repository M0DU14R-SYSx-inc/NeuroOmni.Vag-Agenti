package com.neuroomni.horizons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.neuroomni.horizons.ui.theme.TealAbyss
import com.neuroomni.horizons.ui.theme.TealDeep
import com.neuroomni.horizons.ui.theme.TealMid
import com.neuroomni.horizons.model.ChatMessage
import com.neuroomni.horizons.model.ChatRole
import com.neuroomni.horizons.model.EdgeModel
import com.neuroomni.horizons.model.EdgeModelFactory
import com.neuroomni.horizons.model.InstanceProfile
import kotlinx.coroutines.launch
import com.neuroomni.horizons.ui.panels.ChatPanel
import com.neuroomni.horizons.ui.panels.DiagnosticsPanel
import com.neuroomni.horizons.ui.panels.RouterPanel
import com.neuroomni.horizons.ui.panels.TerminalPanel
import com.neuroomni.horizons.ui.theme.HorizonsTheme
import com.neuroomni.horizons.voice.LocalVoiceEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HorizonsTheme {
                HorizonsApp()
            }
        }
    }
}

/**
 * The four panels of Horizons UI v3.0 (Spec §2). Most use a built-in Material
 * [ImageVector]; Diagnostics uses a custom cogwheel + RPM-gauge drawable.
 */
enum class Panel(
    val title: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
) {
    Chat("Chat", icon = Icons.AutoMirrored.Filled.Chat),
    Router("Router", icon = Icons.Filled.Hub),
    Terminal("Terminal", icon = Icons.Filled.Terminal),
    Diagnostics("Diagnostics", iconRes = R.drawable.ic_diagnostics),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizonsApp() {
    var selectedPanel by remember { mutableStateOf(Panel.Chat) }
    var instanceProfile by remember { mutableStateOf(InstanceProfile.Personal) }

    // Chat state + the currently-selected edge model (defaults to the CI-safe stub).
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var frontierEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val edgeModel: EdgeModel = remember { EdgeModelFactory.create(context) }
    val scope = rememberCoroutineScope()

    // On-device TTS for agent output (Session 4 / Spec §5). Defaults to VoxSherpa
    // (Kokoro-82M), falls back to system TTS if VoxSherpa isn't installed.
    val voiceEngine = remember { LocalVoiceEngine(context.applicationContext) }

    LaunchedEffect(edgeModel) { edgeModel.initialize() }
    DisposableEffect(edgeModel) { onDispose { edgeModel.release() } }
    LaunchedEffect(voiceEngine) { voiceEngine.initialize() }
    DisposableEffect(voiceEngine) { onDispose { voiceEngine.shutdown() } }

    // Teal liquid-marble gradient behind the whole app (wallpaper-inspired).
    val appBackground = Brush.linearGradient(listOf(TealDeep, TealMid, TealAbyss))

    Scaffold(
        modifier = Modifier.background(appBackground),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Horizons") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.Transparent) {
                Panel.entries.forEach { panel ->
                    NavigationBarItem(
                        selected = selectedPanel == panel,
                        onClick = { selectedPanel = panel },
                        icon = {
                            when {
                                panel.iconRes != null -> Icon(
                                    painter = painterResource(panel.iconRes),
                                    contentDescription = panel.title,
                                )
                                panel.icon != null -> Icon(
                                    imageVector = panel.icon,
                                    contentDescription = panel.title,
                                )
                            }
                        },
                        label = { Text(panel.title, maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            InstanceProfileSelector(
                selected = instanceProfile,
                onSelect = { instanceProfile = it },
                modifier = Modifier.fillMaxWidth(),
            )
            val panelModifier = Modifier.weight(1f)
            when (selectedPanel) {
                Panel.Chat -> ChatPanel(
                    messages = messages,
                    frontierEnabled = frontierEnabled,
                    onProviderToggle = { frontierEnabled = it },
                    onSend = { text ->
                        messages.add(ChatMessage(ChatRole.User, text))
                        val replyIndex = messages.size
                        messages.add(ChatMessage(ChatRole.Assistant, ""))
                        scope.launch {
                            val sb = StringBuilder()
                            edgeModel.generateStream(text).collect { token ->
                                sb.append(token)
                                messages[replyIndex] =
                                    messages[replyIndex].copy(text = sb.toString())
                            }
                        }
                    },
                    onSpeak = { text -> voiceEngine.speak(text) },
                    modifier = panelModifier,
                )
                Panel.Router -> RouterPanel(panelModifier)
                Panel.Terminal -> TerminalPanel(panelModifier)
                Panel.Diagnostics -> DiagnosticsPanel(panelModifier)
            }
        }
    }
}

@Composable
private fun InstanceProfileSelector(
    selected: InstanceProfile,
    onSelect: (InstanceProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InstanceProfile.entries.forEach { profile ->
            FilterChip(
                selected = selected == profile,
                onClick = { onSelect(profile) },
                label = { Text(profile.displayName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = profile.color,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
