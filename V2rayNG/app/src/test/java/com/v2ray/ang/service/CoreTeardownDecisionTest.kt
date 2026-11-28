package com.v2ray.ang.service

import com.v2ray.ang.core.shouldScheduleCoreTeardown
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreTeardownDecisionTest {

    @Test
    fun schedulesExactlyOneTeardownForARunningCore() {
        assertFalse(shouldScheduleCoreTeardown(coreRunning = false, teardownActive = false))
        assertTrue(shouldScheduleCoreTeardown(coreRunning = true, teardownActive = false))
        assertFalse(shouldScheduleCoreTeardown(coreRunning = true, teardownActive = true))
    }
}
