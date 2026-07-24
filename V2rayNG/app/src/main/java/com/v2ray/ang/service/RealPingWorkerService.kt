package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = startRealPing(guid)
                    onEvent(RealPingEvent.Result(guid, result.first, result.second))
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    onEvent(RealPingEvent.Progress("$left / $count"))
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onEvent(RealPingEvent.Finish("0"))
            } catch (_: CancellationException) {
                onEvent(RealPingEvent.Finish("-1"))
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

    private fun startRealPing(guid: String): Pair<Long, Long> {
        val retFailure = -1L
        val config = MmkvManager.decodeServerConfig(guid) ?: return Pair(retFailure, retFailure)
        
        val pingType = SettingsManager.getPingType()
        var icmpDelay = 0L

        if (pingType == "both" || pingType == "icmp") {
            if (!config.configType.isComplexType()
                && config.configType != EConfigType.HYSTERIA2
                && config.configType != EConfigType.WIREGUARD
                && config.alpn?.startsWith("h3") != true
                && config.server.isNotNullEmpty()
                && config.serverPort?.toIntOrNull() != null
            ) {
                val url = config.server.orEmpty()
                val port = config.serverPort.orEmpty().toInt()
                val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
                icmpDelay = tcpTime
                if (tcpTime <= -1L && pingType == "both") {
                    return Pair(retFailure, tcpTime)
                }
            } else if (pingType == "icmp") {
                icmpDelay = retFailure
            }
        }

        if (pingType == "icmp") {
            return Pair(0L, icmpDelay)
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return Pair(retFailure, icmpDelay)
        }
        val httpDelay = CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
        return Pair(httpDelay, icmpDelay)
    }
}
