package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.os.BundleCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.RemoteControlManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppConfig.TASKER_ACTION_FIRE_SETTING) return
        // Before API 34, and when the host does not share its identity, only the saved capability
        // authenticates the request. Binder.getCallingUid() here would identify Android, not the host.
        val senderUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) sentFromUid else -1
        val pendingResult = goAsync()
        val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        requestScope.launch {
            try {
                val bundle = intent.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE) ?: return@launch
                if (!RemoteControlManager.isAuthorized(
                        context, senderUid,
                        bundle.getString(AppConfig.TASKER_EXTRA_BUNDLE_PACKAGE),
                        bundle.getString(AppConfig.TASKER_EXTRA_BUNDLE_TOKEN)
                    )) return@launch
                val request = TaskerRequest.parse(
                    BundleCompat.getSerializable(bundle, AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, Boolean::class.javaObjectType),
                    bundle.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID)
                ) ?: return@launch
                if (!request.start) {
                    LauncherManager.stopService(context)
                } else if (request.guid == AppConfig.TASKER_DEFAULT_GUID) {
                    LauncherManager.startServiceFromToggle(context)
                } else if (MmkvManager.decodeServerConfig(request.guid) != null) {
                    // Never persist an unknown GUID, even for an authorized controller.
                    LauncherManager.startService(context, request.guid)
                }
            } catch (error: Exception) {
                // Malformed parcel/bundle exceptions can contain attacker-supplied credentials.
                LogUtil.e(AppConfig.TAG, "TaskerReceiver: request failed (${error.javaClass.simpleName})")
            } finally {
                pendingResult.finish()
                requestScope.cancel()
            }
        }
    }
}

internal data class TaskerRequest(val start: Boolean, val guid: String) {
    companion object {
        fun parse(start: Boolean?, guid: String?): TaskerRequest? =
            if (start == null || guid.isNullOrBlank()) null else TaskerRequest(start, guid)
    }
}
