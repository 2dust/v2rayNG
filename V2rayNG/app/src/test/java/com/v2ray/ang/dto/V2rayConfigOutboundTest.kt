package com.v2ray.ang.dto

import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [V2rayConfig.OutboundBean] endpoint lookup.
 *
 * Profiles created in the app write the endpoint as a flat `settings.address`/`settings.port`
 * pair, while custom configurations may keep the classic v2ray layout, where the endpoint lives
 * in `settings.vnext` (VMess/VLESS) or `settings.servers` (HTTP/SOCKS/Shadowsocks).
 */
class V2rayConfigOutboundTest {

    /**
     * Parses a single outbound wrapped in a minimal, otherwise empty configuration.
     */
    private fun parseOutbound(outbound: String): V2rayConfig.OutboundBean? {
        val config = JsonUtil.fromJson(
            """
            {
              "log": {"loglevel": "warning"},
              "inbounds": [],
              "outbounds": [$outbound],
              "routing": {"domainStrategy": "AsIs", "rules": []}
            }
            """.trimIndent(),
            V2rayConfig::class.java
        )
        return config?.outbounds?.firstOrNull()
    }

    @Test
    fun getServerAddress_readsFlatLayout() {
        val outbound = parseOutbound(
            """{"protocol": "vmess", "settings": {"address": "1.2.3.4", "port": 443}}"""
        )

        assertEquals("1.2.3.4", outbound?.getServerAddress())
        assertEquals(443, outbound?.getServerPort())
    }

    @Test
    fun getServerAddress_readsClassicVnextLayout() {
        val outbound = parseOutbound(
            """
            {
              "protocol": "vmess",
              "settings": {
                "vnext": [{"address": "1.2.3.4", "port": 443, "users": [{"id": "example-id"}]}]
              }
            }
            """.trimIndent()
        )

        assertEquals("1.2.3.4", outbound?.getServerAddress())
        assertEquals(443, outbound?.getServerPort())
    }

    @Test
    fun getServerAddress_readsClassicServersLayout() {
        val outbound = parseOutbound(
            """
            {
              "protocol": "http",
              "settings": {
                "servers": [{"address": "1.2.3.4", "port": 8080, "users": [{"user": "example-user", "pass": "example-pass"}]}]
              }
            }
            """.trimIndent()
        )

        assertEquals("1.2.3.4", outbound?.getServerAddress())
        assertEquals(8080, outbound?.getServerPort())
    }

    @Test
    fun getServerAddress_prefersFlatLayout() {
        val outbound = parseOutbound(
            """
            {
              "protocol": "http",
              "settings": {
                "address": "5.6.7.8",
                "port": 1080,
                "servers": [{"address": "1.2.3.4", "port": 8080}]
              }
            }
            """.trimIndent()
        )

        assertEquals("5.6.7.8", outbound?.getServerAddress())
        assertEquals(1080, outbound?.getServerPort())
    }

    @Test
    fun getServerAddress_returnsNullWithoutEndpoint() {
        val outbound = parseOutbound("""{"protocol": "freedom", "settings": {}}""")

        assertNull(outbound?.getServerAddress())
        assertNull(outbound?.getServerPort())
    }
}
