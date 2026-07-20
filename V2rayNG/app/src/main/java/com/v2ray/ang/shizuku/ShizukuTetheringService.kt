package com.v2ray.ang.shizuku

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.service.TProxyService
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import rikka.shizuku.Shizuku
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Privileged Shizuku process that controls Android tethering and owns its proxy upstream.
 *
 * Android's TestNetworkManager creates a real kernel TUN without root when called by the shell
 * UID. TetheringManager is then told to prefer test networks, so Android's existing Wi-Fi/USB
 * DHCP, forwarding, NAT and DNS machinery sends client traffic into that TUN. The TUN is consumed
 * either by a process-local HEV instance (which connects to the normal v2rayNG SOCKS inbound) or
 * by a second Xray controller initialized with the exact running-core configuration.
 */
@Keep
class ShizukuTetheringService(context: Context) : IShizukuTetheringService.Stub() {

    private val executor = Executor { command -> command.run() }
    private val appContext = context
    private val shellContext = ShellContextCompat.create(context)
    private val tetheringManager = requireNotNull(
        shellContext.getSystemService(TETHERING_SERVICE)
    ) { "TetheringManager is unavailable" }
    private val connectivityManager = requireNotNull(
        shellContext.getSystemService(ConnectivityManager::class.java)
    ) { "ConnectivityManager is unavailable" }
    @Volatile
    private var routingState = ROUTING_STATE_DISABLED
    @Volatile
    private var routingDetail = ""
    private var routingUsesHev = false
    private var routingProfileName = ""
    private var routingSession: RoutingSession? = null
    private var testNetworkManager: Any? = null
    private var testNetworkInterface: Any? = null
    private var testTun: ParcelFileDescriptor? = null
    private var testNetwork: Network? = null
    private var testNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var testNetworkToken: IBinder? = null
    private var testInterfaceName: String? = null
    private var nativeController: CoreController? = null
    private var hevConfigFile: File? = null

    private data class RoutingSession(
        val token: String,
        val assetPath: String,
        val xudpKey: String,
        var desiredTetheringTypes: Int,
        var coreRestartPending: Boolean = false,
    )

    override fun getWifiHotspotState(): Int {
        val types = getActiveTetheringTypes()
        if (types < 0) return HOTSPOT_STATE_UNKNOWN
        return if (types and tetheringTypeBit(TETHERING_TYPE_WIFI) != 0) {
            HOTSPOT_STATE_ENABLED
        } else {
            HOTSPOT_STATE_DISABLED
        }
    }

    override fun setWifiHotspotEnabled(enabled: Boolean): Int = synchronized(this) {
        val result = setTetheringEnabled(TETHERING_TYPE_WIFI, enabled)
        if (result == RESULT_OK) {
            val bit = tetheringTypeBit(TETHERING_TYPE_WIFI)
            routingSession?.let { session ->
                session.desiredTetheringTypes = if (enabled) {
                    session.desiredTetheringTypes or bit
                } else {
                    session.desiredTetheringTypes and bit.inv()
                }
            }
        }
        result
    }

    override fun getActiveTetheringTypes(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= 36) {
                TetheringApi36.getActiveTetheringTypes(
                    tetheringManager,
                    executor,
                    CALLBACK_TIMEOUT_SECONDS,
                )
            } else {
                getLegacyActiveTetheringTypes()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to read tethering state", error)
            TETHERING_TYPES_UNKNOWN
        }
    }

    override fun getRoutingState(): Int {
        if (routingState == ROUTING_STATE_ACTIVE_NATIVE && nativeController?.isRunning != true) {
            routingState = ROUTING_STATE_ERROR
            routingDetail = "Native Xray core stopped unexpectedly"
        }
        return routingState
    }

    override fun getRoutingDetail(): String = routingDetail

    override fun startRouting(
        useHev: Boolean,
        profileName: String,
        coreConfig: String,
        hevConfig: String,
        assetPath: String,
        xudpKey: String,
        syncToken: String,
    ): Int = synchronized(this) {
        if (syncToken.isBlank()) {
            routingDetail = "Tethering synchronization token is empty"
            return@synchronized RESULT_INVALID_SESSION
        }
        val activeTypes = getActiveTetheringTypes()
        val launchConfig = HotspotRoutingLaunchConfig(
            useHev = useHev,
            profileName = profileName,
            coreConfig = coreConfig,
            hevConfig = hevConfig,
            assetPath = assetPath,
            xudpKey = xudpKey,
        )
        val newSession = RoutingSession(
            token = syncToken,
            assetPath = launchConfig.assetPath,
            xudpKey = launchConfig.xudpKey,
            desiredTetheringTypes = activeTypes.takeIf { it >= 0 }
                ?: routingSession?.desiredTetheringTypes
                ?: 0,
        )

        val result = startRoutingLocked(launchConfig)
        if (result == RESULT_OK) routingSession = newSession
        result
    }

    private fun startRoutingLocked(config: HotspotRoutingLaunchConfig): Int {
        if (routingState == ROUTING_STATE_ACTIVE_HEV || routingState == ROUTING_STATE_ACTIVE_NATIVE) {
            return if (routingUsesHev == config.useHev) {
                routingDetail = "Tethering routing is already active"
                RESULT_ALREADY_ACTIVE
            } else {
                RESULT_IMPLEMENTATION_MISMATCH
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            routingState = ROUTING_STATE_ERROR
            routingDetail = "Shizuku tethering requires Android 11 or newer"
            return RESULT_ROUTING_FAILED
        }

        if (testTun != null) {
            return try {
                val restoreTypes = getActiveTetheringTypes()
                check(restoreTypes >= 0) {
                    "Unable to determine active tethering before rebuilding its protected route"
                }
                val failedTypes = rebuildRoutingLocked(config, restoreTypes)
                reportTetheringRestoreFailuresLocked(failedTypes)
                RESULT_OK
            } catch (error: Throwable) {
                val detail = rootCauseMessage(error)
                Log.e(TAG, "Unable to rebuild v2rayNG tethering routing: $detail", error)
                routingState = ROUTING_STATE_ERROR
                routingDetail = detail
                RESULT_ROUTING_FAILED
            }
        }

        return startRoutingOnNewTestNetworkLocked(config)
    }

    private fun startRoutingOnNewTestNetworkLocked(config: HotspotRoutingLaunchConfig): Int {
        cleanupRouting(resetPreference = true)
        routingState = ROUTING_STATE_STARTING
        routingDetail = "Creating Android test-network TUN"

        return try {
            setPreferTestNetworks(true)
            createTestNetwork()
            val tun = checkNotNull(testTun) { "Test TUN file descriptor is unavailable" }
            startRoutingEngineLocked(config, tun.fd)
            setRoutingActiveLocked(config)
            RESULT_OK
        } catch (error: Throwable) {
            val detail = rootCauseMessage(error)
            Log.e(TAG, "Unable to start v2rayNG tethering routing: $detail", error)
            cleanupRouting(resetPreference = true)
            routingState = ROUTING_STATE_ERROR
            routingDetail = detail
            RESULT_ROUTING_FAILED
        }
    }

    override fun stopRouting(): Int = synchronized(this) {
        shutdownRoutingLocked()
    }

    private fun shutdownRoutingLocked(): Int {
        val tetheringResult = stopActiveTetheringLocked(clearDesired = true)
        if (tetheringResult != RESULT_OK) {
            routingState = ROUTING_STATE_ERROR
            routingDetail = "Unable to disable tethering safely before removing its protected route"
            return tetheringResult
        }
        routingSession = null
        routingState = ROUTING_STATE_STOPPING
        routingDetail = "Stopping v2rayNG tethering routing"
        cleanupRouting(resetPreference = true)
        routingState = ROUTING_STATE_DISABLED
        routingDetail = ""
        return RESULT_OK
    }

    private fun stopActiveTetheringLocked(clearDesired: Boolean): Int {
        val activeTypes = getActiveTetheringTypes()
        if (activeTypes < 0) return RESULT_INTERNAL_ERROR

        if (clearDesired) routingSession?.desiredTetheringTypes = 0

        var result = RESULT_OK
        for (type in 0..MAX_TETHERING_TYPE) {
            if (activeTypes and tetheringTypeBit(type) == 0) continue
            val stopResult = setTetheringEnabled(type, false)
            if (stopResult != RESULT_OK && result == RESULT_OK) result = stopResult
        }
        return result
    }

    override fun notifyCoreStopping(token: String): Int = synchronized(this) {
        val session = findRoutingSession(token) ?: return@synchronized RESULT_INVALID_SESSION
        val activeTypes = getActiveTetheringTypes()
        if (activeTypes >= 0 &&
            (routingState == ROUTING_STATE_ACTIVE_HEV ||
                routingState == ROUTING_STATE_ACTIVE_NATIVE ||
                session.desiredTetheringTypes == 0)
        ) {
            session.desiredTetheringTypes = activeTypes
        }
        stopRoutingEngineLocked()
        session.coreRestartPending = true
        if (testTun != null) {
            routingState = ROUTING_STATE_WAITING
            updateRoutingDetailLocked()
            Log.i(
                TAG,
                "Main core stopping; tethering core stopped while preserving the protected " +
                    "test network and tethering types " +
                    "0x${session.desiredTetheringTypes.toString(16)}",
            )
        } else {
            val tetheringResult = stopActiveTetheringLocked(clearDesired = false)
            routingState = ROUTING_STATE_ERROR
            routingDetail = "Protected test network is unavailable"
            Log.e(
                TAG,
                "Main core stopping without a protected test network; disabled tethering " +
                    "with result $tetheringResult",
            )
        }
        RESULT_OK
    }

    override fun synchronizeRouting(
        token: String,
        useHev: Boolean,
        profileName: String,
        coreConfig: String,
        hevConfig: String,
    ): Int = synchronized(this) {
        val session = findRoutingSession(token) ?: return@synchronized RESULT_INVALID_SESSION
        val launchConfig = HotspotRoutingLaunchConfig(
            useHev = useHev,
            profileName = profileName,
            coreConfig = coreConfig,
            hevConfig = hevConfig,
            assetPath = session.assetPath,
            xudpKey = session.xudpKey,
        )
        return@synchronized runCatching {
            applyRoutingConfigLocked(launchConfig, session)
            RESULT_OK
        }.getOrElse {
            failRoutingSynchronizationLocked(it, session)
            RESULT_ROUTING_FAILED
        }
    }

    override fun notifyCoreStartFailed(token: String, detail: String): Int = synchronized(this) {
        val session = findRoutingSession(token) ?: return@synchronized RESULT_INVALID_SESSION
        failRoutingSynchronizationLocked(
            IllegalStateException(detail.ifBlank { "v2rayNG failed to restart" }),
            session,
        )
        RESULT_OK
    }

    private fun findRoutingSession(token: String): RoutingSession? {
        val session = routingSession
        if (session == null || token.isBlank() || token != session.token) {
            Log.w(TAG, "Ignoring hotspot update for an inactive or invalid session")
            return null
        }
        return session
    }

    private fun applyRoutingConfigLocked(
        launchConfig: HotspotRoutingLaunchConfig,
        session: RoutingSession,
    ) {
        Log.i(
            TAG,
            "Synchronizing hotspot routing to profile ${launchConfig.profileName.ifBlank { "<unnamed>" }}",
        )
        val currentlyActive = getActiveTetheringTypes().coerceAtLeast(0)
        val restoreTypes = if (session.coreRestartPending) {
            session.desiredTetheringTypes or currentlyActive
        } else {
            currentlyActive
        }
        session.desiredTetheringTypes = restoreTypes

        val canSwitchInPlace = testTun != null &&
            (routingState == ROUTING_STATE_ACTIVE_HEV ||
                routingState == ROUTING_STATE_ACTIVE_NATIVE ||
                routingState == ROUTING_STATE_STARTING ||
                routingState == ROUTING_STATE_WAITING)
        var switchedInPlace = false
        if (canSwitchInPlace) {
            switchedInPlace = runCatching {
                restartRoutingEngineLocked(launchConfig)
            }.onFailure {
                Log.w(TAG, "In-place hotspot engine switch failed; rebuilding the test network", it)
            }.isSuccess
        }

        val failedTypes = if (switchedInPlace) {
            restoreTetheringTypesLocked(restoreTypes)
        } else {
            rebuildRoutingLocked(launchConfig, restoreTypes)
        }
        session.coreRestartPending = false
        reportTetheringRestoreFailuresLocked(failedTypes)
        if (failedTypes == 0) {
            Log.i(
                TAG,
                "Hotspot routing synchronized on ${testInterfaceName.orEmpty()}; tethering types 0x${restoreTypes.toString(16)}",
            )
        }
    }

    private fun restartRoutingEngineLocked(config: HotspotRoutingLaunchConfig) {
        routingState = ROUTING_STATE_STARTING
        routingDetail = "Switching tethering to the new v2rayNG connection"
        stopRoutingEngineLocked()
        val tun = checkNotNull(testTun) { "Test TUN file descriptor is unavailable" }
        startRoutingEngineLocked(config, tun.fd)
        setRoutingActiveLocked(config)
    }

    private fun restoreTetheringTypesLocked(types: Int): Int {
        var activeTypes = getActiveTetheringTypes()
        if (activeTypes < 0) return types

        var failedTypes = 0
        for (type in 0..MAX_TETHERING_TYPE) {
            val bit = tetheringTypeBit(type)
            if (types and bit == 0 || activeTypes and bit != 0) continue
            val result = setTetheringEnabled(type, true)
            if (result == RESULT_OK) activeTypes = activeTypes or bit else failedTypes = failedTypes or bit
        }
        return failedTypes
    }

    private fun failRoutingSynchronizationLocked(error: Throwable, session: RoutingSession) {
        val detail = rootCauseMessage(error)
        Log.e(TAG, "Unable to synchronize hotspot routing: $detail", error)
        stopRoutingEngineLocked()
        session.coreRestartPending = true
        if (testTun != null) {
            routingState = ROUTING_STATE_WAITING
            updateRoutingDetailLocked()
            Log.w(TAG, "Tethering remains fail-closed on ${testInterfaceName.orEmpty()}")
        } else {
            val tetheringResult = stopActiveTetheringLocked(clearDesired = false)
            if (tetheringResult == RESULT_OK) cleanupRouting(resetPreference = true)
            routingState = ROUTING_STATE_ERROR
            routingDetail = detail
        }
    }

    override fun destroy() {
        val safeToExit = synchronized(this) {
            shutdownRoutingLocked() == RESULT_OK
        }
        if (!safeToExit) {
            Log.e(TAG, "Refusing to destroy the UserService while tethering may still be active")
            return
        }
        System.exit(0)
    }

    private fun setTetheringEnabled(type: Int, enabled: Boolean): Int {
        val activeTypes = getActiveTetheringTypes()
        if (activeTypes >= 0) {
            val alreadyEnabled = activeTypes and tetheringTypeBit(type) != 0
            if (enabled == alreadyEnabled) return RESULT_OK
        }

        if (Build.VERSION.SDK_INT >= 36) {
            return runCatching {
                TetheringApi36.setTetheringEnabled(
                    tetheringManager,
                    type,
                    enabled,
                    executor,
                    CALLBACK_TIMEOUT_SECONDS,
                )
            }.onFailure {
                Log.e(TAG, "Unable to change API 36 tethering type $type to $enabled", it)
            }.getOrDefault(RESULT_INTERNAL_ERROR)
        }

        return if (enabled) {
            runCatching {
                TetheringApi30To35.startTethering(
                    tetheringManager,
                    type,
                    executor,
                    CALLBACK_TIMEOUT_SECONDS,
                )
            }.onFailure {
                Log.e(TAG, "Unable to start legacy tethering type $type", it)
            }.getOrDefault(RESULT_INTERNAL_ERROR)
        } else {
            stopLegacyTethering(type)
        }
    }

    private fun rebuildRoutingLocked(config: HotspotRoutingLaunchConfig, restoreTypes: Int): Int {
        val stopResult = stopActiveTetheringLocked(clearDesired = false)
        check(stopResult == RESULT_OK) {
            "Unable to pause tethering safely before rebuilding its protected route"
        }
        val result = startRoutingOnNewTestNetworkLocked(config)
        check(result == RESULT_OK) {
            routingDetail.ifBlank { "Unable to rebuild tethering routing" }
        }
        return restoreTetheringTypesLocked(restoreTypes)
    }

    private fun reportTetheringRestoreFailuresLocked(failedTypes: Int) {
        if (failedTypes == 0) return
        routingDetail += " · Unable to restore tethering types 0x${failedTypes.toString(16)}"
        Log.w(TAG, routingDetail)
    }

    private fun getLegacyActiveTetheringTypes(): Int {
        val interfaces = invokeLegacyStringList("getTetheredIfaces")
            ?: error("TetheringManager.getTetheredIfaces is unavailable")
        if (interfaces.isEmpty()) return 0

        val regexesByType = mapOf(
            TETHERING_TYPE_WIFI to
                invokeLegacyStringList("getTetherableWifiRegexs").orEmpty(),
            TETHERING_TYPE_USB to
                invokeLegacyStringList("getTetherableUsbRegexs").orEmpty(),
            LEGACY_TETHERING_TYPE_BLUETOOTH to
                invokeLegacyStringList("getTetherableBluetoothRegexs").orEmpty(),
        )
        return interfaces.fold(0) { mask, interfaceName ->
            val type = regexesByType.entries.firstOrNull { (_, regexes) ->
                regexes.any { pattern -> runCatching { Regex(pattern).matches(interfaceName) }.getOrDefault(false) }
            }?.key ?: inferLegacyTetheringType(interfaceName)
            if (type == null) mask else mask or tetheringTypeBit(type)
        }
    }

    private fun invokeLegacyStringList(methodName: String): List<String>? {
        val method = tetheringManager.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        } ?: return null
        return when (val result = method.invoke(tetheringManager)) {
            null -> null
            is Array<*> -> result.filterIsInstance<String>()
            is Collection<*> -> result.filterIsInstance<String>()
            else -> null
        }
    }

    private fun inferLegacyTetheringType(interfaceName: String): Int? {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") ->
                TETHERING_TYPE_WIFI
            name.startsWith("usb") || name.startsWith("rndis") || name.startsWith("ncm") ->
                TETHERING_TYPE_USB
            name.startsWith("bt-pan") || name.startsWith("bnep") ->
                LEGACY_TETHERING_TYPE_BLUETOOTH
            else -> null
        }
    }

    private fun stopLegacyTethering(type: Int): Int {
        return try {
            val method = tetheringManager.javaClass.methods.firstOrNull {
                it.name == "stopTethering" &&
                    it.parameterTypes.contentEquals(arrayOf(Integer.TYPE))
            } ?: error("TetheringManager.stopTethering(int) is unavailable")
            method.invoke(tetheringManager, type)

            val deadline = System.nanoTime() +
                TimeUnit.SECONDS.toNanos(CALLBACK_TIMEOUT_SECONDS)
            val bit = tetheringTypeBit(type)
            while (System.nanoTime() < deadline) {
                val activeTypes = getLegacyActiveTetheringTypes()
                if (activeTypes and bit == 0) return RESULT_OK
                Thread.sleep(LEGACY_STOP_POLL_MILLIS)
            }
            Log.e(TAG, "Timed out waiting for legacy tethering type $type to stop")
            RESULT_INTERNAL_ERROR
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Interrupted while stopping legacy tethering type $type", error)
            RESULT_INTERNAL_ERROR
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to stop legacy tethering type $type", error)
            RESULT_INTERNAL_ERROR
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("WrongConstant") // TRANSPORT_TEST is a hidden transport type.
    private fun createTestNetwork() {
        val manager = checkNotNull(shellContext.getSystemService(TEST_NETWORK_SERVICE)) {
            "TestNetworkManager is unavailable on this Android build"
        }
        testNetworkManager = manager

        val address = createLinkAddress(AppConfig.SHIZUKU_TUN_ADDR_V4)
        val createMethod = manager.javaClass.methods
            .filter { it.name == "createTunInterface" && it.parameterCount == 1 }
            .firstOrNull { it.parameterTypes[0].isArray }
            ?: manager.javaClass.methods.firstOrNull {
                it.name == "createTunInterface" && it.parameterCount == 1 &&
                    Collection::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            ?: error("TestNetworkManager.createTunInterface is unavailable")
        val testInterface = if (createMethod.parameterTypes[0].isArray) {
            createMethod.invoke(manager, arrayOf(address) as Any)
        } else {
            createMethod.invoke(manager, listOf(address))
        } ?: error("TestNetworkManager returned no TUN interface")
        testNetworkInterface = testInterface
        testTun = testInterface.javaClass.getMethod("getFileDescriptor")
            .invoke(testInterface) as ParcelFileDescriptor
        testInterfaceName = testInterface.javaClass.getMethod("getInterfaceName")
            .invoke(testInterface) as String

        val networkReady = CountDownLatch(1)
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun capture(network: Network) {
                val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName
                if (interfaceName == testInterfaceName) {
                    testNetwork = network
                    networkReady.countDown()
                }
            }

            override fun onAvailable(network: Network) = capture(network)
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (linkProperties.interfaceName == testInterfaceName) {
                    testNetwork = network
                    networkReady.countDown()
                }
            }
        }
        testNetworkCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(TRANSPORT_TEST)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .build()
        connectivityManager.requestNetwork(request, callback)

        val token = Binder()
        testNetworkToken = token
        val linkProperties = LinkProperties().apply {
            interfaceName = testInterfaceName
            setLinkAddresses(listOf(address))
            setDnsServers(
                listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("8.8.8.8"),
                )
            )
        }
        val setupMethod = manager.javaClass.methods.firstOrNull {
            it.name == "setupTestNetwork" && it.parameterTypes.contentEquals(
                arrayOf(LinkProperties::class.java, java.lang.Boolean.TYPE, IBinder::class.java)
            )
        } ?: error("TestNetworkManager.setupTestNetwork is unavailable")
        setupMethod.invoke(manager, linkProperties, true, token)

        if (!networkReady.await(TEST_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            error("Android did not publish the test-network TUN")
        }
    }

    private fun setPreferTestNetworks(prefer: Boolean) {
        val method = tetheringManager.javaClass.methods.firstOrNull {
            it.name == "setPreferTestNetworks" && it.parameterTypes.contentEquals(
                arrayOf(java.lang.Boolean.TYPE)
            )
        } ?: error("TetheringManager.setPreferTestNetworks is unavailable")
        method.invoke(tetheringManager, prefer)
    }

    private fun startRoutingEngineLocked(config: HotspotRoutingLaunchConfig, fd: Int) {
        if (config.useHev) {
            require(config.hevConfig.isNotBlank()) { "HEV configuration is empty" }
            startHev(config.hevConfig, fd)
        } else {
            require(config.coreConfig.isNotBlank()) { "Xray configuration is empty" }
            startNativeXray(config.coreConfig, fd, config.assetPath, config.xudpKey)
        }
        routingUsesHev = config.useHev
    }

    private fun setRoutingActiveLocked(config: HotspotRoutingLaunchConfig) {
        routingProfileName = config.profileName
        routingState = if (config.useHev) ROUTING_STATE_ACTIVE_HEV else ROUTING_STATE_ACTIVE_NATIVE
        updateRoutingDetailLocked()
    }

    private fun updateRoutingDetailLocked() {
        routingDetail = buildString {
            append(testInterfaceName.orEmpty())
            if (routingProfileName.isNotBlank()) {
                append(" · ")
                append(routingProfileName)
            }
        }
    }

    private fun stopRoutingEngineLocked() {
        runCatching { nativeController?.stopLoop() }
            .onFailure { Log.w(TAG, "Unable to stop native hotspot core", it) }
        nativeController = null
        runCatching { if (routingUsesHev || hevConfigFile != null) TProxyService.stopExternalTunnel() }
            .onFailure { Log.w(TAG, "Unable to stop hotspot HEV", it) }
        hevConfigFile?.let { runCatching { it.delete() } }
        hevConfigFile = null
        routingUsesHev = false
    }

    private fun startHev(config: String, fd: Int) {
        val file = File(SHELL_RUNTIME_DIR, "v2rayng-hotspot-${android.os.Process.myPid()}.yaml")
        file.writeText(config)
        hevConfigFile = file
        TProxyService.startExternalTunnel(file.absolutePath, fd)
    }

    private fun startNativeXray(
        config: String,
        fd: Int,
        assetPath: String,
        xudpKey: String,
    ) {
        Seq.setContext(appContext.applicationContext)
        Libv2ray.initCoreEnv(assetPath, xudpKey)
        val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, status: String?): Long {
                Log.i(TAG, "Hotspot Xray status $code: ${status.orEmpty()}")
                return 0
            }
        })
        nativeController = controller
        try {
            controller.startLoop(config, fd)
            check(controller.isRunning) { "Native Xray hotspot core did not start" }
        } catch (error: Throwable) {
            runCatching { controller.stopLoop() }
            nativeController = null
            throw error
        }
    }

    private fun cleanupRouting(resetPreference: Boolean) {
        stopRoutingEngineLocked()

        testNetworkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        testNetworkCallback = null
        val manager = testNetworkManager
        val network = testNetwork
        if (manager != null && network != null) {
            runCatching {
                manager.javaClass.getMethod("teardownTestNetwork", Network::class.java)
                    .invoke(manager, network)
            }.onFailure { Log.w(TAG, "Unable to tear down test network", it) }
        }
        testNetwork = null
        runCatching { testTun?.close() }
        testTun = null
        testNetworkInterface = null
        testNetworkManager = null
        testNetworkToken = null
        testInterfaceName = null
        routingProfileName = ""

        if (resetPreference) {
            runCatching { setPreferTestNetworks(false) }
                .onFailure { Log.w(TAG, "Unable to restore tethering upstream preference", it) }
        }
    }

    private fun createLinkAddress(cidr: String): LinkAddress {
        val addressClass = LinkAddress::class.java
        val stringConstructor = addressClass.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        if (stringConstructor != null) {
            stringConstructor.isAccessible = true
            return stringConstructor.newInstance(cidr) as LinkAddress
        }

        val (host, prefix) = cidr.split('/', limit = 2)
        val inetConstructor = addressClass.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(
                arrayOf(InetAddress::class.java, Integer.TYPE)
            )
        } ?: error("LinkAddress constructor is unavailable")
        inetConstructor.isAccessible = true
        return inetConstructor.newInstance(InetAddress.getByName(host), prefix.toInt()) as LinkAddress
    }

    private fun rootCauseMessage(error: Throwable): String {
        var current = if (error is InvocationTargetException) error.targetException else error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message?.takeIf { it.isNotBlank() } ?: current.javaClass.simpleName
    }

    private fun tetheringTypeBit(type: Int): Int = if (type in 0..30) 1 shl type else 0

    companion object {
        private const val TAG = "ShizukuTethering"
        // Shizuku UserServices can outlive an APK update. Bump this whenever the service
        // implementation or its AIDL contract changes so an incompatible shell process is
        // replaced even when a locally rebuilt APK keeps the same Android versionCode.
        const val USER_SERVICE_VERSION = 20_260_725
        private const val TETHERING_SERVICE = "tethering"
        private const val TEST_NETWORK_SERVICE = "test_network"
        private const val SHELL_RUNTIME_DIR = "/data/local/tmp"
        private const val TRANSPORT_TEST = 7
        private const val MAX_TETHERING_TYPE = 15
        private const val TETHERING_TYPE_WIFI = 0
        private const val LEGACY_TETHERING_TYPE_BLUETOOTH = 2
        private const val CALLBACK_TIMEOUT_SECONDS = 10L
        private const val LEGACY_STOP_POLL_MILLIS = 100L
        private const val TEST_NETWORK_TIMEOUT_SECONDS = 15L

        const val TETHERING_TYPE_USB = 1
        const val TETHERING_TYPES_UNKNOWN = -1

        const val HOTSPOT_STATE_DISABLED = 0
        const val HOTSPOT_STATE_ENABLED = 1
        const val HOTSPOT_STATE_UNKNOWN = 2

        const val ROUTING_STATE_DISABLED = 0
        const val ROUTING_STATE_STARTING = 1
        const val ROUTING_STATE_ACTIVE_HEV = 2
        const val ROUTING_STATE_ACTIVE_NATIVE = 3
        const val ROUTING_STATE_STOPPING = 4
        const val ROUTING_STATE_ERROR = 5
        const val ROUTING_STATE_WAITING = 6

        const val RESULT_OK = 0
        const val RESULT_INTERNAL_ERROR = -1
        const val RESULT_ROUTING_FAILED = -2
        const val RESULT_IMPLEMENTATION_MISMATCH = -3
        const val RESULT_INVALID_SESSION = -4
        const val RESULT_ALREADY_ACTIVE = -5

        internal fun createUserServiceArgs() = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuTetheringService::class.java.name)
        )
            .daemon(true)
            .processNameSuffix("shizuku_tethering")
            .debuggable(BuildConfig.DEBUG)
            .version(USER_SERVICE_VERSION)
    }
}
