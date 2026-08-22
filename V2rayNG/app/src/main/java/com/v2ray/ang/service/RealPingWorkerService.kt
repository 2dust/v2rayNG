package com.v2ray.ang.service

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles bypass speed-test trimming and start complete Xray configs.
        // Parallel teardown can abort the native probe process, so serialize their
        // JNI measurements globally across batches.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

private class RealPingProgressState(private val total: Int) {
    private var completed = 0
    private var live = 0
    private var lastProgressAt = SystemClock.elapsedRealtime()

    @Synchronized
    fun record(delayMillis: Long): RealPingEvent.Progress? {
        if (completed >= total) return null
        completed++
        if (delayMillis >= 0L) live++

        val now = SystemClock.elapsedRealtime()
        if (completed < total && now - lastProgressAt < PROGRESS_UPDATE_INTERVAL_MS) return null
        lastProgressAt = now
        return RealPingEvent.Progress(completed = completed, total = total)
    }

    @Synchronized
    fun summary(): RealPingEvent.Finish = RealPingEvent.Finish(
        live = live,
        completed = completed,
        total = total,
    )

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
}

/** Runs one bounded batch of individual delay tests in the disposable probe process. */
class RealPingWorkerService(
    private val context: Context,
    guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {},
) {
    private val guids = guids.distinct()
    private val job = SupervisorJob()
    private val dispatcher = Dispatchers.IO.limitedParallelism(SettingsManager.getRealPingConcurrency())
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))
    private val progress = RealPingProgressState(this.guids.size)
    private val finished = AtomicBoolean(false)

    fun start() {
        onEvent(RealPingEvent.Progress(completed = 0, total = guids.size))
        val jobs = guids.map { guid ->
            scope.launch {
                val delayMillis = safelyProbe(guid)
                currentCoroutineContext().ensureActive()
                onEvent(RealPingEvent.Result(guid, delayMillis))
                progress.record(delayMillis)?.let(onEvent)
            }
        }

        scope.launch {
            jobs.joinAll()
            if (finished.compareAndSet(false, true)) {
                onEvent(progress.summary())
            }
        }
    }

    fun cancel(): RealPingEvent.Finish {
        finished.set(true)
        job.cancel()
        return progress.summary()
    }

    private suspend fun safelyProbe(guid: String): Long = try {
        if (onlyTcp) startTcping(guid) else startRealPing(guid)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        -1L
    }

    private suspend fun startRealPing(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val tcpTime = SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
            if (tcpTime <= -1L) return -1L
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) return -1L
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(
                configResult.content,
                SettingsManager.getDelayTestUrl(),
            )
        }
    }

    private fun startTcping(guid: String): Long {
        val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.split(',')?.all { it.trim().startsWith("h3") } != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            return SpeedtestManager.socketConnectTime(
                config.server.orEmpty(),
                config.serverPort.orEmpty().toInt(),
                1000,
            )
        }
        return -1L
    }
}
