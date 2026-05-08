package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.NotificationHelper
import java.lang.ref.SoftReference

class CoreProxyOnlyService : Service(), ServiceControl {
    private var isRunning = false

    /**
     * Initializes the service.
     */
    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-Proxy: Service command received")
        ensureForegroundStarted()
        CoreServiceManager.startCoreLoopAsync(null) { ok ->
            if (!ok) {
                isRunning = false
                stopSelf()
            } else {
                isRunning = true
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundStarted() {
        try {
            NotificationHelper.startForeground(
                service = this,
                channelType = NotificationChannelType.SERVICE_RUNNING,
                title = getString(R.string.app_name),
                content = getString(R.string.toast_services_start),
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Proxy: Failed to enter foreground", e)
        }
    }

    /**
     * Destroys the service.
     */
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        CoreServiceManager.stopCoreLoop()
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    override fun getService(): Service {
        return this
    }

    /**
     * Starts the service.
     */
    override fun startService() {
        // do nothing
    }

    /**
     * Stops the service.
     */
    override fun stopService() {
        isRunning = false
        stopSelf()
    }

    override fun isServiceRunning(): Boolean {
        return isRunning
    }

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Attaches the base context to the service.
     * @param newBase The new base context.
     */
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
