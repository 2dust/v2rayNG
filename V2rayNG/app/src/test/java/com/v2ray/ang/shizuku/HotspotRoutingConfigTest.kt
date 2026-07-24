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
        val config = HotspotRoutingConfig.engineFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                profileName = "Native",
                coreConfig = """
                    {
                      "inbounds": [
                        {"tag": "socks", "protocol": "socks"},
                        {"tag": "tun", "protocol": "tun"}
                      ],
                      "outbounds": [{"tag": "proxy", "protocol": "freedom"}],
                      "routing": {"domainStrategy": "AsIs"}
                    }
                """.trimIndent(),
            ),
        )

        assertFalse(config.useHev)
        assertEquals("Native", config.profileName)
        val root = JsonParser.parseString(config.content).asJsonObject
        val inbounds = root.getAsJsonArray("inbounds")
        assertEquals(1, inbounds.size())
        assertEquals("tun", inbounds[0].asJsonObject.get("protocol").asString)
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("routing"))
    }

    @Test
    fun hevEngineUsesTheRunningSnapshotSettings() {
        val config = HotspotRoutingConfig.engineFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                profileName = "HEV",
                useHev = true,
                coreConfig = "not used by HEV",
                socksPort = 10808,
                socksUsername = "o'connor",
                socksPassword = "p'ass",
                mtu = 1500,
                hevTcpTimeoutSeconds = 5,
                hevUdpTimeoutSeconds = 7,
                hevLogLevel = "w'arn",
            ),
        )

        assertTrue(config.useHev)
        assertEquals("HEV", config.profileName)
        assertTrue(config.content.contains("port: 10808"))
        assertTrue(config.content.contains("username: 'o''connor'"))
        assertTrue(config.content.contains("password: 'p''ass'"))
        assertTrue(config.content.contains("tcp-read-write-timeout: 5000"))
        assertTrue(config.content.contains("udp-read-write-timeout: 7000"))
        assertTrue(config.content.contains("log-level: 'w''arn'"))
        assertFalse(config.content.contains("  ipv6:"))
    }

    @Test
    fun hevEngineAddsIpv6OnlyWhenEnabled() {
        val config = HotspotRoutingConfig.engineFromSnapshot(
            HotspotRoutingSnapshot(
                running = true,
                vpnMode = true,
                useHev = true,
                ipv6Enabled = true,
            ),
        )

        assertTrue(config.content.contains("  ipv6: '${AppConfig.SHIZUKU_TUN_ADDR_V6.substringBefore('/')}'"))
    }
}
