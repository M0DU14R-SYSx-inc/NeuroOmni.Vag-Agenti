package com.horizons.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class ChatAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Phase 7: collapsible chat surface for the home screen.
        // RemoteViews: text bar + mic button. Tap mic -> Dictation, tap bar -> launch MainActivity Chat panel.
    }
}
