package com.v2ray.ang.handler

import com.v2ray.ang.dto.ConnectionTestResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedtestManagerTest {
    @Test
    fun successfulTestFetchesEndpointOnce() {
        var requests = 0
        val result = SpeedtestManager.buildConnectionTestResult(42, "") {
            requests++
            SpeedtestManager.RemoteEndpointInfo("DE", "192.0.2.1")
        }

        assertEquals(1, requests)
        assertEquals(ConnectionTestResult(42, country = "DE", ipAddress = "192.0.2.1"), result)
    }

    @Test
    fun zeroDelayStillFetchesEndpointOnce() {
        var requests = 0
        val result = SpeedtestManager.buildConnectionTestResult(0, "") {
            requests++
            SpeedtestManager.RemoteEndpointInfo("DE", "2001:db8::1")
        }

        assertEquals(1, requests)
        assertEquals(ConnectionTestResult(0, country = "DE", ipAddress = "2001:db8::1"), result)
    }

    @Test
    fun failedTestSkipsEndpointLookup() {
        val result = SpeedtestManager.buildConnectionTestResult(-1, "timeout") {
            error("A failed connection test must not fetch endpoint information")
        }

        assertEquals(ConnectionTestResult(-1, "timeout"), result)
    }

    @Test
    fun unavailableEndpointPreservesSuccessfulDelayWithoutRetrying() {
        var requests = 0
        val result = SpeedtestManager.buildConnectionTestResult(42, "") {
            requests++
            null
        }

        assertEquals(1, requests)
        assertEquals(ConnectionTestResult(42), result)
    }
}
