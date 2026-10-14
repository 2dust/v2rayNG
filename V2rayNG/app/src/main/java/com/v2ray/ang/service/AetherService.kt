package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.core.AetherSession
import com.v2ray.ang.core.AetherLifecycle
import com.v2ray.ang.core.AetherStartupException
import com.v2ray.ang.core.AetherStartupStage
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.ref.SoftReference
import java.io.File

/** Aether owns its child process; Xray remains the daemon's tunnel/routing authority. */
class AetherService : VpnService(), ServiceControl {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("AetherService"))
    private val lifecycle = AetherLifecycle()
    private var setupJob: Job? = null
    private var watchdog: Job? = null
    private var cleanupJob: Job? = null
    private var session: AetherSession? = null
    private var vpn: ParcelFileDescriptor? = null
    private var hev: TProxyService? = null
    @Volatile
    private var ownsCore = false
    private var guid = ""
    private var mode = ""
    @Volatile
    private var startupSettings: com.v2ray.ang.dto.entities.AetherSettings? = null
    @Volatile
    private var internalProxyPort: Int? = null
    private var receiverRegistered = false
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Once Xray owns the command receiver, it delegates Aether commands back here.
            if (ownsCore) return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_STOP -> stopService()
                AppConfig.MSG_STATE_RESTART -> {
                    restartOwnedService()
                    if (isOrderedBroadcast) resultCode = android.app.Activity.RESULT_OK
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(this, NotificationChannelType.AETHER,
            getString(R.string.aether_title), getString(R.string.aether_starting))
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, commandReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE), Utils.receiverFlags())
            receiverRegistered = true
        }
        if (!lifecycle.canStart(setupJob?.isActive == true, ownsCore)) return START_NOT_STICKY
        setupJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            var failure = R.string.aether_start_error
            try {
                // API 26 adds the process APIs used to guarantee bounded child-process cleanup.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    failure = R.string.aether_unavailable
                    error("Unsupported Android version")
                }
                // Release binaries have not been validated on devices with 16 KiB pages.
                if (Os.sysconf(OsConstants._SC_PAGESIZE) != 4096L) {
                    failure = R.string.aether_unavailable
                    error("Unsupported page size")
                }
                check(!CoreServiceManager.isRunning()) { "Another core is already running" }
                guid = MmkvManager.getSelectServer().orEmpty()
                mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE).orEmpty()
                if (SettingsManager.isRootMode()) {
                    failure = R.string.aether_root_unsupported
                    error("Aether root mode is unsupported")
                }
                if (!File(applicationInfo.nativeLibraryDir, "libaether-core.so").isFile ||
                    !File(applicationInfo.nativeLibraryDir, "libaether-launcher.so").isFile) {
                    failure = R.string.aether_unavailable
                    error("Aether core is not packaged for this ABI")
                }
                val profile = MmkvManager.decodeServerConfig(guid)
                    ?.takeIf { it.configType == EConfigType.AETHER } ?: error("Invalid selected profile")
                val settings = profile.aetherSettings ?: com.v2ray.ang.dto.entities.AetherSettings()
                failure = R.string.aether_invalid_settings
                check(settings.isValid())
                CoreServiceManager.serviceControl = SoftReference(this@AetherService)
                session = AetherSession()
                failure = R.string.aether_start_error
                internalProxyPort = session!!.start(this@AetherService, guid, settings,
                    setOf(SettingsManager.getSocksPort(), SettingsManager.getHttpPort()), serviceScope, mode)
                startupSettings = settings
                if (SettingsManager.isVpnMode()) {
                    failure = R.string.aether_permission_error
                    check(prepare(this@AetherService) == null) { "VPN permission missing" }
                    failure = R.string.aether_vpn_error
                    vpn = createInterface(profile.remarks)
                }
                ownsCore = true
                failure = R.string.aether_routing_error
                check(CoreServiceManager.startCoreLoop(vpn, notifyStarted = false))
                if (vpn != null && SettingsManager.isUsingHevTun()) {
                    failure = R.string.aether_tunnel_error
                    // Acquire the tunnel owner before startup so failed setup still tears it down.
                    hev = TProxyService(this@AetherService, vpn!!, { CoreServiceManager.isRunning() }, {})
                    hev!!.startTun2Socks()
                    check(hev!!.isTunnelRunning()) { "HEV tunnel failed" }
                }
                currentCoroutineContext().ensureActive()
                MessageHelper.sendMsg2UI(this@AetherService, AppConfig.MSG_STATE_START_SUCCESS, "")
                watchdog = serviceScope.launch {
                    while (isActive && !lifecycle.isStopping()) {
                        delay(1_000)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && session?.isAlive() != true) {
                            LogUtil.e(AppConfig.TAG, "Aether: mode=$mode guid=$guid child process exited", IllegalStateException("Core exited"))
                            MessageHelper.sendMsg2UI(this@AetherService, AppConfig.MSG_STATE_START_FAILURE, getString(R.string.aether_start_error))
                            stopService()
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Aether: mode=$mode guid=$guid startup failed", e)
                val message = if (e is AetherStartupException) {
                    val stageName = getString(when (e.stage) {
                        AetherStartupStage.CORE -> R.string.aether_stage_core
                        AetherStartupStage.REGISTRATION -> R.string.aether_stage_registration
                        AetherStartupStage.SCAN -> R.string.aether_stage_scan
                        AetherStartupStage.TRANSPORT -> R.string.aether_stage_transport
                    })
                    e.exitCode?.let { getString(R.string.aether_native_error, stageName, it) }
                        ?: getString(R.string.aether_timeout_error, stageName)
                } else getString(failure)
                MessageHelper.sendMsg2UI(this@AetherService, AppConfig.MSG_STATE_START_FAILURE, message)
                stopService()
            }
        }
        setupJob?.start()
        // Recreate resources on the Android-owned null-intent/SERVICE_INTERFACE start paths.
        return START_NOT_STICKY
    }

    private fun createInterface(name: String): ParcelFileDescriptor {
        val address = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val builder = Builder().setSession(name).setMtu(SettingsManager.getVpnMtu())
            .addAddress(address.ipv4Client, 30)
        if (SettingsManager.routingRulesetsBypassLan()) {
            AppConfig.ROUTED_IP_LIST.forEach { route ->
                val pieces = route.split('/')
                builder.addRoute(pieces[0], pieces[1].toInt())
            }
        } else builder.addRoute("0.0.0.0", 0)
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)) {
            builder.addAddress(address.ipv6Client, 126)
            if (SettingsManager.routingRulesetsBypassLan()) {
                builder.addRoute("2000::", 3).addRoute("fc00::", 18)
            } else builder.addRoute("::", 0)
        }
        SettingsManager.getVpnDnsServers().forEach { builder.addDnsServer(it) }
        val selected = if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY))
            MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET).orEmpty().toSet() else emptySet()
        if (selected.isEmpty() || MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)) {
            (selected + packageName).forEach { builder.addDisallowedApplication(it) }
        } else {
            val allowed = selected - packageName
            check(allowed.isNotEmpty()) { "No applications selected for VPN" }
            allowed.forEach { builder.addAllowedApplication(it) }
        }
        return builder.establish() ?: error("VPN interface unavailable")
    }

    override fun stopService() {
        if (!lifecycle.beginStop()) return
        cleanupJob = serviceScope.launch {
            setupJob?.cancelAndJoin()
            withContext(NonCancellable) { cleanup() }
            stopSelf()
        }
    }

    fun hasReadyProfile(profileGuid: String, settings: com.v2ray.ang.dto.entities.AetherSettings?): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !lifecycle.isStopping() && profileGuid == guid &&
            (settings ?: com.v2ray.ang.dto.entities.AetherSettings()) == startupSettings && session?.isAlive() == true

    override fun aetherProxyPort(guid: String): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            lifecycle.readyPort(guid, this.guid, session?.isAlive() == true, internalProxyPort) else null

    override fun restartOwnedService(): Boolean {
        if (!lifecycle.beginRestart()) return true
        cleanupJob = serviceScope.launch {
            setupJob?.cancelAndJoin()
            val released = withContext(NonCancellable) { cleanup() }
            if (!released) {
                lifecycle.beginStop()
                stopSelf()
                return@launch
            }
            if (isActive) {
                try {
                    val restarted = lifecycle.completeRestart { LauncherManager.startService(this@AetherService) }
                    if (!restarted || MmkvManager.decodeServerConfig(MmkvManager.getSelectServer().orEmpty())?.configType != EConfigType.AETHER) stopSelf()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Aether: mode=$mode guid=$guid restart dispatch failed", e)
                    stopSelf()
                }
            }
        }
        return true
    }

    private suspend fun cleanup() = withContext(Dispatchers.IO) {
        var released = true
        suspend fun release(operation: String, action: suspend () -> Unit): Boolean {
            try {
                action()
                return true
            } catch (e: Exception) {
                released = false
                LogUtil.e(AppConfig.TAG, "Aether: mode=$mode guid=$guid cleanup $operation failed", e)
                return false
            }
        }
        watchdog?.cancel()
        watchdog = null
        if (release("HEV") { hev?.stopTun2Socks() }) hev = null
        if (ownsCore && CoreServiceManager.serviceControl?.get() === this@AetherService) {
            if (release("Xray") { check(CoreServiceManager.stopCoreLoop(awaitNativeStop = true)) }) ownsCore = false
        } else ownsCore = false
        if (release("VPN interface") { vpn?.close() }) vpn = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (release("child process") { session?.stop() }) session = null
        }
        startupSettings = null
        internalProxyPort = null
        released
    }

    override fun onRevoke() { stopService() }

    override fun onDestroy() {
        lifecycle.beginStop()
        setupJob?.cancel()
        // Await cleanup for at most 5 seconds: closing a listener before root/VPN teardown
        // could send traffic to a dead proxy. Native/file work runs on Dispatchers.IO.
        try {
            runBlocking { withTimeout(5_000) { withContext(Dispatchers.IO) { setupJob?.join(); cleanupJob?.join(); cleanup() } } }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Aether: mode=$mode guid=$guid destroy cleanup failed", e)
        } finally {
            serviceScope.cancel()
            if (receiverRegistered) {
                unregisterReceiver(commandReceiver)
                receiverRegistered = false
            }
            super.onDestroy()
        }
    }

    override fun getService(): Service = this
    override fun startService() = Unit
    override fun vpnProtect(socket: Int): Boolean = if (SettingsManager.isVpnMode()) protect(socket) else true
    override fun setUnderlyingNetworks(networks: Array<Network>?): Boolean =
        if (SettingsManager.isVpnMode()) super<VpnService>.setUnderlyingNetworks(networks) else false
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }
}
