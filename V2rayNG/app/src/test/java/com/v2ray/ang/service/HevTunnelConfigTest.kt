package com.v2ray.ang.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevTunnelConfigTest {

    @Test
    fun parsesTimeoutsByPositionWithDefaults() {
        assertEquals(Pair(300, 60), HevTunnelSettings.parseTimeouts(null))
        assertEquals(Pair(5, 7), HevTunnelSettings.parseTimeouts("5, 7"))
        assertEquals(Pair(300, 9), HevTunnelSettings.parseTimeouts("bad, 9"))
        assertEquals(Pair(300, 9), HevTunnelSettings.parseTimeouts(", 9"))
    }

    @Test
    fun buildsEscapedYamlForBothTunnelModes() {
        val yaml = HevTunnelConfig.build(
            HevTunnelParameters(
                mtu = 1500,
                ipv4 = "10.0.0.1",
                ipv6 = "fc00::1",
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                socksUsername = "o'connor",
                socksPassword = "p'ass",
                settings = HevTunnelSettings(5, 7, "w'arn"),
            ),
        )

        assertTrue(yaml.contains("ipv4: '10.0.0.1'"))
        assertTrue(yaml.contains("ipv6: 'fc00::1'"))
        assertTrue(yaml.contains("username: 'o''connor'"))
        assertTrue(yaml.contains("password: 'p''ass'"))
        assertTrue(yaml.contains("tcp-read-write-timeout: 5000"))
        assertTrue(yaml.contains("udp-read-write-timeout: 7000"))
        assertTrue(yaml.contains("log-level: 'w''arn'"))

        val ipv4Only = HevTunnelConfig.build(
            HevTunnelParameters(
                mtu = 1500,
                ipv4 = "10.0.0.1",
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                settings = HevTunnelSettings(5, 7, "warn"),
            ),
        )
        assertFalse(ipv4Only.contains("ipv6:"))
        assertFalse(ipv4Only.contains("username:"))
    }
}
