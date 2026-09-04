package com.v2ray.ang.receiver

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.widget.LauncherWidget
import com.v2ray.ang.ui.widget.LauncherWidgetStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = LauncherWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        LauncherWidgetStateRepository.recordServiceState(CoreServiceManager.isRunning())
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getIntExtra("key", 0)
        val shouldUpdate = intent.action == AppConfig.BROADCAST_ACTION_ACTIVITY &&
                handleServiceEvent(key, intent)
        super.onReceive(context, intent)
        if (shouldUpdate) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    glanceAppWidget.updateAll(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun handleServiceEvent(key: Int, intent: Intent): Boolean = when (key) {
        AppConfig.MSG_STATE_START_SUCCESS,
        AppConfig.MSG_STATE_RUNNING -> {
            LauncherWidgetStateRepository.recordServiceState(isRunning = true)
            true
        }

        AppConfig.MSG_STATE_NOT_RUNNING,
        AppConfig.MSG_STATE_START_FAILURE,
        AppConfig.MSG_STATE_STOP_SUCCESS -> {
            LauncherWidgetStateRepository.recordServiceState(isRunning = false)
            true
        }

        AppConfig.MSG_MEASURE_DELAY_RESULT -> {
            val result = intent.serializable<ConnectionTestResult>("content") ?: return false
            LauncherWidgetStateRepository.storeResult(result, MmkvManager.getSelectServer())
            true
        }

        else -> false
    }
}
