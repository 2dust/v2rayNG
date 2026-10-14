package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AetherLifecycleTest {
    @Test
    fun coldStartRejectsEquivalentSetupAndRunningStarts() {
        val lifecycle = AetherLifecycle()
        assertTrue(lifecycle.canStart(false, false))
        assertFalse(lifecycle.canStart(true, false))
        assertFalse(lifecycle.canStart(false, true))
    }

    @Test
    fun repeatedStopAndStopDuringSetupPreventNewStarts() {
        val lifecycle = AetherLifecycle()
        assertTrue(lifecycle.beginStop())
        assertFalse(lifecycle.beginStop())
        assertFalse(lifecycle.canStart(false, false))
        assertFalse(lifecycle.canStart(true, false))
        assertFalse(lifecycle.beginRestart())
    }

    @Test
    fun restartLaunchesOnceAndStopCancelsPendingRestart() {
        val lifecycle = AetherLifecycle()
        var starts = 0
        assertTrue(lifecycle.beginRestart())
        assertFalse(lifecycle.beginRestart())
        assertTrue(lifecycle.completeRestart { starts++ })
        assertFalse(lifecycle.completeRestart { starts++ })
        assertEquals(1, starts)
        assertTrue(lifecycle.canStart(false, false))
        assertTrue(lifecycle.beginRestart())
        assertFalse(lifecycle.beginStop())
        assertFalse(lifecycle.completeRestart { starts++ })
        assertEquals(1, starts)
        assertTrue(lifecycle.isStopping())
    }

    @Test
    fun failedRestartRemainsStopped() {
        val lifecycle = AetherLifecycle()
        assertTrue(lifecycle.beginRestart())
        runCatching { lifecycle.completeRestart { error("Failed dispatch") } }
        assertTrue(lifecycle.isStopping())
        assertFalse(lifecycle.canStart(false, false))
    }
}
