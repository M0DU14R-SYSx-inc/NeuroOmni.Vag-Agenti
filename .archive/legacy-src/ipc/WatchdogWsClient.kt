package com.horizons.ipc

import android.content.Context
import com.horizons.shared.ipc.WsContract

class WatchdogWsClient(private val context: Context) {
    fun start() {
        // Phase 9: OkHttp WebSocket client connecting to ws://127.0.0.1:${WsContract.DEFAULT_PORT}.
        // Send Hello, periodic Telemetry + Heartbeat. Reconnect with backoff if watchdog absent.
        // Receive HotSwap / RestartSession commands from watchdog.
    }
    fun stop() {}
}
