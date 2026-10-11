package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreServiceManagerTest {

    @Test
    fun `service re-created without a start command is stopped`() {
        assertTrue(shouldStopUnstartedService(awaitingStartCommand = true, coreRunning = false))
    }

    @Test
    fun `service that received a start command is kept`() {
        assertFalse(shouldStopUnstartedService(awaitingStartCommand = false, coreRunning = false))
        assertFalse(shouldStopUnstartedService(awaitingStartCommand = false, coreRunning = true))
    }

    @Test
    fun `running core is never stopped by the start command check`() {
        assertFalse(shouldStopUnstartedService(awaitingStartCommand = true, coreRunning = true))
    }
}
