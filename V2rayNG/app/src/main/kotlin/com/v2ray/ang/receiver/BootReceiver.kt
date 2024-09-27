package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.util.MmkvManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action && MmkvManager.decodeStartOnBoot()) {
            if (MmkvManager.getSelectServer().isNullOrEmpty()) {
                return
            }
            V2RayServiceManager.startV2Ray(context!!)
        }
    }
}