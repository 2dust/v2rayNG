package com.v2ray.ang.root

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootProxyManagerTest {

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
