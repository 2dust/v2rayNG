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
    val running: Boolean,
    val vpnMode: Boolean,
    val profileName: String,
    val useHev: Boolean,
    val coreConfig: String,
    val socksPort: Int,
    val socksUsername: String?,
    val socksPassword: String?,
    val mtu: Int,
    val hevTcpTimeoutSeconds: Int,
    val hevUdpTimeoutSeconds: Int,
    val hevLogLevel: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 2L

        fun stopped() = HotspotRoutingSnapshot(
            running = false,
            vpnMode = false,
            profileName = "",
            useHev = false,
            coreConfig = "",
            socksPort = 0,
            socksUsername = null,
            socksPassword = null,
            mtu = 0,
            hevTcpTimeoutSeconds = 0,
            hevUdpTimeoutSeconds = 0,
            hevLogLevel = "warn",
        )
    }
}
