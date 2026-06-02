package com.horizons

import android.app.Application
import com.horizons.ipc.WatchdogWsClient

class HorizonsApplication : Application() {
    lateinit var watchdog: WatchdogWsClient
        private set

    override fun onCreate() {
        super.onCreate()
        watchdog = WatchdogWsClient(this).also { it.start() }
    }
}
