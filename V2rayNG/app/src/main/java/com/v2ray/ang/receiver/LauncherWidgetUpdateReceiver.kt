package com.v2ray.ang.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.ui.widget.refreshLauncherWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** App-only updates have their own receiver, separate from Glance's broadcast lifecycle. */
class LauncherWidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppConfig.BROADCAST_ACTION_ACTIVITY) return
        when (intent.getIntExtra("key", 0)) {
            AppConfig.MSG_STATE_RUNNING,
            AppConfig.MSG_STATE_NOT_RUNNING,
            AppConfig.MSG_STATE_START_SUCCESS,
            AppConfig.MSG_STATE_START_FAILURE,
            AppConfig.MSG_STATE_STOP_SUCCESS,
            AppConfig.MSG_MEASURE_DELAY,
            AppConfig.MSG_MEASURE_DELAY_RESULT,
            AppConfig.MSG_SELECTED_PROFILE_CHANGED -> Unit
            else -> return
        }
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        val updateScope = CoroutineScope(Dispatchers.IO)
        updateScope.launch {
            try {
                refreshLauncherWidgets(context.applicationContext)
            } finally {
                pendingResult.finish()
                updateScope.cancel()
            }
        }
    }
}
