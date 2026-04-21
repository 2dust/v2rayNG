package com.v2ray.ang.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonArray
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayNativeManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import libv2ray.CoreCallbackHandler
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SpeedTestWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onFinish: (status: String) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    private val dispatcher = Executors.newFixedThreadPool(cpu * 4).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("SpeedTestBatchWorker"))

    private val runningDelayCount = AtomicInteger(0)
    private val totalDelayCount = AtomicInteger(0)

    fun start() {
        val speedGuids = Collections.synchronizedList(mutableListOf<String>())
        val delayJobs = guids.map { guid ->
            totalDelayCount.incrementAndGet()
            scope.launch {
                runningDelayCount.incrementAndGet()
                try {
                    val delay = startRealPing(guid)
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, delay))
                    if (delay > 0L) {
                        speedGuids.add(guid)
                    }
                } finally {
                    val count = totalDelayCount.decrementAndGet()
                    val left = runningDelayCount.decrementAndGet()
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_NOTIFY, "$left / $count")
                }
            }
        }

        scope.launch {
            try {
                joinAll(*delayJobs.toTypedArray())
                runSpeedTests(speedGuids.toList())
                onFinish("0")
            } catch (_: CancellationException) {
                onFinish("-1")
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private suspend fun runSpeedTests(speedGuids: List<String>) {
        val doneCount = AtomicInteger(0)
        val semaphore = Semaphore(SettingsManager.getSpeedTestConcurrency())
        val jobs = speedGuids.map { guid ->
            scope.launch {
                semaphore.withPermit {
                    if (!currentCoroutineContext().isActive) {
                        return@withPermit
                    }

                    val speed = startDownloadSpeedTest(guid)
                    MessageUtil.sendMsg2UI(context, AppConfig.MSG_MEASURE_CONFIG_SPEED_SUCCESS, Pair(guid, speed))
                    val done = doneCount.incrementAndGet()
                    MessageUtil.sendMsg2UI(
                        context,
                        AppConfig.MSG_MEASURE_CONFIG_NOTIFY,
                        "$done / ${speedGuids.size}"
                    )
                }
            }
        }
        joinAll(*jobs.toTypedArray())
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun startRealPing(guid: String): Long {
        val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return -1L
        }
        return V2RayNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
    }

    private suspend fun startDownloadSpeedTest(guid: String): Double {
        val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return 0.0
        }

        val port = findFreePort()
        val config = addHttpInbound(configResult.content, port) ?: return 0.0
        val controller = V2RayNativeManager.newCoreController(SpeedTestCoreCallback())
        return try {
            controller.startLoop(config, 0)
            delay(600)
            if (!controller.isRunning) {
                Log.w(AppConfig.TAG, "Speed test core did not start for $guid")
                return 0.0
            }
            for (url in SettingsManager.getSpeedTestUrls()) {
                val speed = downloadViaProxy(port, url)
                if (speed > 0.0) {
                    return speed
                }
            }
            0.0
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Speed test failed for $guid", e)
            0.0
        } finally {
            try {
                controller.stopLoop()
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to stop speed test core", e)
            }
        }
    }

    private fun downloadViaProxy(port: Int, url: String): Double {
        val timeout = SettingsManager.getSpeedTestTimeoutMillis()
        val conn = HttpUtil.createProxyConnection(
            url,
            port,
            timeout,
            timeout,
            true
        ) ?: return 0.0

        var totalBytes = 0L
        val started = SystemClock.elapsedRealtime()
        return try {
            conn.connect()
            val responseCode = conn.responseCode
            if (responseCode !in HTTP_SUCCESS_CODES) {
                Log.w(AppConfig.TAG, "Speed test URL returned HTTP $responseCode: $url")
                return 0.0
            }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            conn.inputStream.use { input ->
                while (job.isActive && input.read(buffer).also { if (it > 0) totalBytes += it } != -1) {
                    val elapsed = SystemClock.elapsedRealtime() - started
                    if (elapsed >= timeout || totalBytes >= MAX_DOWNLOAD_BYTES) {
                        break
                    }
                }
            }
            if (totalBytes < MIN_DOWNLOAD_BYTES) {
                Log.w(AppConfig.TAG, "Speed test URL returned only $totalBytes bytes: $url")
                return 0.0
            }
            val elapsedSeconds = ((SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)) / 1000.0
            (totalBytes * 8.0) / elapsedSeconds / 1_000_000.0
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Speed test download failed for $url", e)
            0.0
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Speed test download failed for $url", e)
            0.0
        } finally {
            conn.disconnect()
        }
    }

    private fun addHttpInbound(config: String, port: Int): String? {
        val json = JsonUtil.parseString(config) ?: return null
        val inbounds = JsonArray()
        val inbound = JsonUtil.parseString(
            """
            {
              "tag": "speedtest-http",
              "listen": "${AppConfig.LOOPBACK}",
              "port": $port,
              "protocol": "http",
              "settings": {
                "timeout": 0,
                "userLevel": 8
              }
            }
            """.trimIndent()
        ) ?: return null
        inbounds.add(inbound)
        json.add("inbounds", inbounds)
        return JsonUtil.toJsonPretty(json)
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            return socket.localPort
        }
    }

    private class SpeedTestCoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L
        private const val MIN_DOWNLOAD_BYTES = 256L * 1024L
        private val HTTP_SUCCESS_CODES = HttpURLConnection.HTTP_OK..299
    }
}
