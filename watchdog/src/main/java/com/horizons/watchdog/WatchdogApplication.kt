package com.horizons.watchdog

import android.app.Application
import android.content.Intent
import com.horizons.watchdog.service.WatchdogService

class WatchdogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startForegroundService(Intent(this, WatchdogService::class.java))
    }
}
