package com.v2ray.ang.shizuku

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
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
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/** Keeps the normal core's lifecycle and the privileged tethering core synchronized. */
internal object TetheringCoreSync {
    @Volatile
    private var snapshot = HotspotRoutingSnapshot()
    private val coreLease = CoreTetheringLease()
    private var watchingShizuku = false
    @Volatile
    private var recoverWhenShizukuReturns = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (!recoverWhenShizukuReturns) return@OnBinderReceivedListener
        recoverWhenShizukuReturns = false
        val currentSnapshot = snapshot.takeIf { it.running } ?: return@OnBinderReceivedListener
        LogUtil.i(AppConfig.TAG, "Shizuku restarted; recovering protected tethering")
        send(AngApplication.application, HotspotRoutingSync.EVENT_CORE_STARTED, currentSnapshot)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        recoverWhenShizukuReturns = snapshot.running
    }

    fun onStarting() {
        clearCoreState()
    }

    fun onStarted(
        service: Service,
        profileName: String,
        coreConfig: String,
        useHev: Boolean,
    ) {
        if (!service.resources.getBoolean(R.bool.shizuku_tethering_enabled)) return
        val currentSnapshot = createSnapshot(service, profileName, useHev)
        coreLease.attach(service, currentSnapshot, coreConfig)
        snapshot = currentSnapshot
        watchShizuku(service)
        send(service, HotspotRoutingSync.EVENT_CORE_STARTED, snapshot)
    }

    fun onStartFailed(service: Service, detail: String) {
        send(service, HotspotRoutingSync.EVENT_CORE_START_FAILED, detail = detail)
    }

    fun onStopping(service: Service) {
        send(service, HotspotRoutingSync.EVENT_CORE_STOPPING)
        clearCoreState()
    }

    fun clear() = clearCoreState()

    private fun clearCoreState() {
        snapshot = HotspotRoutingSnapshot()
        coreLease.clearEngineConfig()
    }

    private fun watchShizuku(service: Service) {
        if (watchingShizuku) return
        watchingShizuku = true
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        recoverWhenShizukuReturns = !Shizuku.pingBinder()
        ShizukuProvider.requestBinderForNonProviderProcess(service)
    }

    fun sendCurrentSnapshot(service: Service, coreRunning: Boolean) {
        val currentSnapshot = snapshot.takeIf { coreRunning } ?: HotspotRoutingSnapshot()
        service.sendBroadcast(
            Intent(AppConfig.BROADCAST_ACTION_ACTIVITY)
                .setPackage(AppConfig.ANG_PACKAGE)
                .putExtra("key", AppConfig.MSG_HOTSPOT_CONFIG_RESPONSE)
                .putExtra("content", currentSnapshot)
                .withCoreLease(coreLease.takeIf { currentSnapshot.running }),
        )
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
                    .putExtra("content", HotspotRoutingSync(token, event, snapshot, detail))
                    .withCoreLease(coreLease.takeIf { snapshot != null }),
            )
        }.onFailure { LogUtil.e(AppConfig.TAG, "Unable to send tethering synchronization", it) }
    }
}

private class CoreTetheringLease : ICoreTetheringLease.Stub() {
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var tun: ParcelFileDescriptor? = null
    private var routingSnapshot: HotspotRoutingSnapshot? = null
    private var coreConfig: String? = null

    @Synchronized
    fun attach(service: Service, snapshot: HotspotRoutingSnapshot, coreConfig: String) {
        connectivityManager = service.getSystemService(ConnectivityManager::class.java)
        routingSnapshot = snapshot
        this.coreConfig = coreConfig
    }

    @Synchronized
    fun clearEngineConfig() {
        routingSnapshot = null
        coreConfig = null
    }

    @Synchronized
    override fun openEngineConfig(): ParcelFileDescriptor {
        val snapshot = checkNotNull(routingSnapshot) { "Core routing snapshot is unavailable" }
        val rawConfig = checkNotNull(coreConfig) { "Core configuration is unavailable" }
        val content = HotspotRoutingConfig.engineContentFromSnapshot(snapshot, rawConfig)
        val (readSide, writeSide) = ParcelFileDescriptor.createReliablePipe()
        // A writer thread is necessary because writing a large config before returning the read
        // descriptor could fill the pipe and deadlock this Binder call.
        Thread({
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onFailure { LogUtil.e(AppConfig.TAG, "Unable to stream tethering configuration", it) }
        }, ENGINE_CONFIG_WRITER_NAME).apply {
            isDaemon = true
            start()
        }
        return readSide
    }

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
private const val ENGINE_CONFIG_WRITER_NAME = "TetheringConfigWriter"

private fun Intent.withCoreLease(lease: ICoreTetheringLease?): Intent = apply {
    lease ?: return@apply
    putExtra(EXTRA_CORE_LEASE, Bundle().apply { putBinder(EXTRA_CORE_LEASE, lease.asBinder()) })
}

internal fun Intent.coreTetheringLease(): ICoreTetheringLease? =
    getBundleExtra(EXTRA_CORE_LEASE)?.getBinder(EXTRA_CORE_LEASE)
        ?.let(ICoreTetheringLease.Stub::asInterface)
