package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IpPurityResult
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class IpPurityWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onFinish: (status: String) -> Unit = {}
) {
    @Volatile
    private var cancelled = false
    private val job = SupervisorJob()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("IpPurityBatchWorker"))

    fun start() {
        scope.launch {
            try {
                val total = guids.size
                guids.forEachIndexed { index, guid ->
                    if (cancelled || !job.isActive) {
                        throw CancellationException()
                    }
                    val result = startPurityCheck(guid)
                    if (cancelled || !job.isActive) {
                        throw CancellationException()
                    }
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_IP_PURITY_SUCCESS, Pair(guid, result))
                    val remaining = (total - index - 1).coerceAtLeast(0)
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_IP_PURITY_NOTIFY, "$remaining / $total")
                }
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

    private fun startPurityCheck(guid: String): IpPurityResult {
        return SpeedtestManager.getIpPurityResult(context, guid)
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }
}
