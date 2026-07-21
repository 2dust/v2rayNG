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
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Privileged Shizuku process that controls Android tethering and owns its proxy upstream.
 *
 * Android's TestNetworkManager creates a real kernel TUN without root when called by the shell
 * UID. TetheringManager is then told to prefer test networks, so Android's Wi-Fi/USB DHCP,
 * forwarding, NAT and DNS machinery sends client traffic into that TUN. A process-local HEV
 * instance consumes the TUN through v2rayNG's SOCKS inbound, or a second Xray controller consumes
 * it using a configuration derived from the running core.
 *
 * Selecting a preferred test network and starting a tethering downstream are separate Android
 * operations. During that gap, especially while Wi-Fi changes from station to access-point mode,
 * Tethering may briefly choose a physical default network. Every downstream start is therefore
 * treated as provisional until Android reports only the owned test TUN as its actual upstream.
 * Any other non-empty upstream is stopped immediately, keeping the feature fail-closed.
 */
@Keep
class ShizukuTetheringService(context: Context) : IShizukuTetheringService.Stub() {

    // The UserService runs as shell, and framework permission checks also require shell package
    // attribution. The ordinary app context is retained only for the embedded Xray runtime.
    private val executor = Executor { command -> command.run() }
    private val appContext = context.applicationContext
    private val shellContext = ShellContextCompat.create(context)
    private val tetheringManager = requireNotNull(
        // Context.TETHERING_SERVICE is not SDK-visible until API 36.
        shellContext.getSystemService(TETHERING_SERVICE)
    ) { "TetheringManager is unavailable" }
    private val connectivityManager = requireNotNull(
        shellContext.getSystemService(ConnectivityManager::class.java)
    ) { "ConnectivityManager is unavailable" }
    // Binder entry points that touch lifecycle state are synchronized. Keeping the whole routing
    // state machine under one monitor makes profile changes and user toggles transactional.
    private var routingState = ROUTING_STATE_DISABLED
    private var routingDetail = ""
    private var routingProfileName = ""
    private var routingSession: RoutingSession? = null
    private var testNetworkHandle: TestNetworkHandle? = null
    private var nativeController: CoreController? = null
    private var hevConfigFile: File? = null
    // Tethering callbacks can acknowledge a start before the downstream appears in the active
    // type list. Retain that in-flight intent so concurrent shutdown and failed-start cleanup
    // still issue stopTethering instead of mistaking the downstream for already stopped.
    private var requestedTetheringTypes = 0
    // Profile synchronization may restore several downstream types in the background. Track the
    // warning per type so a later success clears only its own stale failure before the UI sees it.
    private var wrongUpstreamWarningTypes = 0

    private val routingActive: Boolean
        get() = routingState == ROUTING_STATE_ACTIVE_HEV ||
            routingState == ROUTING_STATE_ACTIVE_NATIVE

    private val testTun: ParcelFileDescriptor?
        get() = testNetworkHandle?.tun
    private val testInterfaceName: String?
        get() = testNetworkHandle?.interfaceName

    private data class RoutingSession(
        val token: String,
        val assetPath: String,
        val xudpKey: String,
        var dnsServers: List<String>,
        var desiredTetheringTypes: Int,
        var coreRestartPending: Boolean = false,
    )

    /** Owns every Android object whose lifetime is tied to one test-network TUN. */
    private class TestNetworkHandle(
        val manager: Any,
        // Keep TestNetworkInterface reachable; Android associates its lifetime with this object.
        private val interfaceLifetime: Any,
        val tun: ParcelFileDescriptor,
        val interfaceName: String,
        private val connectivityManager: ConnectivityManager,
    ) {
        private val published = CountDownLatch(1)
        val networkLifetimeToken: IBinder = Binder()
        var network: Network? = null
            private set

        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun capture(network: Network, interfaceName: String?) {
                if (interfaceName == this@TestNetworkHandle.interfaceName) {
                    this@TestNetworkHandle.network = network
                    published.countDown()
                }
            }

            override fun onAvailable(network: Network) {
                capture(network, connectivityManager.getLinkProperties(network)?.interfaceName)
            }

            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                capture(network, properties.interfaceName)
            }
        }

        fun awaitPublished(): Boolean =
            published.await(TEST_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        fun release() {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            network?.let { publishedNetwork ->
                runCatching {
                    manager.javaClass.getMethod("teardownTestNetwork", Network::class.java)
                        .invoke(manager, publishedNetwork)
                }.onFailure { Log.w(TAG, "Unable to tear down test network", it) }
            }
            runCatching { tun.close() }
        }
    }

    @Synchronized
    override fun setWifiHotspotEnabled(enabled: Boolean): Int {
        if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return RESULT_ROUTING_FAILED
        }
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
        return result
    }

    override fun getActiveTetheringTypes(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                TetheringApi36.getActiveTetheringTypes(
                    tetheringManager,
                    executor,
                    CALLBACK_TIMEOUT_SECONDS,
                )
            } else {
                TetheringPlatformCompat.getActiveTetheringTypes(tetheringManager)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to read tethering state", error)
            TETHERING_TYPES_UNKNOWN
        }
    }

    @Synchronized
    override fun getRoutingState(): Int {
        if (routingState == ROUTING_STATE_ACTIVE_NATIVE && nativeController?.isRunning != true) {
            setRoutingError("Native Xray core stopped unexpectedly")
        }
        return routingState
    }

    @Synchronized
    override fun getRoutingDetail(): String {
        if (!routingActive && routingState != ROUTING_STATE_WAITING) return routingDetail
        val upstreamInterface = runCatching {
            TetheringPlatformCompat.getUpstreamInterfaceName()
        }.onFailure {
            Log.e(TAG, "Unable to read the active tethering upstream", it)
        }.getOrDefault("")
        return formatRoutingDetail(upstreamInterface)
    }

    @Synchronized
    override fun consumeWarning(): Int {
        val warning = if (wrongUpstreamWarningTypes == 0) {
            RESULT_OK
        } else {
            RESULT_UNPROTECTED_UPSTREAM
        }
        wrongUpstreamWarningTypes = 0
        return warning
    }

    @Synchronized
    override fun startRouting(
        useHev: Boolean,
        profileName: String,
        engineConfig: String,
        dnsServers: Array<out String>,
        assetPath: String,
        xudpKey: String,
        syncToken: String,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return RESULT_ROUTING_FAILED
        if (syncToken.isBlank()) {
            routingDetail = "Tethering synchronization token is empty"
            return RESULT_INVALID_SESSION
        }
        val activeTypes = getActiveTetheringTypes()
        val launchConfig = HotspotRoutingLaunchConfig(
            engine = HotspotRoutingEngineConfig(useHev, profileName, engineConfig),
            dnsServers = dnsServers.toList(),
            assetPath = assetPath,
            xudpKey = xudpKey,
        )
        val newSession = RoutingSession(
            token = syncToken,
            assetPath = launchConfig.assetPath,
            xudpKey = launchConfig.xudpKey,
            dnsServers = launchConfig.dnsServers,
            desiredTetheringTypes = activeTypes.takeIf { it >= 0 }
                ?: routingSession?.desiredTetheringTypes
                ?: 0,
        )

        val result = startRoutingLocked(launchConfig)
        if (result == RESULT_OK) routingSession = newSession
        return result
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startRoutingLocked(config: HotspotRoutingLaunchConfig): Int {
        if (routingActive) {
            if ((routingState == ROUTING_STATE_ACTIVE_HEV) != config.engine.useHev) {
                return RESULT_IMPLEMENTATION_MISMATCH
            }
            routingDetail = "Tethering routing is already active"
            return RESULT_ALREADY_ACTIVE
        }

        return try {
            if (testTun == null) {
                createRoutingLocked(config)
            } else {
                val restoreTypes = getActiveTetheringTypes()
                check(restoreTypes >= 0) {
                    "Unable to determine active tethering before rebuilding its protected route"
                }
                val failedTypes = rebuildRoutingLocked(config, restoreTypes)
                reportTetheringRestoreFailuresLocked(failedTypes)
            }
            RESULT_OK
        } catch (error: Throwable) {
            val detail = rootCauseMessage(error)
            Log.e(TAG, "Unable to start v2rayNG tethering routing: $detail", error)
            setRoutingError(detail)
            RESULT_ROUTING_FAILED
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRoutingLocked(config: HotspotRoutingLaunchConfig) {
        cleanupRouting()
        routingState = ROUTING_STATE_STARTING
        routingDetail = "Creating Android test-network TUN"

        try {
            setPreferTestNetworks(true)
            createTestNetwork(config.dnsServers)
            val tun = checkNotNull(testTun) { "Test TUN file descriptor is unavailable" }
            startRoutingEngineLocked(config, tun.fd)
            setRoutingActiveLocked(config)
        } catch (error: Throwable) {
            // A partially published test network is not useful for recovery. Release it here so
            // callers always see either a complete protected route or no route at all.
            cleanupRouting()
            throw error
        }
    }

    @Synchronized
    override fun stopRouting(): Int = shutdownRoutingLocked()

    private fun shutdownRoutingLocked(): Int {
        val tetheringResult = stopActiveTetheringLocked(clearDesired = true)
        if (tetheringResult != RESULT_OK) {
            setRoutingError("Unable to disable tethering safely before removing its protected route")
            return tetheringResult
        }
        routingSession = null
        routingState = ROUTING_STATE_STOPPING
        routingDetail = "Stopping v2rayNG tethering routing"
        cleanupRouting()
        routingState = ROUTING_STATE_DISABLED
        routingDetail = ""
        return RESULT_OK
    }

    private fun stopActiveTetheringLocked(clearDesired: Boolean): Int {
        val activeTypes = getActiveTetheringTypes()
        if (activeTypes < 0) return RESULT_INTERNAL_ERROR

        // Include starts that Android accepted but has not published as active yet. The protected
        // test network and routing engine must not be removed while such a start can still finish.
        val typesToStop = activeTypes or requestedTetheringTypes
        if (clearDesired) routingSession?.desiredTetheringTypes = 0

        var result = RESULT_OK
        forEachTetheringType(typesToStop) { type, _ ->
            val stopResult = stopTetheringTypeLocked(type)
            if (stopResult != RESULT_OK && result == RESULT_OK) result = stopResult
        }
        return result
    }

    /**
     * Stops the secondary engine before the main core changes, but deliberately keeps the test TUN
     * and downstreams alive. Until [synchronizeRouting] supplies the replacement configuration,
     * tethered clients remain on a dead TUN instead of falling back to the physical network.
     */
    @Synchronized
    override fun notifyCoreStopping(token: String): Int {
        val session = findRoutingSession(token) ?: return RESULT_INVALID_SESSION
        val activeTypes = getActiveTetheringTypes()
        if (activeTypes >= 0 && (routingActive || session.desiredTetheringTypes == 0)) {
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
            setRoutingError("Protected test network is unavailable")
            Log.e(
                TAG,
                "Main core stopping without a protected test network; disabled tethering " +
                    "with result $tetheringResult",
            )
        }
        return RESULT_OK
    }

    @Synchronized
    override fun synchronizeRouting(
        token: String,
        useHev: Boolean,
        profileName: String,
        engineConfig: String,
        dnsServers: Array<out String>,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return RESULT_ROUTING_FAILED
        val session = findRoutingSession(token) ?: return RESULT_INVALID_SESSION
        val launchConfig = HotspotRoutingLaunchConfig(
            engine = HotspotRoutingEngineConfig(useHev, profileName, engineConfig),
            dnsServers = dnsServers.toList(),
            assetPath = session.assetPath,
            xudpKey = session.xudpKey,
        )
        return runCatching {
            applyRoutingConfigLocked(launchConfig, session)
            RESULT_OK
        }.getOrElse {
            failRoutingSynchronizationLocked(it, session)
            RESULT_ROUTING_FAILED
        }
    }

    @Synchronized
    override fun notifyCoreStartFailed(token: String, detail: String): Int {
        val session = findRoutingSession(token) ?: return RESULT_INVALID_SESSION
        failRoutingSynchronizationLocked(
            IllegalStateException(detail.ifBlank { "v2rayNG failed to restart" }),
            session,
        )
        return RESULT_OK
    }

    private fun findRoutingSession(token: String): RoutingSession? {
        val session = routingSession
        if (session == null || token.isBlank() || token != session.token) {
            Log.w(TAG, "Ignoring hotspot update for an inactive or invalid session")
            return null
        }
        return session
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyRoutingConfigLocked(
        launchConfig: HotspotRoutingLaunchConfig,
        session: RoutingSession,
    ) {
        Log.i(
            TAG,
            "Synchronizing hotspot routing to profile ${launchConfig.engine.profileName.ifBlank { "<unnamed>" }}",
        )
        val currentlyActive = getActiveTetheringTypes().coerceAtLeast(0)
        val restoreTypes = if (session.coreRestartPending) {
            session.desiredTetheringTypes or currentlyActive
        } else {
            currentlyActive
        }
        session.desiredTetheringTypes = restoreTypes

        val switchedInPlace = launchConfig.dnsServers == session.dnsServers &&
            testTun != null &&
            (routingActive || routingState == ROUTING_STATE_WAITING) &&
            runCatching {
                // Reusing the TUN avoids changing Android's selected tethering upstream during an
                // ordinary profile switch. Rebuild only if the engine cannot reuse its descriptor.
                restartRoutingEngineLocked(launchConfig)
            }.onFailure {
                Log.w(TAG, "In-place hotspot engine switch failed; rebuilding the test network", it)
            }.isSuccess

        val failedTypes = if (switchedInPlace) {
            restoreTetheringTypesLocked(restoreTypes)
        } else {
            rebuildRoutingLocked(launchConfig, restoreTypes)
        }
        session.dnsServers = launchConfig.dnsServers
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
        if (getActiveTetheringTypes() < 0) return types

        var failedTypes = 0
        forEachTetheringType(types) { type, bit ->
            val result = setTetheringEnabled(type, true)
            if (result != RESULT_OK) failedTypes = failedTypes or bit
        }
        return failedTypes
    }

    /** Visits exactly the tethering types represented in Android's non-negative bit mask. */
    private inline fun forEachTetheringType(types: Int, action: (type: Int, bit: Int) -> Unit) {
        var remaining = types
        while (remaining > 0) {
            val bit = Integer.lowestOneBit(remaining)
            action(Integer.numberOfTrailingZeros(bit), bit)
            remaining = remaining xor bit
        }
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
            if (tetheringResult == RESULT_OK) cleanupRouting()
            setRoutingError(detail)
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

    /**
     * Changes one real Android tethering downstream while preserving the protected-upstream
     * invariant.
     *
     * `setPreferTestNetworks(true)` influences Android's upstream choice, but it does not make the
     * subsequent tethering start atomic. The callback can also arrive before the downstream and
     * its selected upstream are visible. A successful start is consequently not returned to the
     * app until the downstream is active and its actual upstream is exactly [testInterfaceName].
     */
    private fun setTetheringEnabled(type: Int, enabled: Boolean): Int {
        val bit = tetheringTypeBit(type)
        if (bit == 0) return RESULT_INTERNAL_ERROR

        val activeTypes = getActiveTetheringTypes()
        val result = if (!enabled) {
            val alreadyStopped = activeTypes >= 0 && activeTypes and bit == 0 &&
                requestedTetheringTypes and bit == 0
            if (alreadyStopped) RESULT_OK else stopTetheringTypeLocked(type)
        } else {
            startTetheringTypeLocked(type, activeTypes >= 0 && activeTypes and bit != 0)
        }
        if (enabled) {
            wrongUpstreamWarningTypes = when (result) {
                RESULT_OK -> wrongUpstreamWarningTypes and bit.inv()
                RESULT_UNPROTECTED_UPSTREAM -> wrongUpstreamWarningTypes or bit
                else -> wrongUpstreamWarningTypes
            }
        }
        return result
    }

    private fun startTetheringTypeLocked(type: Int, alreadyEnabled: Boolean): Int {
        val firstResult = startTetheringTypeAttemptLocked(type, alreadyEnabled)
        if (firstResult != RESULT_UNPROTECTED_UPSTREAM) return firstResult

        // Some Android builds briefly select their physical upstream while applying the test-
        // network preference. The failed attempt has already been stopped, so it is safe to give
        // the framework a moment to settle and make one fresh attempt. Never loop indefinitely:
        // a persistent wrong upstream must remain fail-closed and be reported to the user.
        Log.w(TAG, "Waiting before retrying protected tethering type $type")
        if (!sleepForUpstreamRetry()) return firstResult
        return startTetheringTypeAttemptLocked(type, alreadyEnabled = false)
    }

    private fun startTetheringTypeAttemptLocked(type: Int, alreadyEnabled: Boolean): Int {
        val bit = tetheringTypeBit(type)
        val expectedUpstream = testInterfaceName
        if (!isRoutingReadyLocked() || testTun == null || expectedUpstream.isNullOrBlank()) {
            Log.e(TAG, "Refusing to start tethering before protected routing is ready")
            return RESULT_ROUTING_FAILED
        }

        // Record the request before crossing into Android. From this point onward every error and
        // every concurrent shutdown path must explicitly stop the possibly in-flight downstream.
        requestedTetheringTypes = requestedTetheringTypes or bit
        val preferenceReady = runCatching { setPreferTestNetworks(true) }
            .onFailure { Log.e(TAG, "Unable to select the protected tethering upstream", it) }
            .isSuccess
        if (!preferenceReady) return stopAfterFailedStartLocked(type, RESULT_INTERNAL_ERROR)

        var needsStart = !alreadyEnabled
        if (alreadyEnabled && !awaitProtectedUpstream(expectedUpstream)) {
            // A downstream started outside this protected session may remain latched to its old
            // physical upstream. Restart it so Android applies the test-network preference while
            // making the new upstream selection.
            Log.w(TAG, "Resetting tethering type $type before protected startup")
            val stopResult = stopTetheringTypeLocked(type)
            if (stopResult != RESULT_OK) return stopAfterFailedStartLocked(type, stopResult)
            needsStart = true
        }

        if (needsStart) {
            val startResult = changeTetheringEnabled(type, true)
            if (startResult != RESULT_OK) return stopAfterFailedStartLocked(type, startResult)
            if (!awaitTetheringTypeState(type, enabled = true)) {
                return stopAfterFailedStartLocked(type, RESULT_INTERNAL_ERROR)
            }
        }

        if (!awaitProtectedUpstream(expectedUpstream)) {
            // Do not wait for a non-empty physical upstream to hand over later: once Android has
            // installed that forwarding path, tethered traffic may already bypass v2rayNG.
            val actualUpstream = readUpstreamInterface()
            Log.e(
                TAG,
                "Refusing tethering type $type on unprotected upstream " +
                    actualUpstream.ifBlank { "<none>" },
            )
            val result = if (actualUpstream.isBlank()) {
                RESULT_ROUTING_FAILED
            } else {
                RESULT_UNPROTECTED_UPSTREAM
            }
            return stopAfterFailedStartLocked(type, result)
        }

        requestedTetheringTypes = requestedTetheringTypes and bit.inv()
        return RESULT_OK
    }

    private fun sleepForUpstreamRetry(): Boolean = try {
        Thread.sleep(UPSTREAM_RETRY_DELAY_MILLIS)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun readUpstreamInterface(): String = runCatching {
        TetheringPlatformCompat.getUpstreamInterfaceName()
    }.getOrDefault("")

    private fun isRoutingReadyLocked(): Boolean {
        return when (routingState) {
            ROUTING_STATE_ACTIVE_NATIVE -> nativeController?.isRunning == true
            ROUTING_STATE_ACTIVE_HEV -> hevConfigFile != null
            else -> false
        }
    }

    private fun stopAfterFailedStartLocked(type: Int, startResult: Int): Int {
        // Cleanup is part of the safety contract. Prefer its failure when Android could not be
        // observed stopping, because the original start error no longer describes the main risk.
        val stopResult = stopTetheringTypeLocked(type)
        return if (stopResult == RESULT_OK) startResult else stopResult
    }

    private fun stopTetheringTypeLocked(type: Int): Int {
        val bit = tetheringTypeBit(type)
        val stopResult = changeTetheringEnabled(type, false)
        if (awaitTetheringTypeState(type, enabled = false)) {
            // Clear the in-flight marker only after Android confirms the downstream is absent.
            requestedTetheringTypes = requestedTetheringTypes and bit.inv()
            return RESULT_OK
        }
        Log.e(TAG, "Timed out waiting for tethering type $type to stop")
        return if (stopResult == RESULT_OK) RESULT_INTERNAL_ERROR else stopResult
    }

    private fun awaitTetheringTypeState(type: Int, enabled: Boolean): Boolean {
        val bit = tetheringTypeBit(type)
        return awaitResult(TETHERING_STATE_TIMEOUT_SECONDS) {
            val activeTypes = getActiveTetheringTypes()
            if (activeTypes >= 0 && (activeTypes and bit != 0) == enabled) true else null
        }
    }

    private fun awaitProtectedUpstream(expectedInterface: String) = awaitResult(
        UPSTREAM_SELECTION_TIMEOUT_SECONDS,
    ) {
        val actualInterface = readUpstreamInterface()
        when {
            TetheringPlatformCompat.isProtectedUpstream(actualInterface, expectedInterface) -> true
            // An empty value means upstream selection has not completed and is safe to poll. Any
            // named, unexpected interface is already a usable forwarding path and must fail fast.
            actualInterface.isNotBlank() -> false
            else -> null
        }
    }

    /** Polls until Android reports success/failure, or returns false on timeout/interruption. */
    private inline fun awaitResult(timeoutSeconds: Long, poll: () -> Boolean?): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            poll()?.let { return it }
            if (System.nanoTime() >= deadline) return false
            try {
                Thread.sleep(TETHERING_STATE_POLL_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    private fun changeTetheringEnabled(type: Int, enabled: Boolean): Int {
        return runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                    TetheringApi36.setTetheringEnabled(
                        tetheringManager,
                        type,
                        enabled,
                        executor,
                        CALLBACK_TIMEOUT_SECONDS,
                    )
                enabled ->
                    TetheringPlatformCompat.startTethering(
                        tetheringManager,
                        type,
                        executor,
                        CALLBACK_TIMEOUT_SECONDS,
                    )
                else -> TetheringPlatformCompat.stopTethering(
                    tetheringManager,
                    type,
                )
            }
        }.onFailure {
            Log.e(TAG, "Unable to set tethering type $type enabled=$enabled", it)
        }.getOrDefault(RESULT_INTERNAL_ERROR)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun rebuildRoutingLocked(config: HotspotRoutingLaunchConfig, restoreTypes: Int): Int {
        val stopResult = stopActiveTetheringLocked(clearDesired = false)
        check(stopResult == RESULT_OK) {
            "Unable to pause tethering safely before rebuilding its protected route"
        }
        createRoutingLocked(config)
        return restoreTetheringTypesLocked(restoreTypes)
    }

    private fun reportTetheringRestoreFailuresLocked(failedTypes: Int) {
        if (failedTypes == 0) return
        routingDetail += " · Unable to restore tethering types 0x${failedTypes.toString(16)}"
        Log.w(TAG, routingDetail)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("WrongConstant") // TRANSPORT_TEST is a hidden transport type.
    private fun createTestNetwork(dnsServers: List<String>) {
        val manager = checkNotNull(shellContext.getSystemService(TEST_NETWORK_SERVICE)) {
            "TestNetworkManager is unavailable on this Android build"
        }
        val address = createLinkAddress(AppConfig.SHIZUKU_TUN_ADDR_V4)
        val handle = createTestNetworkHandle(manager, address)
        testNetworkHandle = handle

        val request = NetworkRequest.Builder()
            .addTransportType(TRANSPORT_TEST)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .build()
        connectivityManager.requestNetwork(request, handle.callback)

        val properties = LinkProperties().apply {
            interfaceName = handle.interfaceName
            setLinkAddresses(listOf(address))
            // Mirror CoreVpnService instead of inventing tethering-specific resolvers. The core
            // configuration decides whether port 53 is handled by v2rayNG's local DNS outbound.
            setDnsServers(dnsServers.map(InetAddress::getByName))
        }
        val setupMethod = manager.javaClass.getMethod(
            "setupTestNetwork",
            LinkProperties::class.java,
            java.lang.Boolean.TYPE,
            IBinder::class.java,
        )
        setupMethod.invoke(manager, properties, true, handle.networkLifetimeToken)

        check(handle.awaitPublished()) { "Android did not publish the test-network TUN" }
    }

    /**
     * TestNetworkManager is not part of the app SDK. Keep its API 33+ reflection in this helper so
     * the routing lifecycle above can use an ordinary [TestNetworkHandle].
     */
    private fun createTestNetworkHandle(manager: Any, address: LinkAddress): TestNetworkHandle {
        val addresses = arrayOf(address)
        val testInterface = manager.javaClass
            .getMethod("createTunInterface", addresses.javaClass)
            .invoke(manager, addresses as Any)
            ?: error("TestNetworkManager returned no TUN interface")
        val tun = testInterface.javaClass.getMethod("getFileDescriptor")
            .invoke(testInterface) as ParcelFileDescriptor
        return try {
            val interfaceName = testInterface.javaClass.getMethod("getInterfaceName")
                .invoke(testInterface) as String
            TestNetworkHandle(
                manager = manager,
                interfaceLifetime = testInterface,
                tun = tun,
                interfaceName = interfaceName,
                connectivityManager = connectivityManager,
            )
        } catch (error: Throwable) {
            runCatching { tun.close() }
            throw error
        }
    }

    private fun setPreferTestNetworks(prefer: Boolean) {
        // This hidden system API makes Android tethering consider the owned test network first.
        tetheringManager.javaClass
            .getMethod("setPreferTestNetworks", java.lang.Boolean.TYPE)
            .invoke(tetheringManager, prefer)
    }

    private fun startRoutingEngineLocked(config: HotspotRoutingLaunchConfig, fd: Int) {
        val engine = config.engine
        require(engine.content.isNotBlank()) {
            "${if (engine.useHev) "HEV" else "Xray"} configuration is empty"
        }
        if (engine.useHev) {
            startHev(engine.content, fd)
        } else {
            startNativeXray(engine.content, fd, config.assetPath, config.xudpKey)
        }
    }

    private fun setRoutingActiveLocked(config: HotspotRoutingLaunchConfig) {
        routingProfileName = config.engine.profileName
        routingState = if (config.engine.useHev) ROUTING_STATE_ACTIVE_HEV else ROUTING_STATE_ACTIVE_NATIVE
        updateRoutingDetailLocked()
    }

    private fun setRoutingError(detail: String) {
        routingState = ROUTING_STATE_ERROR
        routingDetail = detail
    }

    private fun updateRoutingDetailLocked() {
        routingDetail = formatRoutingDetail(testInterfaceName.orEmpty())
    }

    private fun formatRoutingDetail(interfaceName: String): String {
        return listOf(interfaceName, routingProfileName)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    private fun stopRoutingEngineLocked() {
        runCatching { nativeController?.stopLoop() }
            .onFailure { Log.w(TAG, "Unable to stop native hotspot core", it) }
        nativeController = null
        runCatching { if (hevConfigFile != null) TProxyService.stopExternalTunnel() }
            .onFailure { Log.w(TAG, "Unable to stop hotspot HEV", it) }
        hevConfigFile?.let { runCatching { it.delete() } }
        hevConfigFile = null
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
        Seq.setContext(appContext)
        Libv2ray.initCoreEnv(assetPath, xudpKey)
        val controller = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, status: String?): Long {
                Log.i(TAG, "Hotspot Xray status $code: ${status.orEmpty()}")
                return 0
            }
        })
        try {
            controller.startLoop(config, fd)
            check(controller.isRunning) { "Native Xray hotspot core did not start" }
            nativeController = controller
        } catch (error: Throwable) {
            runCatching { controller.stopLoop() }
            throw error
        }
    }

    private fun cleanupRouting() {
        // Callers stop real tethering first. Releasing the engine and TUN in this order then leaves
        // no downstream that Android could silently move back to a physical upstream.
        stopRoutingEngineLocked()

        testNetworkHandle?.release()
        testNetworkHandle = null
        routingProfileName = ""

        runCatching { setPreferTestNetworks(false) }
            .onFailure { Log.w(TAG, "Unable to restore tethering upstream preference", it) }
    }

    private fun createLinkAddress(cidr: String): LinkAddress {
        return LinkAddress::class.java.getDeclaredConstructor(String::class.java).run {
            isAccessible = true
            newInstance(cidr)
        }
    }

    private fun rootCauseMessage(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message?.takeIf { it.isNotBlank() } ?: current.javaClass.simpleName
    }

    companion object {
        private const val TAG = "ShizukuTethering"
        // Shizuku UserServices can outlive an APK update. Bump this whenever the service
        // implementation or its AIDL contract changes so an incompatible shell process is
        // replaced even when a locally rebuilt APK keeps the same Android versionCode.
        const val USER_SERVICE_VERSION = 20_260_745
        private const val TETHERING_SERVICE = "tethering"
        private const val TEST_NETWORK_SERVICE = "test_network"
        private const val SHELL_RUNTIME_DIR = "/data/local/tmp"
        private const val TRANSPORT_TEST = 7
        private const val CALLBACK_TIMEOUT_SECONDS = 10L
        private const val TEST_NETWORK_TIMEOUT_SECONDS = 15L
        private const val TETHERING_STATE_TIMEOUT_SECONDS = 10L
        private const val UPSTREAM_SELECTION_TIMEOUT_SECONDS = 5L
        // Android can take several seconds to dismantle a rejected downstream and reconsider the
        // preferred test network. Keep the delay type-agnostic so Wi-Fi, USB and other downstreams
        // all use the same single bounded retry.
        private const val UPSTREAM_RETRY_DELAY_MILLIS = 8_000L
        private const val TETHERING_STATE_POLL_MILLIS = 200L

        const val TETHERING_TYPE_WIFI = 0
        const val TETHERING_TYPE_USB = 1
        const val TETHERING_TYPES_UNKNOWN = -1

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
        const val RESULT_UNPROTECTED_UPSTREAM = -6

        internal fun createUserServiceArgs() = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuTetheringService::class.java.name)
        )
            .daemon(true)
            .processNameSuffix("shizuku_tethering")
            .debuggable(BuildConfig.DEBUG)
            .version(USER_SERVICE_VERSION)
    }
}
