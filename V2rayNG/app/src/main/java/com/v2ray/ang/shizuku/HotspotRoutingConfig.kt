package com.v2ray.ang.shizuku

import com.google.gson.JsonArray
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.service.HevTunnelConfig
import com.v2ray.ang.service.HevTunnelParameters
import com.v2ray.ang.service.HevTunnelSettings
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

internal data class HotspotRoutingEngineConfig(
    val useHev: Boolean,
    val profileName: String,
    val content: String,
)

internal data class HotspotRoutingLaunchConfig(
    val engine: HotspotRoutingEngineConfig,
    val dnsServers: List<String>,
    val ipv6Enabled: Boolean,
    val xudpKey: String,
)

internal data class HotspotRoutingParameters(
    val useHev: Boolean,
    val profileName: String,
    val dnsServers: List<String>,
    val ipv6Enabled: Boolean,
    val xudpKey: String,
)

/** Builds the privileged datapath configuration from the exact running-core snapshot. */
internal object HotspotRoutingConfig {

    fun parametersFromSnapshot(snapshot: HotspotRoutingSnapshot): HotspotRoutingParameters {
        requireRoutableSnapshot(snapshot)
        return HotspotRoutingParameters(
            useHev = snapshot.useHev,
            profileName = snapshot.profileName,
            dnsServers = snapshot.vpnDnsServers,
            ipv6Enabled = snapshot.ipv6Enabled,
            xudpKey = Utils.getDeviceIdForXUDPBaseKey(),
        )
    }

    fun engineContentFromSnapshot(snapshot: HotspotRoutingSnapshot, coreConfig: String): String {
        requireRoutableSnapshot(snapshot)
        return if (snapshot.useHev) buildHevConfig(snapshot) else nativeTunOnlyConfig(coreConfig)
    }

    private fun requireRoutableSnapshot(snapshot: HotspotRoutingSnapshot) {
        require(snapshot.running) { "Start v2rayNG before enabling tethering routing" }
        require(snapshot.vpnMode) { "v2rayNG must be running in VPN mode" }
    }

    private fun nativeTunOnlyConfig(rawConfig: String): String {
        val root = JsonUtil.parseString(rawConfig)
            ?: error("The running Xray configuration is invalid")
        val inbounds = root.get("inbounds")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: error("The running Xray configuration has no inbounds")
        val tunInbounds = JsonArray()
        inbounds.forEach { inbound ->
            val protocol = inbound.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("protocol")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
            if (protocol == "tun") tunInbounds.add(inbound.deepCopy())
        }
        require(tunInbounds.size() > 0) { "The running Xray configuration has no TUN inbound" }

        // The normal core already owns its SOCKS/HTTP listener ports. The hotspot core only
        // needs the TUN inbound; all outbounds, DNS, routing, balancing and observatory sections
        // are intentionally preserved byte-for-byte at the JSON model level.
        root.add("inbounds", tunInbounds)
        return JsonUtil.toJsonPretty(root) ?: error("Unable to serialize the hotspot configuration")
    }

    private fun buildHevConfig(snapshot: HotspotRoutingSnapshot): String {
        return HevTunnelConfig.build(
            HevTunnelParameters(
                mtu = snapshot.mtu,
                ipv4 = AppConfig.SHIZUKU_TUN_ADDR_V4.substringBefore('/'),
                ipv6 = AppConfig.SHIZUKU_TUN_ADDR_V6.substringBefore('/')
                    .takeIf { snapshot.ipv6Enabled },
                socksAddress = AppConfig.LOOPBACK,
                socksPort = snapshot.socksPort,
                socksUsername = snapshot.socksUsername,
                socksPassword = snapshot.socksPassword,
                settings = HevTunnelSettings(
                    tcpTimeoutSeconds = snapshot.hevTcpTimeoutSeconds,
                    udpTimeoutSeconds = snapshot.hevUdpTimeoutSeconds,
                    logLevel = snapshot.hevLogLevel,
                ),
            ),
        )
    }
}
