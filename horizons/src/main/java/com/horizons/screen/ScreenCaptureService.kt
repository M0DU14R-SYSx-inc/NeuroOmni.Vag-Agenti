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

/**
 * Foreground service required for MediaProjection on Android 14+ (API 34+).
 *
 * MUST be started (and reach foreground state) BEFORE
 * MediaProjectionManager.getMediaProjection(resultCode, data) is invoked, otherwise
 * the platform throws SecurityException("Media projections require a foreground service
 * of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION").
 *
 * The consent (resultCode + Intent data) is passed through as extras so the caller has
 * a single entry point. ScreenshotCapture reads them back via the static [latestConsent]
 * holder once the service is up.
 */
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
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        latestConsent = null
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
         * Start the FGS so MediaProjection can run on API 34+. Caller (Activity) must
         * invoke this BEFORE [android.media.projection.MediaProjectionManager.getMediaProjection].
         */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
