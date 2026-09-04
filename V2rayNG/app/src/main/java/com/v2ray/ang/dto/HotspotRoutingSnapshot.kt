package com.v2ray.ang.dto

import java.io.Serializable

/**
 * Exact launch parameters captured by the running core process for Shizuku tethering.
 *
 * The normal core lives in a dedicated Android process. Sending this snapshot over the existing
 * app-scoped broadcast channel avoids regenerating a configuration from settings that may have
 * changed since that core was started.
 */
data class HotspotRoutingSnapshot(
    val running: Boolean = false,
    val vpnMode: Boolean = false,
    val profileName: String = "",
    val useHev: Boolean = false,
    val ipv6Enabled: Boolean = false,
    // This is the same IP-only list advertised by CoreVpnService. When local DNS is enabled,
    // the generated core configuration intercepts its port-53 traffic and sends it to dns-out;
    // otherwise the configured addresses remain ordinary routed DNS destinations.
    val vpnDnsServers: List<String> = emptyList(),
    val socksPort: Int = 0,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val mtu: Int = 0,
    val hevTcpTimeoutSeconds: Int = 0,
    val hevUdpTimeoutSeconds: Int = 0,
    val hevLogLevel: String = "warn",
    // Unlike the tethering session token, this identity changes on every main-core launch.
    // Nullable for broadcasts from an older app process during an APK update. Such snapshots
    // cannot authorize a new engine; request fresh launch metadata instead.
    val launchId: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 5L
    }
}
