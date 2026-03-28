package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class FastIpPurityWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onFinish: (status: String) -> Unit = {}
) {
    @Volatile
    private var cancelled = false
    private val job = SupervisorJob()
    private val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val poolSize = (cpu.coerceAtMost(4)).coerceAtLeast(2)
    private val dispatcher = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("FastIpPurityBatchWorker"))
    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    if (cancelled || !job.isActive) {
                        throw CancellationException()
                    }
                    val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis
                    val score = if (delay != null && delay < 0L) {
                        -1
                    } else {
                        SpeedtestManager.getIpPurityScore(context, guid)
                    }
                    if (cancelled || !job.isActive) {
                        throw CancellationException()
                    }
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_FAST_IP_PURITY_SUCCESS, Pair(guid, score))
                } catch (_: CancellationException) {
                    throw CancellationException()
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    if (!cancelled && job.isActive) {
                        MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_FAST_IP_PURITY_NOTIFY, "$left / $count")
                    }
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onFinish("0")
            } catch (_: CancellationException) {
                onFinish("-1")
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        cancelled = true
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }
}
