package com.v2ray.ang.receiver

import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.v2ray.ang.ui.widget.LauncherWidget

class WidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LauncherWidget()
}
