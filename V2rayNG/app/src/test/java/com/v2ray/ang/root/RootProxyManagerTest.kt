package com.v2ray.ang.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootProxyManagerTest {

    @Test
    fun `IPv6 LAN forwarding disables jumbo and TSO`() {
        val config = RootProxyManager.buildHevConfig(null, null, 10808, true, 1500, true)

        assertTrue(config.contains("  offload:\n    jumbo: false\n"))
        assertTrue(config.contains("  multi-queue: true\n"))
        assertTrue(config.contains("    tso: false\n"))
        assertFalse(config.contains("uso:"))
    }

    @Test
    fun `local root keeps default offloads and configured MTU`() {
        val config = RootProxyManager.buildHevConfig(null, null, 10808, true, 8500, false)

        assertFalse(config.contains("offload:"))
        assertTrue(config.contains("  mtu: 8500\n"))
        assertTrue(config.contains("  ipv6:"))
    }

    @Test
    fun `LAN offload guard preserves IPv4 only and quoted authentication`() {
        val config = RootProxyManager.buildHevConfig("user'name", "pa'ss", 10809, false, 1500, true)

        assertTrue(config.contains("  offload:\n    jumbo: false\n"))
        assertFalse(config.contains("tso:"))
        assertFalse(config.contains("  ipv6:"))
        assertTrue(config.contains("  port: 10809\n"))
        assertTrue(config.contains("  username: 'user''name'\n"))
        assertTrue(config.contains("  password: 'pa''ss'\n"))
    }

    @Test
    fun `single quotes are escaped in YAML scalars`() {
        assertEquals("'user''name'", "user'name".toSingleQuotedYamlScalar())
        assertEquals("'pa''''ss'", "pa''ss".toSingleQuotedYamlScalar())
    }

    @Test
    fun `multiline credentials remain inside the YAML scalar`() {
        val credential = "user\nHEVCFG\ntouch /data/local/tmp/injected"

        assertEquals("'user\nHEVCFG\ntouch /data/local/tmp/injected'", credential.toSingleQuotedYamlScalar())
    }
}
