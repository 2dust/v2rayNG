package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.AetherSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AetherSettingsTest {
    @Test
    fun defaultsProduceAnUnattendedLoopbackOnlyCommand() {
        val settings = AetherSettings()
        assertTrue(settings.isValid())
        val arguments = settings.arguments("/private/identity.toml", 1819)
        assertEquals("127.0.0.1:1819", arguments[arguments.indexOf("--bind") + 1])
        assertEquals("/private/identity.toml", arguments[arguments.indexOf("--config") + 1])
        assertTrue("--h2" in arguments)
        assertTrue("--fragment" in arguments)
        assertFalse("--peer" in arguments)
    }

    @Test
    fun explicitSettingsRemainDistinctArgumentsWithoutShellInterpretation() {
        val settings = AetherSettings(protocol = "wg", carrier = "h3", endpoint = "[2606:4700::1]:2408", fragment = false)
        assertTrue(settings.isValid())
        val arguments = settings.arguments("/private/a profile/identity.toml", 45123)
        assertTrue("--h3" in arguments && "--no-fragment" in arguments)
        assertEquals(settings.endpoint, arguments[arguments.indexOf("--peer") + 1])
        assertEquals("/private/a profile/identity.toml", arguments.last { it.endsWith("identity.toml") })
    }

    @Test
    fun invalidAndEmptyFieldsRejectConfiguration() {
        val base = AetherSettings()
        listOf(
            base.copy(protocol = ""), base.copy(protocol = "unknown"), base.copy(carrier = "udp"),
            base.copy(scanMode = ""), base.copy(ipVersion = "unknown"), base.copy(noise = "unknown"),
            base.copy(endpoint = "host.invalid:443"), base.copy(endpoint = "999.1.1.1:443"),
            base.copy(endpoint = "1.1.1.1:0"), base.copy(endpoint = "1.1.1.1:443/path"),
            base.copy(dns = ""), base.copy(dns = "1.1.1.1,"), base.copy(dns = "999.1.1.1"),
            base.copy(fragmentSize = "0"), base.copy(fragmentSize = "32-16"),
            base.copy(fragmentDelay = "-1"), base.copy(fragmentDelay = "1001"),
        ).forEach { assertFalse(it.isValid(), it.toString()) }
    }

    @Test
    fun everySupportedModeAndBoundaryIsAccepted() {
        val base = AetherSettings()
        listOf("turbo", "balanced", "thorough", "verified", "ironclad").forEach { assertTrue(base.copy(scanMode = it).isValid()) }
        listOf("v4", "v6", "both").forEach { assertTrue(base.copy(ipVersion = it).isValid()) }
        listOf("off", "light", "firewall", "balanced", "gfw", "aggressive").forEach { assertTrue(base.copy(noise = it).isValid()) }
        assertTrue(base.copy(fragmentSize = "1", fragmentDelay = "0").isValid())
        assertTrue(base.copy(fragmentSize = "16384", fragmentDelay = "1000").isValid())
        assertTrue(base.copy(dns = "1.1.1.1, 2606:4700:4700::1111", endpoint = "1.1.1.1:65535").isValid())
    }
}
