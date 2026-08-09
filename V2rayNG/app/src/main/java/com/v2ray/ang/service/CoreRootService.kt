package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.root.RootProxyManager
import com.v2ray.ang.root.RootTproxyManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.SoftReference

/**
 * Foreground service for the root (system-wide) run modes. Unlike [CoreVpnService] it
 * does not use Android VpnService — traffic is routed by iptables instead
 * (see [RootProxyManager]).
 *
 * The in-process core is started first (so its listener is up and the foreground
 * notification is posted promptly), then the root routing rules are installed off the
 * main thread. On teardown the rules are removed before the core stops.
 */
class CoreRootService : Service(), ServiceControl {

    private enum class Engine { NONE, TUN2SOCKS, TPROXY }

    private var setupJob: Job? = null

    @Volatile
    private var engineStarted = Engine.NONE

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationManager.ensureForeground()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: command received")

        if (CoreServiceManager.isRunning()) {
            LogUtil.i(AppConfig.TAG, "StartCore-Root: Core is already running")
            return START_STICKY
        }

        // Start the in-process core first (this also posts the foreground notification),
        // then install the root routing off the main thread.
        if (!CoreServiceManager.startCoreLoop(null)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Root: core failed to start")
            stopService()
            return START_NOT_STICKY
        }

        setupJob = CoroutineScope(Dispatchers.IO).launch {
            if (!startRouting()) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: failed to start root mode, stopping")
                stopService()
            }
        }

        return START_STICKY
    }

    /**
     * Install the routing rules for the selected root engine.
     *
     * When TPROXY is selected but its setup fails (missing xt_TPROXY, no usable anti-loop
     * match, helper won't start), fall back to the tun2socks engine rather than leaving the
     * user with a dead connection: TPROXY's own teardown has already run, so no stale rule
     * survives into the fallback. [engineStarted] records which engine actually took, so
     * teardown removes the right one.
     */
    private fun startRouting(): Boolean {
        if (SettingsManager.isRootTproxyMode()) {
            if (RootTproxyManager.start(this)) {
                engineStarted = Engine.TPROXY
                return true
            }
            LogUtil.w(AppConfig.TAG, "StartCore-Root: TPROXY setup failed, falling back to tun2socks")
        }
        if (RootProxyManager.start(this)) {
            engineStarted = Engine.TUN2SOCKS
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Wait for any in-flight async setup to finish before tearing down. The rules are
        // installed off the main thread and can take seconds (the setup script waits for the
        // tun to appear); if a stop arrives during that window, teardown would run first and
        // the setup would then re-install the rules + tun pointing at a now-dead core,
        // blackholing all traffic until the next start/stop cycle clears it.
        runBlocking { setupJob?.cancelAndJoin() }
        // Remove routing rules BEFORE stopping the core so traffic is never redirected
        // to a dead listener. Synchronous on purpose — leaving rules behind breaks the net.
        // When setup never reported an engine (cancelled mid-flight, or a TPROXY attempt that
        // failed before the fallback ran) both teardowns run: each is idempotent, and the cost
        // of skipping the wrong one is a device left without connectivity.
        when (engineStarted) {
            Engine.TPROXY -> RootTproxyManager.stop(this)
            Engine.TUN2SOCKS -> RootProxyManager.stop(this)
            Engine.NONE -> {
                RootTproxyManager.stop(this)
                RootProxyManager.stop(this)
            }
        }
        engineStarted = Engine.NONE
        CoreServiceManager.stopCoreLoop()
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        stopSelf()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let(AppLocaleManager::localizedContext)
        super.attachBaseContext(context)
    }
}
