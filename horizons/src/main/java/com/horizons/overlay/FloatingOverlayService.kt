package com.horizons.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Phase 7: foreground notification + WindowManager TYPE_APPLICATION_OVERLAY views
        // for the 4 floating tiles. Each tile has its own WindowManager.LayoutParams,
        // independently draggable, x/y persisted in SharedPreferences keyed by tile id.
        // Tiles: MicTile (green-on-record), AiPillTile (meta|bash split),
        //        ScreenAskTile (lightbulb + lightning anim), ReadBackTile (auto-fires; tap = stop).
        return START_STICKY
    }
}
