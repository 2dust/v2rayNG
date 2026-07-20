package com.v2ray.ang.shizuku

import android.app.Service
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.dto.HotspotRoutingSync
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.service.CoreVpnService
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil

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
        val timeoutSetting = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT)
            ?: AppConfig.HEVTUN_RW_TIMEOUT
        val timeoutParts = timeoutSetting.split(',').map { it.trim() }

        return HotspotRoutingSnapshot(
            running = true,
            vpnMode = service is CoreVpnService,
            profileName = profileName,
            useHev = useHev,
            coreConfig = coreConfig,
            socksPort = SettingsManager.getSocksPort(),
            socksUsername = SettingsManager.getSocksUsername(),
            socksPassword = SettingsManager.getSocksPassword(),
            mtu = SettingsManager.getVpnMtu(),
            hevTcpTimeoutSeconds = timeoutParts.getOrNull(0)?.toIntOrNull() ?: 300,
            hevUdpTimeoutSeconds = timeoutParts.getOrNull(1)?.toIntOrNull() ?: 60,
            hevLogLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL)
                ?: "warn",
        )
    }

    private fun send(
        service: Service,
        event: Int,
        snapshot: HotspotRoutingSnapshot? = null,
        detail: String = "",
    ) {
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
        MessageUtil.sendMsg2Shizuku(
            service,
            AppConfig.MSG_HOTSPOT_SYNC,
            HotspotRoutingSync(token, event, snapshot, detail),
        )
    }
}
