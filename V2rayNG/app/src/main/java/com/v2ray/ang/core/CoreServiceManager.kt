package com.v2ray.ang.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.IDialerService
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.SubscriptionUpdateMessage
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BrowserDialerMode
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress

object CoreServiceManager {

    private const val AETHER_WARM_UP_MS = 30_000L

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null

    /** The Aether core the running configuration depends on, null when it has no Aether outbound. */
    private var currentAether: AetherCore? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null

    /** Written on the main thread and read by the reload thread, which tells a stop by it. */
    @Volatile
    private var networkMonitor: NetworkMonitor? = null
    private val connectionTestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Owns the Aether warm-up wait; cancelled on every start and stop so a stale wait cannot report. */
    private val aetherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var aetherWarmUpJob: Job? = null

    /** Set once an Aether exit has stopped the service, so a second report of the same exit is a no-op. */
    @Volatile
    private var aetherExitHandled = false

    @Volatile
    private var isReloading = false

    /** Tun descriptor the core was started with, null in the proxy only and root run modes. */
    private var currentVpnInterface: ParcelFileDescriptor? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Whether the service is up; see [ReloadOutcome.serviceRuns]. [isRunning] is Xray alone, which a
     * reload has stopped for a moment. What answers a state query, or decides between a start and a
     * stop, goes by this one.
     */
    fun isServiceRunning() = ReloadOutcome.serviceRuns(
        coreRunning = isRunning(),
        reloading = isReloading,
        stoppedMeanwhile = networkMonitor == null,
    )

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, userFacingReason(e))
            NotificationManager.cancelNotification()
            return false
        }
    }

    /**
     * A start or reload failure whose message is a localized resource string, meant for the main
     * screen. Every other failure reaches the UI without a reason: its message is technical and
     * belongs in the log, and the UI shows only resource text.
     */
    private class StartFailure(message: String) : RuntimeException(message)

    private fun userFacingReason(e: Exception): String = if (e is StartFailure) e.message.orEmpty() else ""

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?, isReload: Boolean = false) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            if (result.localizedError) throw StartFailure(result.errorMessage)
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        cancelAetherWarmUp()
        // Starting a configuration ends the config tests, whatever the configuration: that is what a start
        // means to the user, and the tests spawn Aether cores of their own, which a session's core must not
        // come up beside, on the same key or with Psiphon on the same datastore. The same goes for the test
        // phase of subscription updates, whose downloads go on. A reload keeps the session's place and
        // leaves the tests alone.
        if (!isReload) {
            MessageHelper.sendMsg2TestService(service, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL))
            MessageHelper.sendMsg2SubscriptionService(
                service,
                SubscriptionUpdateMessage(AppConfig.MSG_SUB_UPDATE_CANCEL_TEST, forcedUpdate = false)
            )
        }
        // One core serves every Aether outbound of the configuration: the selected profile itself, a hop
        // of its chain, a routing target, a policy-group member, or the SOCKS outbounds of a custom
        // configuration that asks for it with aetherCommand. It listens on the port its arguments name.
        val aether = result.aetherCore
        if (aether != null) {
            if (!AetherCoreManager.isSupported(service)) {
                throw StartFailure(service.getString(R.string.aether_unsupported_abi))
            }
            // Xray would take the port first, and the Aether outbound would dial the configuration's own inbound.
            val aetherPort = aether.ports.firstOrNull { AetherDependency.inboundListensOn(result.content, it) }
            if (aetherPort != null) {
                LogUtil.w(
                    AppConfig.TAG,
                    "StartCore-Manager: ${service.javaClass.simpleName} ${if (isReload) "reload" else "start"} refused, " +
                        "an inbound of the configuration listens on the Aether port $aetherPort, guid=$guid"
                )
                // A reload still has the previous session's core; without Xray it serves nothing.
                AetherCoreManager.stop()
                throw StartFailure(service.getString(R.string.aether_listen_port_taken))
            }
        }
        // A reload still has the previous session's core; it ends before Xray starts again. The new core,
        // if any, starts once Xray listens, see launchNativeCore.
        AetherCoreManager.stop()

        try {
            launchNativeCore(service, guid, config, aether, result.content, vpnInterface, isReload)
        } catch (e: Exception) {
            // Setup failed after this attempt spawned the Aether process; release it with the rest.
            AetherCoreManager.stop()
            throw e
        }
    }

    @Throws(Exception::class)
    private fun launchNativeCore(
        service: Service,
        guid: String,
        config: ProfileItem,
        aether: AetherCore?,
        content: String,
        vpnInterface: ParcelFileDescriptor?,
        isReload: Boolean,
    ) {
        currentConfig = config
        currentAether = aether
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }
        coreController.startLoop(content, tunFd)

        if (!isRunning()) {
            error("Core failed to start")
        }

        // The Aether core dials out through an inbound of Xray, which listens once the start returns, so
        // the core starts after it rather than spending its first dials on a port nobody listens on yet.
        if (aether != null) {
            // The tests were told to stop as this start began; the session's core waits for their cores to be gone.
            aetherExitHandled = false
            AetherCoreManager.start(service, aether, afterProbes = !isReload) { onAetherExit(guid) }
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        if (aether != null) {
            announceAetherWarmUp(service, guid, isReload)
        } else if (!isReload) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Xray is up as soon as it starts, but an Aether profile carries no traffic until the Aether
     * process has scanned and connected, which can take minutes. Clients are told the service is
     * running right away, the main screen and the notification show the connecting state, and the
     * start-success signal follows once the Aether SOCKS listener accepts connections.
     */
    private fun announceAetherWarmUp(service: Service, guid: String, isReload: Boolean) {
        val connecting = service.getString(R.string.aether_core_connecting)
        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_RUNNING, "")
        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_CONNECTING, connecting)
        NotificationManager.setStatusLine(connecting)
        aetherWarmUpJob = aetherScope.launch {
            var listening = false
            while (isActive && !listening && AetherCoreManager.isRunning) {
                listening = AetherCoreManager.awaitListening(AETHER_WARM_UP_MS)
            }
            when (AetherCoreManager.warmUpOutcome(listening, isActive, isRunning())) {
                AetherCoreManager.WarmUpOutcome.ABANDONED -> Unit
                // The core's exit callback reports it as well; the service stops on whichever comes first.
                AetherCoreManager.WarmUpOutcome.CORE_EXITED -> onAetherExit(guid)
                AetherCoreManager.WarmUpOutcome.LISTENING -> {
                    NotificationManager.setStatusLine(null)
                    val ready = if (isReload) AppConfig.MSG_STATE_RUNNING else AppConfig.MSG_STATE_START_SUCCESS
                    MessageHelper.sendMsg2UI(service, ready, "")
                }
            }
        }
    }

    private fun isAetherWarmingUp(): Boolean = aetherWarmUpJob?.isActive == true

    private fun cancelAetherWarmUp() {
        aetherWarmUpJob?.cancel()
        aetherWarmUpJob = null
        NotificationManager.setStatusLine(null)
    }

    /**
     * Stops the service once the Aether core is gone while Xray still runs. Reached from the
     * core's exit callback and from the warm-up wait, so the stop happens once per session.
     */
    private fun onAetherExit(guid: String) {
        val control = serviceControl?.get() ?: return
        val service = control.getService()
        ContextCompat.getMainExecutor(service).execute {
            if (aetherExitHandled || AetherCoreManager.isRunning || !isRunning() || serviceControl?.get() !== control) return@execute
            aetherExitHandled = true
            LogUtil.e(
                AppConfig.TAG,
                "StartCore-Manager: Aether core exited while running, stopping ${service.javaClass.simpleName}, guid=$guid"
            )
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, service.getString(AetherCoreManager.stoppedMessage()))
            control.stopService()
        }
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        connectionTestScope.coroutineContext.cancelChildren()
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null
        // First, so that nothing further down posts the notification again: the proxy-only and the
        // root service tear down in onDestroy, out of the foreground already, where a post outlives them.
        NotificationManager.cancelNotification()
        cancelAetherWarmUp()
        AetherCoreManager.stop()

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
    private fun startNetworkMonitor(service: Service) {
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks -> serviceControl?.get()?.setUnderlyingNetworks(networks) },
            onHandover = { reloadCore() },
        ).also { it.register() }
    }

    /**
     * Restarts the core in place after the upstream network changed: the service, the notification
     * and the VPN interface all stay up, so nothing of this is visible.
     *
     * The config is rebuilt on purpose, outbound server domains are resolved while building it and
     * an address resolved on a network that is gone can be unusable on the new one.
     *
     * @return True if the core is running again.
     */
    private fun reloadCore(): Boolean {
        if (isReloading) return false
        val service = getService() ?: return false
        if (!isRunning()) return false
        // Only a monitor asks for a reload, and a stop clears it: without one the service is going down
        // already, and a stop that arrives during the reload is told by it afterwards.
        val monitor = networkMonitor ?: return false

        try {
            val tunFd = currentVpnInterface

            isReloading = true
            connectionTestScope.coroutineContext.cancelChildren()
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")

            coreController.stopLoop()
            launchCore(service, tunFd, isReload = true)

            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload finished")
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to reload core: $message", e)
            // After a stop the screen has been told of it, and after a new start its state is that start's to report.
            if (networkMonitor === monitor) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, userFacingReason(e))
            }
        } finally {
            isReloading = false
        }

        // No monitor at all means stopped and not started again; a new one belongs to a new start, which owns the cores.
        return when (ReloadOutcome.of(coreRunning = isRunning(), stoppedMeanwhile = networkMonitor == null)) {
            ReloadOutcome.KEEP_RUNNING -> true
            ReloadOutcome.STOP_SERVICE -> {
                stopServiceAfterFailedReload(monitor)
                false
            }

            ReloadOutcome.RELEASE_CORES -> {
                releaseAfterStoppedReload(service)
                false
            }
        }
    }

    /**
     * Stops the service once a reload has left Xray stopped; see [ReloadOutcome.STOP_SERVICE]. The
     * reload runs on a background thread, the stop goes through the service on the main thread.
     */
    private fun stopServiceAfterFailedReload(monitor: NetworkMonitor) {
        val control = serviceControl?.get() ?: return
        val service = control.getService()
        ContextCompat.getMainExecutor(service).execute {
            // A stop or a new start that arrived meanwhile has taken over.
            if (isRunning() || networkMonitor !== monitor || serviceControl?.get() !== control) return@execute
            LogUtil.e(
                AppConfig.TAG,
                "StartCore-Manager: reload left no core running, stopping ${service.javaClass.simpleName}, guid=${MmkvManager.getSelectServer()}"
            )
            control.stopService()
        }
    }

    /**
     * Releases what a reload started for a service that was stopped while it ran; see
     * [ReloadOutcome.RELEASE_CORES]. The teardown of that service has done the rest already.
     *
     * It runs on the main thread, where a service starts as well: a start that followed the stop,
     * such as the second half of a restart, is then either over, and what runs is its own, or has
     * not begun, and finds nothing left running. For the same reason Xray is stopped in place here
     * rather than in the background: a start that begins next must not meet the core of the reload.
     */
    private fun releaseAfterStoppedReload(service: Service) {
        ContextCompat.getMainExecutor(service).execute {
            if (networkMonitor != null) return@execute
            LogUtil.w(
                AppConfig.TAG,
                "StartCore-Manager: ${service.javaClass.simpleName} was stopped during a reload, releasing the cores the reload started, " +
                    "guid=${MmkvManager.getSelectServer()}"
            )
            NotificationManager.cancelNotification()
            cancelAetherWarmUp()
            AetherCoreManager.stop()
            try {
                coreController.stopLoop()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop the core a stopped reload started", e)
            }
            CoreNativeManager.reconcileBrowserDialer("")
            browserDialer?.stop()
            browserDialer = null
            // The reload may have announced a running or connecting service after the stop was reported.
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        }
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay(requestId: String) {
        val service = getService() ?: return
        if (!isRunning() || isReloading) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            return
        }

        connectionTestScope.coroutineContext.cancelChildren()
        connectionTestScope.launch {
            // The same budget every other profile's probe gets; a tunnel still scanning past it is reported, not waited for.
            if (currentAether != null && !AetherCoreManager.awaitListening(AetherDelayTester.TEST_BUDGET_MS)) {
                val reason = if (AetherCoreManager.isRunning) R.string.aether_core_connecting else R.string.aether_core_stopped
                val stalled = ConnectionTestResult(delayMillis = -1L, errorMessage = service.getString(reason))
                withContext(Dispatchers.Main.immediate) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, stalled, requestId)
                }
                return@launch
            }

            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":").orEmpty()
            }
            if (time == -1L) {
                ensureActive()
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":").orEmpty()
                }
            }

            ensureActive()
            val endpoint = if (time >= 0) SpeedtestManager.getRemoteIPInfo() else null
            val result = ConnectionTestResult(
                delayMillis = time,
                errorMessage = errorStr,
                country = endpoint?.country,
                ipAddress = endpoint?.ipAddress,
            )
            withContext(Dispatchers.Main.immediate) {
                if (isRunning()) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, result, requestId)
                } else {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
                }
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            }
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback startup")
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback shutdown")
            return 0
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback onEmitStatus $s")
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    // A client that gets no acknowledgement takes the service for gone: the daemon
                    // cannot report its own death, so silence is the only sign of it.
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    if (isServiceRunning()) {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                        if (isAetherWarmingUp()) {
                            val service = serviceControl.getService()
                            MessageHelper.sendMsg2UI(
                                service,
                                AppConfig.MSG_STATE_CONNECTING,
                                service.getString(R.string.aether_core_connecting)
                            )
                        }
                    } else {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    // The UI and daemon run in separate processes, so acknowledge the active
                    // daemon before stopping it instead of relying on possibly stale UI state.
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            serviceControl.stopService()
                            delay(500L)
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    measureV2rayDelay(intent.getStringExtra("content").orEmpty())
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification()
                }
            }
        }
    }
}
