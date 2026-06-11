package com.neuroomni.horizons

import android.app.Application

/**
 * App entry point. Installs a global uncaught-exception handler so a fatal JVM
 * crash is written to [CrashLog] before the process dies, then chains to the
 * platform handler so Android still tears the process down normally. The next
 * launch reads the saved report and shows it (see MainActivity), so a crash is
 * diagnosable on-device instead of a bare "keeps stopping" dialog.
 */
class HorizonsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.write(this, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
