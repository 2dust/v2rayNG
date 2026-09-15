package com.v2ray.ang.service

import com.google.gson.JsonParseException
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TcpingEndpointTest {
    private val custom = ProfileItem.create(EConfigType.CUSTOM).copy(
        server = "stale.example", serverPort = "1234",
    )
    private val server = """{"address":"example.com","port":443}"""
    private val single = """{"protocol":"vless","settings":{"vnext":[$server]}}"""

    private fun config(vararg outbounds: String) =
        """{"outbounds":[${outbounds.joinToString(",")}]}"""

    @Test
    fun singleLegacyAndFlatServersUseRawEndpointsInsteadOfStoredMetadata() {
        for (protocol in listOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http")) {
            val key = if (protocol in listOf("vmess", "vless")) "vnext" else "servers"
            for (settings in listOf(server, """{"$key":[$server]}""")) {
                val raw = config("""{"protocol":"$protocol","settings":$settings}""")
                assertEquals("example.com" to 443, resolveTcpingEndpoint(custom, raw))
                assertEquals("example.com" to 443, resolveTcpingEndpoint(custom.copy(server = null, serverPort = null), raw))
            }
        }
        assertEquals("stale.example", custom.server)
    }

    @Test
    fun directBlockAndDnsOutboundsDoNotHideTheSingleProxyServer() {
        val raw = config(single,
            """{"protocol":"freedom","settings":{}}""",
            """{"protocol":"blackhole","settings":{}}""",
            """{"protocol":"dns","settings":{}}""",
        )
        assertEquals("example.com" to 443, resolveTcpingEndpoint(custom, raw))
    }

    @Test
    fun multipleOutboundsOrLegacyServerEntriesRemainSkipped() {
        for (raw in listOf(
            config(single, single),
            config("""{"protocol":"vless","settings":{"vnext":[$server,$server]}}"""),
            config("""{"protocol":"socks","settings":{"servers":[$server,{}]}}"""),
            config("""{"protocol":"wireguard","settings":{"peers":[{"endpoint":"one.example:1"},{"endpoint":"two.example:2"}]}}"""),
        )) {
            assertNull(resolveTcpingEndpoint(custom, raw))
        }
    }

    @Test
    fun customEligibilityUsesTheEndpointRegardlessOfStoredProtocolHints() {
        val raw = config("""{"protocol":"wireguard","settings":{"peers":[{"endpoint":"[2001:db8::1]:51820"}]}}""")
        assertEquals("2001:db8::1" to 51820, resolveTcpingEndpoint(custom.copy(alpn = "h3"), raw))
    }

    @Test
    fun missingInvalidOrEmptyRawEndpointsNeverFallBackToStoredAddresses() {
        for (raw in listOf(null, "null", "{}", config(),
            config("""{"protocol":"vless","settings":{"address":"bad host","port":443}}"""),
            config("""{"protocol":"vless","settings":{"address":"999.0.0.1","port":443}}"""),
            config("""{"protocol":"freedom","settings":{}}"""),
        )) {
            assertNull(resolveTcpingEndpoint(custom, raw))
        }
    }

    @Test
    fun tcpProbesRequireAValidPortInAdditionToTheAddress() {
        for (port in listOf("null", "0", "65536", "-1", "443.5", "\"invalid\"")) {
            val raw = config("""{"protocol":"vless","settings":{"address":"example.com","port":$port}}""")
            assertNull(resolveTcpingEndpoint(custom, raw))
        }
        assertNull(resolveTcpingEndpoint(custom, config("""{"protocol":"vless","settings":{"address":"example.com"}}""")))
    }

    @Test
    fun malformedJsonIsReportedToTheWorkerForLogging() {
        for (raw in listOf("{", "[]", "true")) {
            assertThrows(JsonParseException::class.java) { resolveTcpingEndpoint(custom, raw) }
        }
    }

    @Test
    fun generatedProfilesKeepTheirExistingTcpEligibility() {
        val profile = ProfileItem.create(EConfigType.VLESS).copy(server = "normal.example", serverPort = "8443")
        assertEquals("normal.example" to 8443, resolveTcpingEndpoint(profile, "{"))
        assertEquals("normal.example" to 8443, resolveTcpingEndpoint(profile.copy(alpn = "h2,h3"), null))
        for (type in listOf(EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN, EConfigType.HYSTERIA2, EConfigType.WIREGUARD)) {
            assertNull(resolveTcpingEndpoint(profile.copy(configType = type), config(single)))
        }
        for (alpn in listOf("h3", " h3,h3-29 ")) {
            assertNull(resolveTcpingEndpoint(profile.copy(alpn = alpn), null))
        }
        assertNull(resolveTcpingEndpoint(profile.copy(server = ""), null))
        assertNull(resolveTcpingEndpoint(profile.copy(serverPort = "invalid"), null))
    }
}
