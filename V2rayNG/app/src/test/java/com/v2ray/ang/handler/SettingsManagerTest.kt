package com.v2ray.ang.handler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerTest {
    @Test
    fun coreRequestsRequireNativeXrayTunWithoutAnEffectiveLocalProxy() {
        assertTrue(shouldUseCore())
        assertFalse(shouldUseCore(vpnMode = false))
        assertFalse(shouldUseCore(usingHevTun = true))
        assertFalse(shouldUseCore(rootMode = true))
        assertFalse(shouldUseCore(rootLanSharing = true))
        assertFalse(shouldUseCore(localProxyEnabled = true))
    }

    private fun shouldUseCore(
        vpnMode: Boolean = true,
        usingHevTun: Boolean = false,
        rootMode: Boolean = false,
        rootLanSharing: Boolean = false,
        localProxyEnabled: Boolean = false,
    ): Boolean = SettingsManager.shouldUseCoreForAppRequests(
        vpnMode,
        usingHevTun,
        rootMode,
        rootLanSharing,
        localProxyEnabled,
    )
}
