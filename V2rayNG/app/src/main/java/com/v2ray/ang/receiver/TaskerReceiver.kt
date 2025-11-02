package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.V2RayServiceManager

class TaskerReceiver : BroadcastReceiver() {

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It retrieves the bundle from the intent and checks the switch and guid values.
     * Depending on the switch value, it starts or stops the V2Ray service.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
            val switch = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false)
            val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID).orEmpty()

            if (switch == null || TextUtils.isEmpty(guid)) {
                return
            } else if (switch) {
                if (guid == AppConfig.TASKER_DEFAULT_GUID) {
                    V2RayServiceManager.startVServiceFromToggle(context)
                } else {
                    V2RayServiceManager.startVService(context, guid)
                }
            } else {
                V2RayServiceManager.stopVService(context)
            }
        } catch (e: Exception) {
            android.util.Log.e(AppConfig.TAG, "Error processing Tasker broadcast", e)
        }
    }
}
