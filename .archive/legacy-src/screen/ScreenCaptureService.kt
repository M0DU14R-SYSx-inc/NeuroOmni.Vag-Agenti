package com.horizons.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred

class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        latestConsent = if (data != null) Consent(resultCode, data) else null

        ensureChannel(this)
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen-ask")
            .setContentText("Capturing screen for VLM")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Signal that startForeground has returned, i.e. the FGS is now in
        // foreground state and MediaProjection.getMediaProjection() is safe.
        // Callers await this instead of guessing a delay.
        readySignal?.complete(Unit)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        latestConsent = null
        readySignal = null
        super.onDestroy()
    }

    data class Consent(val resultCode: Int, val data: Intent)

    companion object {
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 4711
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        /** Set by [onStartCommand]; read by [ScreenshotCapture.captureToFile]. */
        @Volatile
        var latestConsent: Consent? = null
            internal set

        /**
         * Reset on every [start]; completed inside [onStartCommand] once the
         * service has reached foreground state via startForeground(). Callers
         * `await()` this before invoking MediaProjectionManager.getMediaProjection
         * so we don't race with the SecurityException("FGS required") trap.
         */
        @Volatile
        private var readySignal: CompletableDeferred<Unit>? = null

        /**
         * Start the FGS so MediaProjection can run on API 34+. Returns a Deferred
         * that completes when the service is actually in foreground state — the
         * caller awaits it before calling getMediaProjection.
         */
        fun start(context: Context, resultCode: Int, data: Intent): CompletableDeferred<Unit> {
            val signal = CompletableDeferred<Unit>()
            readySignal = signal
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return signal
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen capture",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Active while screen-ask captures a frame for the VLM." }
            )
        }
    }
}
