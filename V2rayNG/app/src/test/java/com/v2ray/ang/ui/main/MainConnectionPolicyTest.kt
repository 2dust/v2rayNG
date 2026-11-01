package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.perappproxy.PerAppRoutingMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainConnectionPolicyTest {
    @Test
    fun disconnectedWithoutProfileCannotConnect() {
        assertFalse(MainConnectionPolicy.canConnect(false, null, PerAppRoutingMode.ALL, 0))
        assertFalse(MainConnectionPolicy.canConnect(false, "", PerAppRoutingMode.ALL, 0))
    }

    @Test
    fun selectedProfileCanConnectInAllAndBypassModes() {
        assertTrue(MainConnectionPolicy.canConnect(false, "profile", PerAppRoutingMode.ALL, 0))
        assertTrue(MainConnectionPolicy.canConnect(false, "profile", PerAppRoutingMode.EXCEPT_SELECTED, 0))
    }

    @Test
    fun emptyAllowlistRequiresSelectionButRunningServiceCanAlwaysStop() {
        assertFalse(MainConnectionPolicy.canConnect(false, "profile", PerAppRoutingMode.ONLY_SELECTED, 0))
        assertTrue(MainConnectionPolicy.canConnect(false, "profile", PerAppRoutingMode.ONLY_SELECTED, 1))
        assertTrue(MainConnectionPolicy.canConnect(true, null, PerAppRoutingMode.ONLY_SELECTED, 0))
    }
}
