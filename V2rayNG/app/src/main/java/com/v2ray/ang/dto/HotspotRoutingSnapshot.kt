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
    val coreConfig: String = "",
    val socksPort: Int = 0,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val mtu: Int = 0,
    val hevTcpTimeoutSeconds: Int = 0,
    val hevUdpTimeoutSeconds: Int = 0,
    val hevLogLevel: String = "warn",
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L
    }
}
