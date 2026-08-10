package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayOutboundCompatibilityTest {
    private val validVlessEncryption =
        "mlkem768x25519plus.native.0rtt.2PcBa3Yz0zBdt4p8-PkJMzx9hIj2Ve-UmrnmZRPnpRk"

    @Test
    fun unencryptedVlessToPublicEndpointIsDeprecated() {
        assertTrue(isDeprecated(EConfigType.VLESS, server = "example.com"))
        assertTrue(isDeprecated(EConfigType.VLESS, server = "8.8.8.8", method = "none"))
        assertTrue(isDeprecated(EConfigType.VLESS, server = "2001:4860:4860::8888"))
    }

    @Test
    fun vlessTransportOrProtocolEncryptionRemainsSupported() {
        assertFalse(isDeprecated(EConfigType.VLESS, server = "example.com", security = AppConfig.TLS))
        assertFalse(isDeprecated(EConfigType.VLESS, server = "example.com", security = AppConfig.REALITY))
        assertFalse(
            isDeprecated(
                EConfigType.VLESS,
                server = "example.com",
                method = validVlessEncryption,
            )
        )
    }

    @Test
    fun removedTlsAllowInsecureIsDeprecatedForGeneratedTlsOutbounds() {
        listOf(
            EConfigType.VMESS,
            EConfigType.VLESS,
            EConfigType.SHADOWSOCKS,
            EConfigType.TROJAN,
            EConfigType.HYSTERIA2,
        ).forEach { type ->
            val profile = profile(type, server = "example.com", security = AppConfig.TLS)
            profile.insecure = true

            assertTrue(type.name, XrayOutboundCompatibility.isDeprecated(profile))
        }
    }

    @Test
    fun pinnedCertificatePreventsAllowInsecureFromBeingGenerated() {
        val profile = profile(EConfigType.VLESS, server = "example.com", security = AppConfig.TLS)
        profile.insecure = true
        profile.pinnedCA256 = "00".repeat(32)

        assertFalse(XrayOutboundCompatibility.isDeprecated(profile))
    }

    @Test
    fun removedTlsAllowInsecureIsRejectedEvenForPrivateEndpoints() {
        val profile = profile(EConfigType.VLESS, server = "192.168.1.1", security = AppConfig.TLS)
        profile.insecure = true

        assertTrue(XrayOutboundCompatibility.isDeprecated(profile))
    }

    @Test
    fun allowInsecureFlagIsIgnoredWithoutGeneratedTlsSettings() {
        val profiles = listOf(
            profile(EConfigType.VLESS, server = "example.com", security = AppConfig.REALITY),
            profile(EConfigType.VLESS, server = "example.com", method = validVlessEncryption),
            profile(EConfigType.SOCKS, server = "example.com", security = AppConfig.TLS),
        )
        profiles.forEach { it.insecure = true }

        profiles.forEach { profile ->
            assertFalse(profile.configType.name, XrayOutboundCompatibility.isDeprecated(profile))
        }
    }

    @Test
    fun unencryptedTrojanToPublicEndpointIsDeprecated() {
        assertTrue(isDeprecated(EConfigType.TROJAN, server = "example.com"))
        assertFalse(isDeprecated(EConfigType.TROJAN, server = "example.com", security = AppConfig.TLS))
    }

    @Test
    fun xrayPrivateIpv4RangesRemainSupported() {
        val privateAddresses = listOf(
            "0.0.0.1",
            "10.255.255.255",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.31.255.255",
            "192.0.0.1",
            "192.0.2.1",
            "192.88.99.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "255.255.255.255",
        )

        privateAddresses.forEach { server ->
            assertFalse(server, isDeprecated(EConfigType.VLESS, server = server))
        }
        assertTrue(isDeprecated(EConfigType.VLESS, server = "100.128.0.1"))
        assertTrue(isDeprecated(EConfigType.VLESS, server = "192.0.3.1"))
        assertTrue(isDeprecated(EConfigType.VLESS, server = "010.0.0.1"))
        assertFalse(isDeprecated(EConfigType.VLESS, server = " 10.0.0.1 "))
    }

    @Test
    fun xrayPrivateIpv6RangesRemainSupported() {
        listOf("::", "::1", "fc00::1", "fdff::1", "fe80::1", "ff02::1").forEach { server ->
            assertFalse(server, isDeprecated(EConfigType.TROJAN, server = server))
        }
        assertTrue(isDeprecated(EConfigType.TROJAN, server = "2001:db8::1"))
    }

    @Test
    fun xrayPrivateDomainsRemainSupported() {
        val privateDomains = listOf(
            "router",
            "LAN",
            "host.localdomain",
            "node.example",
            "service.invalid",
            "localhost",
            "proxy.test",
            "printer.local",
            "gateway.home.arpa",
            "service.internal.",
        )

        privateDomains.forEach { server ->
            assertFalse(server, isDeprecated(EConfigType.VLESS, server = server))
        }
        assertTrue(isDeprecated(EConfigType.VLESS, server = "example.com"))
        assertTrue(isDeprecated(EConfigType.VLESS, server = "internal.example.com"))
    }

    @Test
    fun unrelatedProtocolsAreNotMarked() {
        assertFalse(isDeprecated(EConfigType.VMESS, server = "example.com"))
        assertFalse(isDeprecated(EConfigType.SHADOWSOCKS, server = "example.com"))
        assertFalse(isDeprecated(EConfigType.WIREGUARD, server = "example.com"))
    }

    private fun isDeprecated(
        type: EConfigType,
        server: String,
        security: String? = null,
        method: String? = null,
    ): Boolean = XrayOutboundCompatibility.isDeprecated(
        profile(type, server, security, method)
    )

    private fun profile(
        type: EConfigType,
        server: String,
        security: String? = null,
        method: String? = null,
    ) = ProfileItem(
        configType = type,
        server = server,
        security = security,
        method = method,
    )
}
