package com.iwankurnia.hub.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Hosts [AppCountWidget], the single-row per-app unread bar. */
class AppCountWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AppCountWidget()
}
