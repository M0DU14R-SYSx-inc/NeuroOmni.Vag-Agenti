package com.horizons.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RouterPanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Router — provider library")
        // Phase 6: list saved NamedBackend entries, add/edit/delete row UI.
        // Per-row toggles: dispatcherEligible, isFailoverTarget.
        // Key entry via CredentialStore. Web-search tool entry. Nexa key + model downloader card.
    }
}
