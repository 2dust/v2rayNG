package com.v2ray.ang.shizuku

import android.content.Context
import com.google.gson.JsonArray
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import com.v2ray.ang.service.HevTunnelConfig
import com.v2ray.ang.service.HevTunnelParameters
import com.v2ray.ang.service.HevTunnelSettings
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

data class HotspotRoutingEngineConfig(
    val useHev: Boolean,
    val profileName: String,
    val content: String,
)

data class HotspotRoutingLaunchConfig(
    val engine: HotspotRoutingEngineConfig,
    val assetPath: String,
    val xudpKey: String,
)

/** Builds the privileged datapath configuration from the exact running-core snapshot. */
object HotspotRoutingConfig {

    fun launchFromSnapshot(context: Context, snapshot: HotspotRoutingSnapshot): HotspotRoutingLaunchConfig =
        HotspotRoutingLaunchConfig(
            engine = engineFromSnapshot(snapshot),
            assetPath = Utils.userAssetPath(context),
            xudpKey = Utils.getDeviceIdForXUDPBaseKey(),
        )

    fun engineFromSnapshot(snapshot: HotspotRoutingSnapshot): HotspotRoutingEngineConfig {
        require(snapshot.running) { "Start v2rayNG before enabling tethering routing" }
        require(snapshot.vpnMode) { "v2rayNG must be running in VPN mode" }

        return HotspotRoutingEngineConfig(
            useHev = snapshot.useHev,
            profileName = snapshot.profileName,
            content = if (snapshot.useHev) {
                buildHevConfig(snapshot)
            } else {
                nativeTunOnlyConfig(snapshot.coreConfig)
            },
        )
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
