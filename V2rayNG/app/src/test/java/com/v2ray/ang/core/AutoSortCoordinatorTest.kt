package com.v2ray.ang.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoSortCoordinatorTest {

    @Test
    fun completeFirstBatchIsCommittedImmediately() = runBlocking {
        val committed = CompletableDeferred<Unit>()
        var commitCount = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AutoSortCoordinator(
            scope = scope,
            planProvider = { plan() },
            batchRunner = { current ->
                current.candidateGuids.associateWith { 25L }
            },
            resultCommitter = { _, _ ->
                commitCount++
                committed.complete(Unit)
                false
            },
        )

        try {
            coordinator.start()
            withTimeout(5_000) { committed.await() }
            assertEquals(1, commitCount)
        } finally {
            coordinator.stop()
            scope.cancel()
        }
    }

    @Test
    fun cancellationDuringBatchDoesNotCommit() = runBlocking {
        val batchStarted = CompletableDeferred<Unit>()
        val batchCancelled = CompletableDeferred<Unit>()
        var commitCount = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = AutoSortCoordinator(
            scope = scope,
            planProvider = { plan() },
            batchRunner = {
                batchStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    batchCancelled.complete(Unit)
                }
            },
            resultCommitter = { _, _ ->
                commitCount++
                true
            },
        )

        try {
            coordinator.start()
            withTimeout(5_000) { batchStarted.await() }
            coordinator.stop()
            withTimeout(5_000) { batchCancelled.await() }
            assertEquals(0, commitCount)
        } finally {
            coordinator.stop()
            scope.cancel()
        }
    }

    private fun plan() = AutoSortPlan(
        policyGroupGuid = "group",
        subscriptionId = "subscription",
        intervalMinutes = 10,
        candidateGuids = listOf("node"),
    )
}
