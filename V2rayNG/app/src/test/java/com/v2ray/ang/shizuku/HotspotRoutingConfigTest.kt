package com.v2ray.ang.shizuku

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.HotspotRoutingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotRoutingConfigTest {

    @Test
    fun nativeEngineKeepsOnlyTheTunInbound() {
        val config = HotspotRoutingConfig.engineContentFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                profileName = "Native",
            ),
            """
                {
                  "inbounds": [
                    {"tag": "socks", "protocol": "socks"},
                    {"tag": "tun", "protocol": "tun"}
                  ],
                  "outbounds": [{"tag": "proxy", "protocol": "freedom"}],
                  "routing": {"domainStrategy": "AsIs"}
                }
            """.trimIndent(),
        )

        val root = JsonParser.parseString(config).asJsonObject
        val inbounds = root.getAsJsonArray("inbounds")
        assertEquals(1, inbounds.size())
        assertEquals("tun", inbounds[0].asJsonObject.get("protocol").asString)
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("routing"))
    }

    @Test
    fun hevEngineUsesTheRunningSnapshotSettings() {
        val config = HotspotRoutingConfig.engineContentFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                profileName = "HEV",
                useHev = true,
                socksPort = 10808,
                socksUsername = "o'connor",
                socksPassword = "p'ass",
                mtu = 1500,
                hevTcpTimeoutSeconds = 5,
                hevUdpTimeoutSeconds = 7,
                hevLogLevel = "w'arn",
            ),
            "not used by HEV",
        )

        assertTrue(config.contains("port: 10808"))
        assertTrue(config.contains("username: 'o''connor'"))
        assertTrue(config.contains("password: 'p''ass'"))
        assertTrue(config.contains("tcp-read-write-timeout: 5000"))
        assertTrue(config.contains("udp-read-write-timeout: 7000"))
        assertTrue(config.contains("log-level: 'w''arn'"))
        assertFalse(config.contains("  ipv6:"))
    }

    @Test
    fun hevEngineAddsIpv6OnlyWhenEnabled() {
        val config = HotspotRoutingConfig.engineContentFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                useHev = true,
                ipv6Enabled = true,
            ),
            "not used by HEV",
        )

        assertTrue(config.contains("  ipv6: '${AppConfig.SHIZUKU_TUN_ADDR_V6.substringBefore('/')}'"))
    }
}
