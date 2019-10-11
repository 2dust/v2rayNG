package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import org.jetbrains.anko.toast

class WidgetProvider : AppWidgetProvider() {
    /**
     * 每次窗口小部件被更新都调用一次该方法
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        val intent = Intent(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        val pendingIntent = PendingIntent.getBroadcast(context, R.id.layout_switch, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViews.setOnClickPendingIntent(R.id.layout_switch, pendingIntent)

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    /**
     * 接收窗口小部件点击时发送的广播
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppConfig.BROADCAST_ACTION_WIDGET_CLICK == intent.action) {

            val isRunning = Utils.isServiceRun(context, "com.v2ray.ang.service.V2RayVpnService")
            if (isRunning) {
//                context.toast(R.string.toast_services_stop)
                Utils.stopVService(context)
            } else {
//                context.toast(R.string.toast_services_start)
                Utils.startVService(context)
            }
        }
    }

}
