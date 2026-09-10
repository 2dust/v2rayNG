package com.v2ray.ang.core

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreServiceRestartTest {

    @Test
    fun activeRestartIsDetectedUntilCancellation() {
        val restartJob = SupervisorJob()

        assertTrue(hasActiveRestart(restartJob))
        restartJob.cancel()
        assertFalse(hasActiveRestart(restartJob))
    }

    @Test
    fun onlyCurrentActiveRestartCanStartReplacement() {
        val currentJob = SupervisorJob()
        val staleJob = SupervisorJob()

        assertFalse(canStartReplacement(currentJob, staleJob))
        assertTrue(canStartReplacement(currentJob, currentJob))

        currentJob.cancel()
        assertFalse(canStartReplacement(currentJob, currentJob))
        staleJob.cancel()
    }

    @Test
    fun waitsUntilCoreReportsStopped() = runBlocking {
        var running = true
        val waits = mutableListOf<Int>()

        val stopped = waitForCoreToStop(
            timeoutMillis = 500,
            pollIntervalMillis = 50,
            isRunning = { running },
            wait = {
                waits += it
                if (waits.size == 3) running = false
            },
        )

        assertTrue(stopped)
        assertEquals(listOf(50, 50, 50), waits)
    }

    @Test
    fun timeoutUsesOnlyTheRemainingInterval() = runBlocking {
        val waits = mutableListOf<Int>()

        val stopped = waitForCoreToStop(
            timeoutMillis = 120,
            pollIntervalMillis = 50,
            isRunning = { true },
            wait = { waits += it },
        )

        assertFalse(stopped)
        assertEquals(listOf(50, 50, 20), waits)
    }

    @Test
    fun alreadyStoppedCoreDoesNotWait() = runBlocking {
        var waited = false

        val stopped = waitForCoreToStop(
            timeoutMillis = 500,
            pollIntervalMillis = 50,
            isRunning = { false },
            wait = { waited = true },
        )

        assertTrue(stopped)
        assertFalse(waited)
    }
}
