package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.PingType
import com.v2ray.ang.handler.PingManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val remaining = AtomicInteger(guids.size)

    fun start() {
        scope.launch {
            try {
                // PingManager decides how to spread the work: proxy types share one core
                // instance and run together, TCP and ICMP go wide on their own
                val type = if (onlyTcp) PingType.TCP else SettingsManager.getPingType()
                PingManager.pingAll(context, guids, type, concurrency) { guid, delay ->
                    onEvent(RealPingEvent.Result(guid, delay))
                    onEvent(RealPingEvent.Progress("${remaining.decrementAndGet()} / ${guids.size}"))
                }
                onEvent(RealPingEvent.Finish("0"))
            } catch (_: CancellationException) {
                onEvent(RealPingEvent.Finish("-1"))
            } catch (_: Throwable) {
                onEvent(RealPingEvent.Finish("0"))
            } finally {
                close()
            }
        }
    }

    fun cancel() {
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
