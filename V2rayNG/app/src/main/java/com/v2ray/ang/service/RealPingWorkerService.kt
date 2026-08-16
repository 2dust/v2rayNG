package com.v2ray.ang.service

import android.content.Context
import android.os.SystemClock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.ProbePlan
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.Libv2ray
import libv2ray.ProbeHandler

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles start complete Xray configs. Keep their native
        // lifecycle serialized across workers, matching the upstream safety
        // boundary while generated Observatory profiles remain concurrent.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

/** Runs one progressively reported delay-test batch through one native core. */
class RealPingWorkerService(
    private val context: Context,
    guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val guids = guids.distinct()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO + CoroutineName("ProbeBatch"))
    private val controller = Libv2ray.newProbeController()
    @Volatile
    private var finished = false
    private val emittedDelays = mutableMapOf<String, Long>()
    private var completedWorkUnits = 0
    private var totalWorkUnits = guids.size
    private val remainingWorkUnits = guids.associateWith { 1 }.toMutableMap()
    private var lastProgressAt = 0L

    fun start() {
        if (onlyTcp) {
            startTcpBatch()
            return
        }
        scope.launch {
            try {
                val plan = CoreConfigManager.getProbePlan(context, guids)
                val probeCount = plan.profiles.sumOf { it.outboundTags.size }
                setWorkUnits(plan)
                val concurrency = SettingsManager.getRealPingConcurrency()
                if (plan.profiles.isNotEmpty()) {
                    LogUtil.i(
                        AppConfig.TAG,
                        "Starting $probeCount real-delay probes for ${plan.profiles.size} profiles with limit $concurrency",
                    )
                }
                runPlan(plan, concurrency)
                failPending()
                finish("0")
            } catch (_: CancellationException) {
                finish("-1")
            } catch (error: Throwable) {
                if (!finished) {
                    LogUtil.e(AppConfig.TAG, "Probe batch failed", error)
                    failPending()
                    finish("-1")
                }
            }
        }
    }

    private fun startTcpBatch() {
        val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
        val jobs = guids.map { guid ->
            scope.launch(dispatcher) {
                emitResult(guid, safelyProbe(guid, ::startTcping))
                completeWork(guid, profileCompleted = true)
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

    private suspend fun runPlan(plan: ProbePlan, concurrency: Int) {
        plan.failedGuids.forEach { guid ->
            emitResult(guid, -1L)
            completeWork(guid, profileCompleted = true)
        }
        probeBatch(plan, concurrency)
        probeIndividually(plan.individualGuids)
    }

    /** Each fallback needs its own Xray instance, so these cannot overlap safely. */
    private suspend fun probeIndividually(individualGuids: List<String>) {
        individualGuids.forEach { guid ->
            currentCoroutineContext().ensureActive()
            emitResult(guid, safelyProbe(guid, ::startRealPing))
            completeWork(guid, profileCompleted = true)
        }
    }

    private suspend fun probeBatch(plan: ProbePlan, concurrency: Int) {
        if (plan.profiles.isEmpty()) return
        try {
            controller.probe(
                plan.content,
                JsonUtil.toJson(plan.profiles),
                concurrency,
                object : ProbeHandler {
                    override fun onProbeResult(
                        groupID: String?,
                        delay: Long,
                        completed: Boolean,
                    ) {
                        val guid = groupID ?: return
                        emitResult(guid, delay)
                        completeWork(guid, profileCompleted = completed)
                    }
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val retryGuids = plan.profiles.map { it.guid }.filter(::isPending)
            LogUtil.w(
                AppConfig.TAG,
                "Shared probe core rejected ${retryGuids.size} profiles; isolating the failing profile",
                error,
            )
            retryProbeGuids(retryGuids, concurrency)
        }
    }

    /** Binary isolation keeps one malformed Xray config from degrading every valid profile. */
    private suspend fun retryProbeGuids(retryGuids: List<String>, concurrency: Int) {
        currentCoroutineContext().ensureActive()
        val activeGuids = retryGuids.filter(::isPending)
        if (activeGuids.isEmpty()) return
        if (activeGuids.size == 1) {
            probeIndividually(activeGuids)
            return
        }
        val halves = activeGuids.chunked((activeGuids.size + 1) / 2)
        halves.forEach { half ->
            currentCoroutineContext().ensureActive()
            val retryPlan = try {
                CoreConfigManager.getProbePlan(context, half)
            } catch (error: Exception) {
                LogUtil.w(AppConfig.TAG, "Failed to rebuild ${half.size} isolated probe profiles", error)
                retryProbeGuids(half, concurrency)
                return@forEach
            }
            runPlan(retryPlan, concurrency)
        }
    }

    @Synchronized
    private fun failPending() {
        remainingWorkUnits.filterValues { it > 0 }.keys.toList().forEach { guid ->
            emitResult(guid, emittedDelays[guid] ?: -1L)
            completeWork(guid, profileCompleted = true)
        }
    }

    @Synchronized
    private fun isPending(guid: String): Boolean = remainingWorkUnits[guid]?.let { it > 0 } == true

    @Synchronized
    private fun emitResult(guid: String, delay: Long) {
        if (finished) return
        if (emittedDelays[guid] != delay) {
            emittedDelays[guid] = delay
            onEvent(RealPingEvent.Result(guid, delay))
        }
    }

    @Synchronized
    private fun setWorkUnits(plan: ProbePlan) {
        remainingWorkUnits.clear()
        guids.forEach { remainingWorkUnits[it] = 1 }
        plan.profiles.forEach { profile ->
            remainingWorkUnits[profile.guid] = profile.outboundTags.size.coerceAtLeast(1)
        }
        totalWorkUnits = remainingWorkUnits.values.sum().coerceAtLeast(1)
        completedWorkUnits = 0
        lastProgressAt = 0L
        emitProgress(force = true)
    }

    @Synchronized
    private fun completeWork(guid: String, profileCompleted: Boolean) {
        val remaining = remainingWorkUnits[guid] ?: return
        if (remaining <= 0) return
        val completed = if (profileCompleted) remaining else 1
        remainingWorkUnits[guid] = remaining - completed
        completedWorkUnits = (completedWorkUnits + completed).coerceAtMost(totalWorkUnits)
        emitProgress(force = completedWorkUnits == totalWorkUnits)
    }

    private fun emitProgress(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgressAt < PROGRESS_UPDATE_INTERVAL_MS) return
        lastProgressAt = now
        onEvent(RealPingEvent.Progress("$completedWorkUnits / $totalWorkUnits"))
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

    private suspend fun startRealPing(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        val configResult = CoreConfigManager.getV2rayConfig4RealDelay(context, guid)
        if (!configResult.status) return -1L
        return RealPingExecutionLimiter.run(config.configType) {
            controller.measureDelay(configResult.content, SettingsManager.getDelayTestUrl())
        }
    }

    private suspend fun safelyProbe(guid: String, probe: suspend (String) -> Long): Long = try {
        probe(guid)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LogUtil.e(AppConfig.TAG, "Probe failed for $guid", error)
        -1L
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
}
