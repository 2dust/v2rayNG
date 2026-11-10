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

    @Test
    fun geckoObfsDefaultsPacketSizeWhenOmitted() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/?obfs=gecko&obfs-password=secret")

        assertEquals("gecko", profile.obfs)
        assertEquals("secret", profile.obfsPassword)
        assertEquals("512", profile.minPacketSize)
        assertEquals("1200", profile.maxPacketSize)
    }

    @Test
    fun geckoObfsCustomPacketSizePreserved() {
        val profile = Hysteria2Fmt.parse(
            "hy2://test@example.com/?obfs=gecko&obfs-password=secret&minPacketSize=600&maxPacketSize=1300"
        )

        assertEquals("gecko", profile.obfs)
        assertEquals("secret", profile.obfsPassword)
        assertEquals("600", profile.minPacketSize)
        assertEquals("1300", profile.maxPacketSize)
    }

    @Test
    fun geckoObfsSurvivesUriRoundTrip() {
        val profile = Hysteria2Fmt.parse(
            "hy2://test@example.com/?obfs=gecko&obfs-password=secret&minPacketSize=600&maxPacketSize=1300"
        )
        val exported = "hysteria2://${Hysteria2Fmt.toUri(profile)}"
        val reparsed = Hysteria2Fmt.parse(exported)

        assertEquals("gecko", reparsed.obfs)
        assertEquals("secret", reparsed.obfsPassword)
        assertEquals("600", reparsed.minPacketSize)
        assertEquals("1300", reparsed.maxPacketSize)
    }

    @Test
    fun salamanderObfsDoesNotSetPacketSize() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/?obfs=salamander&obfs-password=secret")

        assertEquals("salamander", profile.obfs)
        assertEquals("secret", profile.obfsPassword)
        assertEquals(null, profile.minPacketSize)
        assertEquals(null, profile.maxPacketSize)
    }

    @Test
    fun obfsNoneDoesNotSetObfs() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/?obfs=none&obfs-password=secret")

        assertEquals(null, profile.obfs)
    }

    @Test
    fun noObfsSurvivesUriRoundTrip() {
        val profile = Hysteria2Fmt.parse("hy2://test@example.com/?sni=example.com")
        val exported = "hysteria2://${Hysteria2Fmt.toUri(profile)}"
        val reparsed = Hysteria2Fmt.parse(exported)

        assertEquals(null, reparsed.obfs)
        assertEquals(null, reparsed.obfsPassword)
        assertEquals(null, reparsed.minPacketSize)
        assertEquals(null, reparsed.maxPacketSize)
    }
}
