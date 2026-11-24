package com.v2ray.ang.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreStopConfirmationTest {
    @Test
    fun confirmsShutdownOnlyAfterNativeStopReturns() = runBlocking {
        val finishStop = CompletableDeferred<Unit>()
        var running = true
        val stopped = async(start = CoroutineStart.UNDISPATCHED) {
            stopCoreAndConfirm(
                stop = { finishStop.await(); running = false },
                isRunning = { running },
            )
        }

        assertFalse(stopped.isCompleted)
        finishStop.complete(Unit)
        assertTrue(stopped.await())
    }

    @Test
    fun returningFromStopDoesNotConfirmAStillRunningCore() = runBlocking {
        assertFalse(stopCoreAndConfirm(stop = {}, isRunning = { true }))
    }

    @Test
    fun aNativeErrorCannotConfirmASuccessfulStop() = runBlocking {
        val error = IllegalStateException("Native stop failed")
        val result = runCatching {
            stopCoreAndConfirm(stop = { throw error }, isRunning = { false })
        }
        assertSame(error, result.exceptionOrNull())
    }
}
