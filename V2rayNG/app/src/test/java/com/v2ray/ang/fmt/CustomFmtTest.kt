package com.v2ray.ang.fmt

import com.google.gson.JsonParseException
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CustomFmtTest {
    private fun config(vararg outbounds: String): String =
        """{"remarks":"Custom test","outbounds":[${outbounds.joinToString(",")}]}"""

    private fun outbound(protocol: String, settings: String): String =
        """{"protocol":"$protocol","settings":$settings}"""

    private val server = """{"address":"example.com","port":443}"""

    @Test
    fun readsLegacyAndFlatSettingsForEverySupportedServerProtocol() {
        for (protocol in listOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http")) {
            val key = if (protocol in listOf("vmess", "vless")) "vnext" else "servers"
            for (settings in listOf(server, """{"$key":[$server]}""")) {
                val raw = config(outbound(protocol, settings))
                val metadata = CustomFmt.parseMetadata(raw)
                assertEquals(protocol, 1, metadata.serverCount)
                assertEquals(protocol, "example.com", metadata.server)
                assertEquals(protocol, "443", metadata.serverPort)
                val profile = CustomFmt.parse(raw)
                assertEquals(EConfigType.CUSTOM, profile.configType)
                assertEquals("Custom test", profile.remarks)
                assertEquals(metadata.server, profile.server)
                assertEquals(metadata.serverPort, profile.serverPort)
            }
        }
        for (protocol in listOf("hysteria", "hysteria2", "VLESS")) {
            assertEquals("example.com", CustomFmt.parse(config(outbound(protocol, server))).server)
        }
    }

    @Test
    fun ignoresDirectBlockAndDnsOutbounds() {
        val metadata = CustomFmt.parseMetadata(config(
            outbound("freedom", server),
            outbound("blackhole", "{}"),
            outbound("dns", server),
            outbound("vless", server),
        ))
        assertEquals(1, metadata.serverCount)
        assertEquals("example.com", metadata.server)
    }

    @Test
    fun multipleOutboundsDoNotSelectTheFirstServer() {
        val metadata = CustomFmt.parseMetadata(config(outbound("vless", server), outbound("trojan", server)))
        assertEquals(2, metadata.serverCount)
        assertNull(metadata.server)
        assertNull(metadata.serverPort)
    }

    @Test
    fun countsEveryServerInsideLegacyArraysEvenIfAddressesRepeatOrAreInvalid() {
        for ((protocol, key) in listOf("vless" to "vnext", "socks" to "servers")) {
            for (second in listOf(server, "{}", "null")) {
                val metadata = CustomFmt.parseMetadata(config(outbound(protocol, """{"$key":[$server,$second]}""")))
                assertEquals(2, metadata.serverCount)
                assertNull(metadata.server)
                assertNull(metadata.serverPort)
            }
        }
    }

    @Test
    fun flatSettingsTakePrecedenceOverLegacyArrays() {
        val metadata = CustomFmt.parseMetadata(config(outbound("vless",
            """{"address":"flat.example","port":8443,"vnext":[$server,$server]}""")))
        assertEquals(1, metadata.serverCount)
        assertEquals("flat.example", metadata.server)
        assertEquals("8443", metadata.serverPort)
    }

    @Test
    fun wireGuardUsesPeersRatherThanLocalAddresses() {
        for (endpoint in listOf("example.com:51820", "[2001:db8::1]:51820")) {
            val raw = config(outbound("wireguard", """{"address":["10.0.0.1/32"],"peers":[{"endpoint":"$endpoint"}]}"""))
            val metadata = CustomFmt.parseMetadata(raw)
            assertEquals(1, metadata.serverCount)
            assertEquals(endpoint.substringBeforeLast(':').removeSurrounding("[", "]"), metadata.server)
            assertEquals("51820", metadata.serverPort)
        }
        val multiple = CustomFmt.parseMetadata(config(outbound("wireguard",
            """{"peers":[{"endpoint":"one.example:1"},{"endpoint":"two.example:2"}]}""")))
        assertEquals(2, multiple.serverCount)
        assertNull(multiple.server)
        assertNull(multiple.serverPort)
    }

    @Test
    fun acceptsIpLiteralsLocalAndInternationalHostnamesWithoutResolvingThem() {
        for (address in listOf("192.0.2.1", "2001:db8::1", "[2001:db8::1]", "localhost", "例子.测试", "example.com.")) {
            assertEquals(address, CustomFmt.parse(config(outbound("vless", """{"address":"$address","port":443}"""))).server)
        }
    }

    @Test
    fun invalidAddressDoesNotLeakPartialMetadata() {
        for (address in listOf("", " ", "bad host", "https://example.com", "user@example.com", "example.com/path", "-bad.example", "bad..example", "999.0.0.1", "[invalid::ip]")) {
            val metadata = CustomFmt.parseMetadata(config(outbound("vless", """{"address":"$address","port":443}""")))
            assertNull(address, metadata.server)
            assertNull(address, metadata.serverPort)
        }
        for (value in listOf("null", "false", "42", "[]", "{}")) {
            assertNull(CustomFmt.parse(config(outbound("vless", """{"address":$value,"port":443}"""))).server)
        }
    }

    @Test
    fun invalidOrAbsentPortIsNotDisplayedButValidAddressRemains() {
        for (port in listOf("null", "0", "65536", "-1", "443.5", "false", "{}", "\"invalid\"")) {
            val metadata = CustomFmt.parseMetadata(config(outbound("vless", """{"address":"example.com","port":$port}""")))
            assertEquals("example.com", metadata.server)
            assertNull(metadata.serverPort)
        }
        assertNull(CustomFmt.parse(config(outbound("vless", """{"address":"example.com"}"""))).serverPort)
    }

    @Test
    fun emptyOrUnrecognizedConfigurationsHaveNoServer() {
        for (raw in listOf("{}", "null", config(), config(outbound("freedom", "{}")), """{"outbounds":[null,42,{}]}""")) {
            val metadata = CustomFmt.parseMetadata(raw)
            assertEquals(0, metadata.serverCount)
            assertNull(metadata.server)
            assertNull(metadata.serverPort)
        }
        assertNull(CustomFmt.parse(config(outbound("vless", "{}"))).server)
    }

    @Test
    fun malformedJsonStillFailsImportValidation() {
        for (raw in listOf("{", "[]", "true")) {
            assertThrows(JsonParseException::class.java) { CustomFmt.parse(raw) }
        }
    }
}
