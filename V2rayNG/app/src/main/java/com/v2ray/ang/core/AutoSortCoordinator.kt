package com.v2ray.ang.core

import com.v2ray.ang.extension.delay
import com.v2ray.ang.handler.AutoSortPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

internal data class AutoSortPlan(
    val policyGroupGuid: String,
    val subscriptionId: String,
    val intervalMinutes: Int,
    val candidateGuids: List<String>,
)

/**
 * Runs the app-level real-delay sweep that refreshes the visible subscription order.
 *
 * Xray's leastPing balancer remains the routing authority. This coordinator only refreshes
 * persisted test results and list order, and every cycle is owned by the proxy service lifetime.
 */
internal class AutoSortCoordinator(
    private val scope: CoroutineScope,
    private val planProvider: () -> AutoSortPlan?,
    private val batchRunner: suspend (AutoSortPlan) -> Map<String, Long>?,
    private val resultCommitter: (AutoSortPlan, Map<String, Long>) -> Boolean,
    private val waitBeforeNextRun: suspend (Long) -> Unit = { delay(it) },
) {
    private val generation = AtomicLong(0L)
    private var job: Job? = null

    @Synchronized
    fun start() {
        val previousJob = job
        previousJob?.cancel()
        generation.incrementAndGet()
        val currentGeneration = generation.get()
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            previousJob?.join()
            if (!isActive || generation.get() != currentGeneration) return@launch

            while (currentCoroutineContext().isActive) {
                val plan = planProvider() ?: return@launch
                val results = if (plan.candidateGuids.isEmpty()) {
                    null
                } else {
                    batchRunner(plan)
                }
                currentCoroutineContext().ensureActive()
                if (results != null && results.keys.containsAll(plan.candidateGuids)) {
                    val committed = synchronized(this@AutoSortCoordinator) {
                        generation.get() == currentGeneration &&
                                job?.isActive == true &&
                                resultCommitter(plan, results)
                    }
                    if (!committed) return@launch
                }
                waitBeforeNextRun(AutoSortPolicy.intervalMillis(plan.intervalMinutes))
            }
        }
        job = newJob
        newJob.start()
    }

    @Synchronized
    fun stop() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
    }
}
