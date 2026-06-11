package com.horizons.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.horizons.MainActivity
import com.horizons.R

class ChatAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_chat)
        val launch = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_chat_input, launch)
        views.setOnClickPendingIntent(R.id.widget_chat_mic, launch)
        mgr.updateAppWidget(ComponentName(context, ChatAppWidgetProvider::class.java), views)
        ids.forEach { mgr.updateAppWidget(it, views) }
    }
}
