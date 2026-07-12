package com.v2ray.ang.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkIdentityResolverTest {
    @Test
    fun normalizesWifiSsidWithoutSplittingMeshAccessPoints() {
        assertEquals("wifi:Home Mesh", NetworkIdentityResolver.wifiKey("\"Home Mesh\""))
        assertEquals("wifi", NetworkIdentityResolver.wifiKey("<unknown ssid>"))
        assertEquals("wifi", NetworkIdentityResolver.wifiKey(null))
        assertEquals("wifi", NetworkIdentityResolver.wifiKey("\"Home Mesh\"", rememberPerNetwork = false))
    }

    @Test
    fun prefersCurrentCellularOperatorAndFallsBackToSimOperator() {
        assertEquals("cellular:310260", NetworkIdentityResolver.cellularKey("310260", "23415"))
        assertEquals("cellular:23415", NetworkIdentityResolver.cellularKey("", "23415"))
        assertEquals("cellular", NetworkIdentityResolver.cellularKey("invalid", null))
    }
}
