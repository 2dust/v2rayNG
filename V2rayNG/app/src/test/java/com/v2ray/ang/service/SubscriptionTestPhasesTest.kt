package com.v2ray.ang.service

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionTestPhasesTest {

    private val started = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()
    private val phases = SubscriptionTestPhases<String> { cancelled += it }

    private fun start(token: Int, worker: String): Deferred<Boolean> = phases.start(token, worker) { started += it }

    private fun Deferred<Boolean>.result(): Boolean? = if (isCompleted) runBlocking { await() } else null

    @Test
    fun finishedPhaseCompletesTheTest() {
        val phase = start(phases.begin(), "a")

        assertEquals(listOf("a"), started)
        assertNull(phase.result())
        assertFalse(phases.isIdle())
        phases.finish("a")
        assertEquals(true, phase.result())
        assertTrue(phases.isIdle())
    }

    @Test
    fun startingAConfigurationEndsTheRunningPhases() {
        val token = phases.begin()
        val a = start(token, "a")
        val b = start(token, "b")

        phases.cancelAll()

        assertEquals(listOf("a", "b"), cancelled.sorted())
        assertEquals(false, a.result())
        assertEquals(false, b.result())
        assertTrue(phases.isIdle())
        // A worker that finishes after its phase ended did not complete the test
        phases.finish("a")
        assertEquals(false, a.result())
    }

    @Test
    fun updatesThatRunWhenAConfigurationStartsTestNoMore() {
        val token = phases.begin()
        phases.cancelAll()

        assertFalse(phases.mayStart(token))
        assertEquals(false, start(token, "a").result())
        assertTrue(started.isEmpty())
        assertTrue(phases.isIdle())
    }

    @Test
    fun updatesThatBeginAfterwardsTestAgain() {
        phases.cancelAll()
        val token = phases.begin()

        assertTrue(phases.mayStart(token))
        val phase = start(token, "a")
        assertEquals(listOf("a"), started)
        assertNull(phase.result())
    }
}
