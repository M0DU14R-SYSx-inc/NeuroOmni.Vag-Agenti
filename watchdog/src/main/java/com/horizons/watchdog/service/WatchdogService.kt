package com.horizons.watchdog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.horizons.shared.ipc.WsContract

class WatchdogService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        // Phase 9: start Java-WebSocket server on 127.0.0.1:${WsContract.DEFAULT_PORT}.
        // Track connected Horizons client; mutual heartbeat; on N misses, attempt resurrection.
        // Telemetry sink: append to ring buffer + drive fallback ladder.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        // Phase 9: stop WS server, persist any unsent telemetry.
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHAN_ID, "Watchdog", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHAN_ID)
            .setContentTitle("Horizons Watchdog")
            .setContentText("Hosting loopback :${WsContract.DEFAULT_PORT}")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHAN_ID = "watchdog_fgs"
        private const val NOTIF_ID = 1001
    }
}
