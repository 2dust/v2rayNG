package com.v2ray.ang.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.engine.AppProcessFinder
import com.v2ray.ang.core.engine.CoreEngine
import com.v2ray.ang.core.engine.CoreEngineFactory
import com.v2ray.ang.core.engine.CoreEventHandler
import com.v2ray.ang.core.engine.CoreSelector
import com.v2ray.ang.core.engine.CoreType
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConfigResult
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.service.CoreProxyOnlyService
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.SoftReference
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object CoreServiceManager {

    private val coreEventHandler = CoreCallback()
    private var coreEngine: CoreEngine = CoreEngineFactory.create(CoreType.XRAY, coreEventHandler)
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null

    /**
     * Bumped when [stopCoreLoop] runs so an in-flight [startCoreLoop] (e.g. sing-box binary install)
     * can abort cleanly instead of racing the next session — common when toggling quickly on OEM ROMs.
     */
    private val coreLifecycleGeneration = AtomicInteger(0)

    private val coreLoopStartExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CoreLoopStart").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            coreEngine.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreEngine.registerProcessFinder(processFinder!!)
            }
        }

    /**
     * Starts the V2Ray service from a toggle action.
     * @param context The context from which the service is started.
     * @return True if the service was started successfully, false otherwise.
     */
    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        startContextService(context)
        return true
    }

    /**
     * Starts the V2Ray service.
     * @param context The context from which the service is started.
     * @param guid The GUID of the server configuration to use (optional).
     */
    fun startVService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: startVService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        startContextService(context)
    }

    /**
     * Stops the V2Ray service.
     * @param context The context from which the service is stopped.
     */
    fun stopVService(context: Context) {
        //context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning(): Boolean {
        return coreEngine.isRunning || (serviceControl?.get()?.isServiceRunning() == true)
    }


    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Starts the context service for V2Ray.
     * Chooses between VPN service or Proxy-only service based on user settings.
     * @param context The context from which the service is started.
     */
    private fun startContextService(context: Context) {
        if (coreEngine.isRunning) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return
        }

        val guid = resolveLaunchServerGuid()
        if (guid == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: No server selected")
            context.toast(R.string.app_tile_first_use)
            return
        }

        val config = MmkvManager.decodeServerConfig(guid)
        if (config == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to decode server config")
            context.toast(R.string.toast_services_failure)
            return
        }

        if (!ensureCoreEngine(config)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to resolve core engine")
            return
        }

        if (config.configType != EConfigType.CUSTOM
            && config.configType != EConfigType.POLICYGROUP
            && config.configType != EConfigType.PROXYCHAIN
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Invalid server configuration")
            return
        }
        // refresh socks port when enabled dynamic socks port
        SettingsManager.refreshRuntimeSocksPort()

//        val result = V2rayConfigUtil.getV2rayConfig(context, guid)
//        if (!result.status) return

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }

        val isVpnMode = SettingsManager.isVpnMode()
        val intent = if (isVpnMode) {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to start service", e)
            context.toast(R.string.toast_services_failure)
        }
    }

    private fun resolveLaunchServerGuid(): String? {
        val selectedGuid = MmkvManager.getSelectServer()
        if (!selectedGuid.isNullOrBlank() && MmkvManager.decodeServerConfig(selectedGuid) != null) {
            return selectedGuid
        }

        val fallbackGuid = MmkvManager.decodeAllServerList()
            .firstOrNull { guid -> MmkvManager.decodeServerConfig(guid) != null }

        if (!fallbackGuid.isNullOrBlank()) {
            MmkvManager.setSelectServer(fallbackGuid)
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Recovered missing selected server with fallback guid=$fallbackGuid")
        }

        return fallbackGuid
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoopAsync(vpnInterface: ParcelFileDescriptor?, callback: (Boolean) -> Unit) {
        coreLoopStartExecutor.execute {
            val result = try {
                startCoreLoop(vpnInterface)
            } catch (t: Throwable) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Unexpected failure starting core loop", t)
                false
            }
            mainHandler.post { callback(result) }
        }
    }

    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        val generationAtStart = coreLifecycleGeneration.get()

        if (coreEngine.isRunning) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        val guid = MmkvManager.getSelectServer()
        if (guid == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: No server selected")
            return false
        }

        val config = MmkvManager.decodeServerConfig(guid)
        if (config == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to decode server config")
            return false
        }

        if (!ensureCoreEngine(config)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to resolve core engine")
            return false
        }

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = buildRuntimeConfig(service, guid, config)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            return failStart(service, "StartCore-Manager: Failed to build runtime config for ${coreEngine.type}")
        }

        if (abortStartIfStale(service, generationAtStart, "after config build")) {
            return false
        }

        try {
            val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
            mFilter.addAction(Intent.ACTION_SCREEN_ON)
            mFilter.addAction(Intent.ACTION_SCREEN_OFF)
            mFilter.addAction(Intent.ACTION_USER_PRESENT)
            ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())
        } catch (e: Exception) {
            return failStart(service, "StartCore-Manager: Failed to register receiver", e)
        }

        if (abortStartIfStale(service, generationAtStart, "after registerReceiver")) {
            return false
        }

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        try {
            NotificationManager.showNotification(currentConfig)
            coreEngine.startLoop(result.content, tunFd)
        } catch (e: Exception) {
            return failStart(service, "StartCore-Manager: Failed to start core loop", e)
        }

        if (abortStartIfStale(service, generationAtStart, "after startLoop")) {
            runCatching { coreEngine.stopLoop() }
            return false
        }

        if (coreEngine.isRunning == false) {
            return failStart(service, "StartCore-Manager: Core failed to start")
        }

        try {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
            NotificationManager.startSpeedNotification(currentConfig)
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
        } catch (e: Exception) {
            return failStart(service, "StartCore-Manager: Failed to complete startup", e)
        }
        return true
    }

    private fun abortStartIfStale(service: Service, generationAtStart: Int, phase: String): Boolean {
        if (generationAtStart == coreLifecycleGeneration.get()) {
            return false
        }
        LogUtil.w(AppConfig.TAG, "StartCore-Manager: Aborted start ($phase) — lifecycle generation changed")
        runCatching {
            service.unregisterReceiver(mMsgReceive)
        }
        NotificationManager.cancelNotification()
        currentConfig = null
        return true
    }

    private fun failStart(service: Service, message: String, error: Exception? = null): Boolean {
        if (error == null) {
            LogUtil.e(AppConfig.TAG, message)
        } else {
            LogUtil.e(AppConfig.TAG, message, error)
        }
        runCatching {
            service.unregisterReceiver(mMsgReceive)
        }
        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, error?.message ?: message)
        NotificationManager.cancelNotification()
        return false
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        coreLifecycleGeneration.incrementAndGet()

        val service = getService() ?: return false

        if (coreEngine.isRunning) {
            try {
                runBlocking(Dispatchers.IO) {
                    coreEngine.stopLoop()
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop core loop", e)
            }
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Queries the statistics for a given tag and link.
     * @param tag The tag to query.
     * @param link The link to query.
     * @return The statistics value.
     */
    fun queryStats(tag: String, link: String): Long {
        return coreEngine.queryStats(tag, link)
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay() {
        if (coreEngine.isRunning == false) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""

            try {
                time = coreEngine.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
            }
            if (time == -1L) {
                try {
                    time = coreEngine.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }
            if (time == -1L && errorStr.isBlank()) {
                errorStr = service.getString(R.string.toast_failure)
            }

            val result = if (time >= 0) {
                service.getString(R.string.connection_test_available, time)
            } else {
                service.getString(R.string.connection_test_error, errorStr)
            }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, result)

            // Only fetch IP info if the delay test was successful
            if (time >= 0) {
                SpeedtestManager.getRemoteIPInfo()?.let { ip ->
                    MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, "$result\n$ip")
                }
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

    private fun ensureCoreEngine(config: ProfileItem): Boolean {
        val resolvedCore = CoreSelector.resolve(config)
        if (coreEngine.type == resolvedCore) {
            return true
        }
        if (coreEngine.isRunning) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Cannot switch core while running")
            return false
        }

        return try {
            coreEngine = CoreEngineFactory.create(resolvedCore, coreEventHandler)
            val service = getService()
            coreEngine.initCoreEnv(service)
            processFinder?.let { coreEngine.registerProcessFinder(it) }
            true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to create core engine $resolvedCore", e)
            false
        }
    }

    private fun buildRuntimeConfig(service: Service, guid: String, config: ProfileItem): ConfigResult {
        return when (coreEngine.type) {
            CoreType.XRAY -> CoreConfigManager.getV2rayConfig(service, guid)
            CoreType.SING_BOX -> buildSingBoxRuntimeConfig(guid, config)
        }
    }

    private fun buildSingBoxRuntimeConfig(guid: String, config: ProfileItem): ConfigResult {
        if (!CoreSelector.isExplicitSingBoxTestProfile(config)) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Sing-box runtime is currently limited to explicitly marked CUSTOM profiles")
            return ConfigResult(false)
        }

        val raw = MmkvManager.decodeServerRaw(guid)
        if (raw.isNullOrBlank()) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Missing raw sing-box config for $guid")
            return ConfigResult(false)
        }

        return ConfigResult(true, guid, CoreConfigManager.normalizeSingBoxCustomConfig(raw))
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreEventHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop service", e)
                -1
            }
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(statusCode: Long, message: String?): Long {
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : AppProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
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
            } catch (e: Exception) {
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
                    if (isRunning()) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
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
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    startVService(serviceControl.getService())
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification(currentConfig)
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification(currentConfig)
                }
            }
        }
    }
}
