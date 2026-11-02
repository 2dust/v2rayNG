package com.v2ray.ang.shizuku

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.service.HevTunnelSettings
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File

/** Keeps the normal core's lifecycle and the privileged tethering core synchronized. */
internal object TetheringCoreSync {
    @Volatile
    private var snapshot = HotspotRoutingSnapshot()
    private val coreLease = CoreTetheringLease()
    private var watchingShizuku = false
    private val recoveryLock = Any()
    private var recoveryState = TetheringRecoveryState()
    private var activeProfileId = ""

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (transitionRecovery(TetheringRecoveryState::onBinderReceived) != TetheringRecoveryAction.RECOVER) {
            return@OnBinderReceivedListener
        }
        val currentSnapshot = snapshot.takeIf { it.running } ?: return@OnBinderReceivedListener
        LogUtil.i(AppConfig.TAG, "Shizuku restarted; recovering protected tethering")
        safely("recover", activeProfileId) {
            send(AngApplication.application, HotspotRoutingSync.EVENT_CORE_STARTED, currentSnapshot)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateRecovery(TetheringRecoveryState::onBinderDied)
    }

    fun onStarting() {
        safely("start-preparation") { clearCoreState() }
    }

    fun onStarted(
        service: Service,
        profileId: String,
        profileName: String,
        coreConfig: String,
        useHev: Boolean,
    ) {
        safely("start", profileId) {
            if (!service.resources.getBoolean(R.bool.shizuku_tethering_enabled)) return@safely
            try {
                val currentSnapshot = createSnapshot(service, profileName, useHev)
                coreLease.attach(service, currentSnapshot, coreConfig)
                snapshot = currentSnapshot
                activeProfileId = profileId
                updateRecovery(TetheringRecoveryState::onCoreStarted)
                watchShizuku(service)
                send(service, HotspotRoutingSync.EVENT_CORE_STARTED, snapshot)
            } catch (error: Throwable) {
                clearCoreState()
                throw error
            }
        }
    }

    fun onStartFailed(service: Service, detail: String) {
        safely("start-failure") {
            try {
                send(service, HotspotRoutingSync.EVENT_CORE_START_FAILED, detail = detail)
            } finally {
                clearCoreState()
            }
        }
    }

    fun onStopping(service: Service) {
        safely("stop") {
            try {
                send(service, HotspotRoutingSync.EVENT_CORE_STOPPING)
            } finally {
                clearCoreState()
            }
        }
    }

    fun clear() {
        safely("clear") { clearCoreState() }
    }

    fun onAppForegrounded(service: Service) {
        val action = transitionRecovery { it.onAppForegrounded(Build.VERSION.SDK_INT) }
        if (action != TetheringRecoveryAction.REQUEST_BINDER) return

        // Android 14+ queues Shizuku's cross-process Binder broadcast while the provider
        // process is cached. Once the UI foregrounds that process, explicitly ask it for the
        // replacement Binder so protected tethering can be restored without another user action.
        // Remove this request with ShizukuForegroundRecovery under the condition documented there.
        runCatching { ShizukuProvider.requestBinderForNonProviderProcess(service.applicationContext) }
            .onFailure {
                updateRecovery(TetheringRecoveryState::onForegroundRequestFailed)
                logFailure("foreground-recovery", activeProfileId, it)
            }
    }

    private fun clearCoreState() {
        snapshot = HotspotRoutingSnapshot()
        coreLease.clearEngineConfig()
        activeProfileId = ""
        updateRecovery(TetheringRecoveryState::onCoreStopped)
    }

    private fun watchShizuku(service: Service) {
        if (!watchingShizuku) {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            try {
                Shizuku.addBinderDeadListener(binderDeadListener)
            } catch (error: Throwable) {
                runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
                throw error
            }
            watchingShizuku = true
        }
        val binderAvailable = runCatching { Shizuku.pingBinder() }
            .onFailure { logFailure("check-Shizuku", activeProfileId, it) }
            .getOrDefault(false)
        if (binderAvailable) return

        updateRecovery(TetheringRecoveryState::onBinderDied)
        // The request owns a receiver until Shizuku answers. Tie it to the application rather
        // than the short-lived core Service, and do not register another when its Binder is live.
        runCatching { ShizukuProvider.requestBinderForNonProviderProcess(service.applicationContext) }
            .onFailure { logFailure("request-Binder", activeProfileId, it) }
    }

    fun sendCurrentSnapshot(service: Service, coreRunning: Boolean) {
        safely("snapshot", activeProfileId) {
            val currentSnapshot = snapshot.takeIf { coreRunning } ?: HotspotRoutingSnapshot()
            service.sendBroadcast(
                Intent(AppConfig.BROADCAST_ACTION_ACTIVITY)
                    .setPackage(AppConfig.ANG_PACKAGE)
                    .putExtra("key", AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE)
                    .putExtra("content", currentSnapshot)
                    .withCoreLease(coreLease.takeIf { currentSnapshot.running }),
            )
        }
    }

    private fun createSnapshot(
        service: Service,
        profileName: String,
        useHev: Boolean,
    ): HotspotRoutingSnapshot {
        val hevSettings = HevTunnelSettings.current()

        return HotspotRoutingSnapshot(
            running = true,
            vpnMode = service is CoreVpnService,
            profileName = profileName,
            useHev = useHev,
            ipv6Enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true,
            vpnDnsServers = SettingsManager.getVpnDnsServers(),
            socksPort = SettingsManager.getSocksPort(),
            socksUsername = SettingsManager.getSocksUsername(),
            socksPassword = SettingsManager.getSocksPassword(),
            mtu = SettingsManager.getVpnMtu(),
            hevTcpTimeoutSeconds = hevSettings.tcpTimeoutSeconds,
            hevUdpTimeoutSeconds = hevSettings.udpTimeoutSeconds,
            hevLogLevel = hevSettings.logLevel,
            launchId = Utils.getUuid(),
        )
    }

    private fun send(
        context: Context,
        event: Int,
        snapshot: HotspotRoutingSnapshot? = null,
        detail: String = "",
    ) {
        if (!context.resources.getBoolean(R.bool.shizuku_tethering_enabled)) return
        val token = MmkvManager.decodeSettingsString(AppConfig.PREF_SHIZUKU_SYNC_TOKEN)
            ?.takeIf { it.isNotBlank() }
            ?: run {
                LogUtil.i(AppConfig.TAG, "Tethering sync event $event skipped: no active Shizuku session")
                return
            }
        LogUtil.i(
            AppConfig.TAG,
            "Sending tethering sync event $event${snapshot?.profileName?.let { " for $it" }.orEmpty()}",
        )
        runCatching {
            context.sendBroadcast(
                Intent(context, ShizukuRoutingSyncReceiver::class.java)
                    .setPackage(AppConfig.ANG_PACKAGE)
                    .putExtra("content", HotspotRoutingSync(token, event, snapshot, detail))
                    .withCoreLease(coreLease.takeIf { snapshot != null }),
            )
        }.onFailure { LogUtil.e(AppConfig.TAG, "Unable to send tethering synchronization", it) }
    }

    private fun updateRecovery(update: (TetheringRecoveryState) -> TetheringRecoveryState) {
        synchronized(recoveryLock) { recoveryState = update(recoveryState) }
    }

    private fun transitionRecovery(
        update: (TetheringRecoveryState) -> TetheringRecoveryTransition,
    ): TetheringRecoveryAction = synchronized(recoveryLock) {
        val transition = update(recoveryState)
        recoveryState = transition.state
        transition.action
    }

    private fun safely(phase: String, profileId: String = activeProfileId, action: () -> Unit) {
        runCoreSyncHook(action) { error -> logFailure(phase, profileId, error) }
    }

    private fun logFailure(phase: String, profileId: String, error: Throwable) {
        runCatching {
            LogUtil.e(
                AppConfig.TAG,
                "Tethering sync failed: mode=shared-core phase=$phase profileId=$profileId operation=Shizuku synchronization",
                error,
            )
        }
    }
}

private class CoreTetheringLease : ICoreTetheringLease.Stub() {
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var tun: ParcelFileDescriptor? = null
    private data class Launch(val snapshot: HotspotRoutingSnapshot, val config: String)
    @Volatile private var launch: Launch? = null
    private var assetDirectory: File? = null
    private var configDirectory: File? = null

    @Synchronized
    fun attach(service: Service, snapshot: HotspotRoutingSnapshot, coreConfig: String) {
        connectivityManager = service.getSystemService(ConnectivityManager::class.java)
        launch = Launch(snapshot, coreConfig)
        assetDirectory = File(Utils.userAssetPath(service))
        configDirectory = service.cacheDir
    }

    @Synchronized
    fun clearEngineConfig() {
        launch = null
        assetDirectory = null
        configDirectory = null
    }

    override fun isCurrentLaunch(launchId: String): Boolean = launch?.snapshot?.launchId == launchId

    override fun openEngineConfig(launchId: String): ParcelFileDescriptor {
        val (current, directory) = synchronized(this) {
            val current = checkNotNull(launch) { "Core routing snapshot is unavailable" }
            check(current.snapshot.launchId == launchId) { "Core launch was superseded" }
            current to checkNotNull(configDirectory) { "Core cache directory is unavailable" }
        }
        // Capture one immutable launch, then do parsing/file I/O outside the lifecycle lock.
        // A delayed Binder request must never combine old metadata with a newer configuration.
        val temporary = File.createTempFile(ENGINE_CONFIG_FILE_PREFIX, null, directory)
        try {
            temporary.writeText(
                HotspotRoutingConfig.engineContentFromSnapshot(current.snapshot, current.config),
                Charsets.UTF_8,
            )
            return ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // Unix keeps the opened inode alive for the Binder recipient while removing the
            // credential-bearing configuration pathname immediately.
            temporary.delete()
        }
    }

    @Synchronized
    override fun assetFingerprint(): String = assetFiles().joinToString("|") {
        "${it.name}:${it.length()}:${it.lastModified()}"
    }

    @Synchronized
    override fun listAssetFiles(): Array<String> = assetFiles().map { it.name }.toTypedArray()

    @Synchronized
    override fun openAssetFile(name: String): ParcelFileDescriptor {
        require(name.isNotBlank() && File(name).name == name) { "Invalid asset name" }
        val file = File(checkNotNull(assetDirectory) { "Core asset directory is unavailable" }, name)
        require(file.isFile) { "Core asset is unavailable: $name" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun assetFiles(): List<File> = assetDirectory?.listFiles()
        ?.filter { it.isFile }
        ?.sortedBy { it.name }
        .orEmpty()

    @Synchronized
    override fun holdTestNetwork(tun: ParcelFileDescriptor) {
        releaseTestNetwork()
        val manager = checkNotNull(connectivityManager) { "Core network manager is unavailable" }
        val callback = ConnectivityManager.NetworkCallback()
        try {
            manager.requestNetwork(TetheringPlatformCompat.testNetworkRequest(), callback)
            networkCallback = callback
            this.tun = tun
        } catch (error: Throwable) {
            runCatching { tun.close() }
            throw error
        }
    }

    @Synchronized
    override fun releaseTestNetwork() {
        networkCallback?.let { callback ->
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        tun?.let { runCatching { it.close() } }
        tun = null
    }
}

private const val EXTRA_CORE_LEASE = "core_tethering_lease"
private const val ENGINE_CONFIG_FILE_PREFIX = "v2rayng-tethering-config-"

private fun Intent.withCoreLease(lease: ICoreTetheringLease?): Intent = apply {
    lease ?: return@apply
    putExtra(EXTRA_CORE_LEASE, Bundle().apply { putBinder(EXTRA_CORE_LEASE, lease.asBinder()) })
}

internal fun Intent.coreTetheringLease(): ICoreTetheringLease? =
    getBundleExtra(EXTRA_CORE_LEASE)?.getBinder(EXTRA_CORE_LEASE)
        ?.let(ICoreTetheringLease.Stub::asInterface)

internal enum class TetheringRecoveryAction {
    NONE,
    RECOVER,
    REQUEST_BINDER,
}

internal data class TetheringRecoveryTransition(
    val state: TetheringRecoveryState,
    val action: TetheringRecoveryAction = TetheringRecoveryAction.NONE,
)

internal data class TetheringRecoveryState(
    val coreRunning: Boolean = false,
    val recoverWhenShizukuReturns: Boolean = false,
    val foregroundRequestPending: Boolean = false,
) {
    fun onCoreStarted() = copy(coreRunning = true)

    fun onCoreStopped() = TetheringRecoveryState()

    fun onBinderDied() = copy(
        recoverWhenShizukuReturns = coreRunning,
        foregroundRequestPending = false,
    )

    fun onBinderReceived(): TetheringRecoveryTransition {
        val recover = coreRunning && recoverWhenShizukuReturns
        return TetheringRecoveryTransition(
            state = copy(
                recoverWhenShizukuReturns = false,
                foregroundRequestPending = false,
            ),
            action = if (recover) TetheringRecoveryAction.RECOVER else TetheringRecoveryAction.NONE,
        )
    }

    fun onAppForegrounded(sdkInt: Int): TetheringRecoveryTransition {
        val requestBinder = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            recoverWhenShizukuReturns && !foregroundRequestPending
        return TetheringRecoveryTransition(
            state = if (requestBinder) copy(foregroundRequestPending = true) else this,
            action = if (requestBinder) TetheringRecoveryAction.REQUEST_BINDER else TetheringRecoveryAction.NONE,
        )
    }

    fun onForegroundRequestFailed() = copy(foregroundRequestPending = false)
}

internal fun runCoreSyncHook(action: () -> Unit, onFailure: (Throwable) -> Unit): Boolean = try {
    action()
    true
} catch (error: Throwable) {
    runCatching { onFailure(error) }
    false
}
