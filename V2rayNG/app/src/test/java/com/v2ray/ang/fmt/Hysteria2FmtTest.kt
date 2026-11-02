package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class Hysteria2FmtTest {

    @Test
    fun hysteria2WithoutPortUses443() {
        val profile = Hysteria2Fmt.parse("hysteria2://test@example.com/")

        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun hy2WithoutPortUses443() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/")

        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun uriWithoutCredentialsUses443() {
        val profile = Hysteria2Fmt.parse("hysteria2://example.com/")

        assertEquals("example.com", profile.server)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun omittedPortPreservesTlsParameters() {
        val profile = Hysteria2Fmt.parse(
            "hy2://test@example.com/?sni=example.org&insecure=0&pinSHA256=test-pin#example"
        )

        assertEquals("test", profile.password)
        assertEquals("example", profile.remarks)
        assertEquals(AppConfig.TLS, profile.security)
        assertEquals("example.org", profile.sni)
        assertEquals(false, profile.insecure)
        assertEquals("test-pin", profile.pinnedCA256)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun ipv6WithoutPortUses443() {
        val profile = Hysteria2Fmt.parse("hysteria2://test@[2001:db8::1]/")

        assertEquals("2001:db8::1", profile.server)
        assertEquals("443", profile.serverPort)
    }

    @Test
    fun explicitPortsArePreserved() {
        for (scheme in listOf("hysteria2", "hy2")) {
            // Port range validation is separate from choosing a missing-port default.
            for (port in listOf(0, 443, 8443, 65535, 65536)) {
                val profile = Hysteria2Fmt.parse("$scheme://test@example.com:$port/")

                assertEquals("example.com", profile.server)
                assertEquals(port.toString(), profile.serverPort)
            }
        }
    }

    @Test
    fun nonnumericPortDoesNotAcquireAHost() {
        val profile = Hysteria2Fmt.parse("hysteria2://test@example.com:invalid/")

        assertEquals("", profile.server)
    }

    @Test
    fun defaultPortSurvivesUriRoundTrip() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/?sni=example.org")
        val exported = "hysteria2://${Hysteria2Fmt.toUri(profile)}"
        val reparsed = Hysteria2Fmt.parse(exported)

        assertEquals(443, URI(exported).port)
        assertEquals("443", reparsed.serverPort)
        assertEquals(profile.server, reparsed.server)
        assertEquals(profile.password, reparsed.password)
        assertEquals(profile.sni, reparsed.sni)
    }
}
