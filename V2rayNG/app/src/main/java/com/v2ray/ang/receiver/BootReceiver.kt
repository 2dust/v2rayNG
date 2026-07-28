package com.v2ray.ang.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil

class BootReceiver : BroadcastReceiver() {
    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It handles BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, and MY_PACKAGE_REPLACED.
     * If the conditions are met, it starts the V2Ray service.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (context == null) return

        LogUtil.i(AppConfig.TAG, "BootReceiver received: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Continue
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                if (userManager != null && !userManager.isUserUnlocked) {
                    LogUtil.w(AppConfig.TAG, "BootReceiver: User is locked, skipping auto start")
                    return
                }
            }
            else -> {
                LogUtil.w(AppConfig.TAG, "BootReceiver: Unhandled action: $action")
                return
            }
        }

        if (!MmkvManager.decodeStartOnBoot()) {
            LogUtil.i(AppConfig.TAG, "BootReceiver: Auto-start on boot is disabled")
            return
        }

        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            LogUtil.w(AppConfig.TAG, "BootReceiver: No server selected")
            return
        }

        LogUtil.i(AppConfig.TAG, "BootReceiver: Starting V2Ray service")
        CoreServiceManager.startVService(context)
        SubscriptionUpdater.sync(context)
    }
}
