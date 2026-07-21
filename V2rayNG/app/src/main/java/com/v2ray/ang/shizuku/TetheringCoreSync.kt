package com.v2ray.ang.shizuku

import android.app.Service
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.service.HevTunnelSettings
import com.v2ray.ang.util.LogUtil

/** Keeps the normal core's lifecycle and the privileged tethering core synchronized. */
internal object TetheringCoreSync {
    @Volatile
    private var snapshot = HotspotRoutingSnapshot()

    fun onStarting() {
        snapshot = HotspotRoutingSnapshot()
    }

    fun onStarted(
        service: Service,
        profileName: String,
        coreConfig: String,
        useHev: Boolean,
    ) {
        if (!service.resources.getBoolean(R.bool.shizuku_tethering_enabled)) return
        snapshot = createSnapshot(service, profileName, coreConfig, useHev)
        send(service, HotspotRoutingSync.EVENT_CORE_STARTED, snapshot)
    }

    fun onStartFailed(service: Service, detail: String) {
        send(service, HotspotRoutingSync.EVENT_CORE_START_FAILED, detail = detail)
    }

    fun onStopping(service: Service) {
        send(service, HotspotRoutingSync.EVENT_CORE_STOPPING)
        snapshot = HotspotRoutingSnapshot()
    }

    fun clear() {
        snapshot = HotspotRoutingSnapshot()
    }

    fun currentSnapshot(coreRunning: Boolean): HotspotRoutingSnapshot =
        snapshot.takeIf { coreRunning } ?: HotspotRoutingSnapshot()

    private fun createSnapshot(
        service: Service,
        profileName: String,
        coreConfig: String,
        useHev: Boolean,
    ): HotspotRoutingSnapshot {
        val hevSettings = HevTunnelSettings.current()

        return HotspotRoutingSnapshot(
            running = true,
            vpnMode = service is CoreVpnService,
            profileName = profileName,
            useHev = useHev,
            coreConfig = coreConfig,
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
        service: Service,
        event: Int,
        snapshot: HotspotRoutingSnapshot? = null,
        detail: String = "",
    ) {
        if (!service.resources.getBoolean(R.bool.shizuku_tethering_enabled)) return
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
            service.sendBroadcast(
                Intent(service, ShizukuRoutingSyncReceiver::class.java)
                    .putExtra("content", HotspotRoutingSync(token, event, snapshot, detail)),
            )
        }.onFailure { LogUtil.e(AppConfig.TAG, "Unable to send tethering synchronization", it) }
    }
}
