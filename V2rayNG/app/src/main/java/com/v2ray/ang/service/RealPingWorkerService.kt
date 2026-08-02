package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import libv2ray.ProbeHandler

/** Runs one progressively reported delay-test batch through one native core. */
class RealPingWorkerService(
    private val context: Context,
    guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val guids = guids.distinct()
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO + CoroutineName("ProbeBatch"))
    private val controller = Libv2ray.newProbeController()
    @Volatile
    private var finished = false
    private val emittedDelays = mutableMapOf<String, Long>()
    private val pendingGuids = guids.toMutableSet()

    fun start() {
        if (onlyTcp) {
            startTcpBatch()
            return
        }
        scope.launch {
            try {
                val plan = CoreConfigManager.getProbePlan(context, guids)
                plan.failedGuids.forEach { emitResult(it, -1L, completed = true) }
                if (plan.profiles.isNotEmpty()) {
                    val concurrency = SettingsManager.getRealPingConcurrency()
                    val probeCount = plan.profiles.sumOf { it.outboundTags.size }
                    LogUtil.i(
                        AppConfig.TAG,
                        "Starting $probeCount real-delay probes for ${plan.profiles.size} profiles with limit $concurrency",
                    )
                    controller.probe(
                        plan.content,
                        JsonUtil.toJson(plan.profiles),
                        concurrency,
                        object : ProbeHandler {
                            override fun onProbeResult(
                                groupID: String?,
                                delay: Long,
                                alive: Boolean,
                                completed: Boolean,
                            ): Long {
                                emitResult(groupID!!, if (alive) delay else -1L, completed)
                                return 0
                            }
                        },
                    )
                }
                probeIndividually(plan.individualGuids)
                finish("0")
            } catch (_: CancellationException) {
                finish("-1")
            } catch (error: Throwable) {
                if (!finished) {
                    LogUtil.e(AppConfig.TAG, "Probe batch failed", error)
                    failPending()
                    finish("0")
                }
            }
        }
    }

    private fun startTcpBatch() {
        val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
        val jobs = guids.map { guid ->
            scope.launch(dispatcher) {
                emitResult(guid, startTcping(guid), completed = true)
            }
        }
        scope.launch {
            try {
                jobs.joinAll()
                finish("0")
            } catch (_: CancellationException) {
                finish("-1")
            }
        }
    }

    fun cancel() {
        controller.cancel()
        job.cancel()
        finish("-1")
    }

    private suspend fun probeIndividually(individualGuids: List<String>) {
        val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
        individualGuids.map { guid ->
            scope.launch(dispatcher) {
                emitResult(guid, startRealPing(guid), completed = true)
            }
        }.joinAll()
    }

    @Synchronized
    private fun failPending() {
        pendingGuids.toList().forEach { guid ->
            emitResult(guid, emittedDelays[guid] ?: -1L, completed = true)
        }
    }

    @Synchronized
    private fun emitResult(guid: String, delay: Long, completed: Boolean) {
        if (finished) return
        if (emittedDelays[guid] != delay) {
            emittedDelays[guid] = delay
            onEvent(RealPingEvent.Result(guid, delay))
        }
        if (completed) {
            pendingGuids.remove(guid)
            onEvent(RealPingEvent.Progress("${pendingGuids.size} / ${guids.size}"))
        }
    }

    @Synchronized
    private fun finish(status: String) {
        if (finished) return
        finished = true
        onEvent(RealPingEvent.Finish(status))
    }

    private fun startTcping(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType() &&
            config.configType != EConfigType.HYSTERIA2 &&
            config.configType != EConfigType.WIREGUARD &&
            config.alpn?.startsWith("h3") != true &&
            config.server.isNotNullEmpty() &&
            config.serverPort?.toIntOrNull() != null
        ) {
            return SpeedtestManager.socketConnectTime(config.server.orEmpty(), config.serverPort.orEmpty().toInt(), 1000)
        }
        return -1L
    }

    private fun startRealPing(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType() &&
            config.configType != EConfigType.HYSTERIA2 &&
            config.configType != EConfigType.WIREGUARD &&
            config.alpn?.startsWith("h3") != true &&
            config.server.isNotNullEmpty() &&
            config.serverPort?.toIntOrNull() != null &&
            SpeedtestManager.socketConnectTime(config.server.orEmpty(), config.serverPort.orEmpty().toInt(), 1000) <= -1L
        ) return -1L

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) return -1L
        return CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
    }
}
