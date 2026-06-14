package com.horizons

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Greenfield MainActivity — placeholder until the design artifact lands and
 * the chat / vision / voice tiles are built. Shows engine status so the
 * operator can confirm the SDK initialized.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as HorizonsApplication
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { padding ->
                        val status by app.engineStatus.collectAsState()
                        val error by app.engineError.collectAsState()
                        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                            Text("Horizons — greenfield build", style = MaterialTheme.typography.titleLarge)
                            Text("Engine: $status", style = MaterialTheme.typography.bodyLarge)
                            error?.let { Text("Error: $it", style = MaterialTheme.typography.bodySmall) }
                            Text(
                                "UI tiles land in follow-up at-bats. See SOTU.md.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
