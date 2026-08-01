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
import libv2ray.ProbeHandler
import java.util.concurrent.atomic.AtomicBoolean

/** Runs one progressively reported delay-test batch through one native core. */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO + CoroutineName("ProbeBatch"))
    private val controller = CoreNativeManager.newProbeController()
    private val finished = AtomicBoolean(false)
    private val emittedDelays = mutableMapOf<String, Long>()
    private val completedGuids = mutableSetOf<String>()
    private var totalProfiles = 0

    fun start() {
        if (onlyTcp) {
            startTcpBatch()
            return
        }
        scope.launch {
            try {
                val plan = CoreConfigManager.getV2rayConfig4BatchSpeedtest(context, guids)
                totalProfiles = (plan.profiles.map { it.guid } + plan.failedGuids).distinct().size
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
                                groupID?.let {
                                    emitResult(it, if (alive) delay else -1L, completed)
                                }
                                return 0
                            }
                        },
                    )
                }
                completeMissing(plan.profiles.map { it.guid } + plan.failedGuids)
                finish("0")
            } catch (_: CancellationException) {
                finish("-1")
            } catch (error: Throwable) {
                if (!finished.get()) {
                    LogUtil.e(AppConfig.TAG, "Probe batch failed", error)
                    finish("-1")
                }
            }
        }
    }

    private fun startTcpBatch() {
        totalProfiles = guids.size
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

    @Synchronized
    private fun completeMissing(allGuids: List<String>) {
        allGuids.distinct().forEach { guid ->
            if (guid !in completedGuids) emitResult(guid, emittedDelays[guid] ?: -1L, completed = true)
        }
    }

    @Synchronized
    private fun emitResult(guid: String, delay: Long, completed: Boolean) {
        if (finished.get()) return
        if (emittedDelays[guid] != delay) {
            emittedDelays[guid] = delay
            onEvent(RealPingEvent.Result(guid, delay))
        }
        if (completed && completedGuids.add(guid)) {
            val remaining = (totalProfiles - completedGuids.size).coerceAtLeast(0)
            onEvent(RealPingEvent.Progress("$remaining / $totalProfiles"))
        }
    }

    @Synchronized
    private fun finish(status: String) {
        if (finished.compareAndSet(false, true)) {
            onEvent(RealPingEvent.Finish(status))
        }
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
}
