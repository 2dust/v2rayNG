package com.dalulong.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.dalulong.app.AppConfig
import com.dalulong.app.contracts.ServiceControl
import com.dalulong.app.core.CoreServiceManager
import com.dalulong.app.handler.SettingsManager
import com.dalulong.app.util.LogUtil
import com.dalulong.app.util.MyContextWrapper
import java.lang.ref.SoftReference

class CoreProxyOnlyService : Service(), ServiceControl {
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
        CoreServiceManager.startCoreLoop(null)
        return START_STICKY
    }

    /**
     * Destroys the service.
     */
    override fun onDestroy() {
        super.onDestroy()
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
        stopSelf()
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
