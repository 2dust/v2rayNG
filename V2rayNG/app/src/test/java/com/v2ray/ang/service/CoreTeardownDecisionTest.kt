package com.v2ray.ang.service

import com.v2ray.ang.core.shouldScheduleCoreTeardown
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTeardownDecisionTest {

    @Test
    fun schedulesExactlyOneTeardownForARunningCore() {
        assertFalse(shouldScheduleCoreTeardown(coreRunning = false, teardownActive = false))
        assertTrue(shouldScheduleCoreTeardown(coreRunning = true, teardownActive = false))
        assertFalse(shouldScheduleCoreTeardown(coreRunning = true, teardownActive = true))
    }
}
