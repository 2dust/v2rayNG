package com.v2ray.ang.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException

internal suspend fun refreshLauncherWidgets(context: Context) {
    runWidgetUpdate(
        update = {
            LauncherWidgetStateRepository.instance.refresh()
            LauncherWidget().updateAll(context)
        },
        onFailure = { LogUtil.e("LauncherWidget", "Failed to refresh daemon widgets", it) },
    )
}

/** A presentation failure must not escape into the process that owns the VPN. */
internal suspend fun runWidgetUpdate(update: suspend () -> Unit, onFailure: (Exception) -> Unit) {
    try {
        update()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
    }
}
