package com.example.cfstats

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class ContributionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // When the widget is updated, schedule our periodic work.
        UpdateWidgetWorker.enqueueWork(context)
    }
}