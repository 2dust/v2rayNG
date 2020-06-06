package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.v2ray.ang.R
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils

class WidgetProvider : AppWidgetProvider() {
    /**
     * 每次窗口小部件被更新都调用一次该方法
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val isRunning = Utils.isServiceRun(context, "com.v2ray.ang.service.V2RayVpnService")
        updateWidgetBackground(context, appWidgetManager, appWidgetIds, isRunning)
    }

    private fun updateWidgetBackground(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, isRunning: Boolean) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        val intent = Intent(context, WidgetProvider::class.java)
        intent.setAction(AppConfig.BROADCAST_ACTION_WIDGET_CLICK)
        val pendingIntent = PendingIntent.getBroadcast(context, R.id.layout_switch, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViews.setOnClickPendingIntent(R.id.layout_switch, pendingIntent)
        if (isRunning) {
            remoteViews.setInt(R.id.layout_switch, "setBackgroundResource", R.drawable.ic_rounded_corner_theme);
        } else {
            remoteViews.setInt(R.id.layout_switch, "setBackgroundResource", R.drawable.ic_rounded_corner_grey);
        }

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
            val manager = AppWidgetManager.getInstance(context)
            updateWidgetBackground(context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
                    !isRunning);
        }
    }
}
