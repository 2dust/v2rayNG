package com.v2ray.ang.handler

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.IpPurityResult
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

object SpeedtestManager {

    private const val TEMP_CORE_START_RETRY = 10
    private const val TEMP_CORE_START_WAIT_MS = 100L
    private const val TEMP_PROXY_REQUEST_RETRY = 3
    private const val TEMP_PROXY_REQUEST_WAIT_MS = 200L
    private const val TEMP_PROXY_REQUEST_TIMEOUT_MS = 5000

    private val tcpTestingSockets = ArrayList<Socket?>()

    /**
     * Measures the TCP connection time to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    suspend fun tcping(url: String, port: Int): Long {
        var time = -1L
        for (k in 0 until 2) {
            val one = socketConnectTime(url, port)
            if (!currentCoroutineContext().isActive) {
                break
            }
            if (one != -1L && (time == -1L || one < time)) {
                time = one
            }
        }
        return time
    }

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int): Long {
        try {
            val socket = Socket()
            synchronized(this) {
                tcpTestingSockets.add(socket)
            }
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(url, port), 3000)
            val time = System.currentTimeMillis() - start
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            socket.close()
            return time
        } catch (e: UnknownHostException) {
            Log.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        }
        return -1
    }

    /**
     * Closes all TCP sockets that are currently being tested.
     */
    fun closeAllTcpSockets() {
        synchronized(this) {
            tcpTestingSockets.forEach {
                it?.close()
            }
            tcpTestingSockets.clear()
        }
    }

    /**
     * Tests the connection to a given URL and port.
     *
     * @param context The Context in which the test is running.
     * @param port The port to connect to.
     * @return A pair containing the elapsed time in milliseconds and the result message.
     */
    fun testConnection(context: Context, port: Int): Pair<Long, String> {
        var result: String
        var elapsed = -1L

        val conn = HttpUtil.createProxyConnection(SettingsManager.getDelayTestUrl(), port, 15000, 15000) ?: return Pair(elapsed, "")
        try {
            val start = SystemClock.elapsedRealtime()
            val code = conn.responseCode
            elapsed = SystemClock.elapsedRealtime() - start

            result = when (code) {
                204 -> context.getString(R.string.connection_test_available, elapsed)
                200 if conn.contentLengthLong == 0L -> context.getString(R.string.connection_test_available, elapsed)
                else -> throw IOException(
                    context.getString(R.string.connection_test_error_status_code, code)
                )
            }
        } catch (e: IOException) {
            Log.e(AppConfig.TAG, "Connection test IOException", e)
            result = context.getString(R.string.connection_test_error, e.message)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Connection test Exception", e)
            result = context.getString(R.string.connection_test_error, e.message)
        } finally {
            conn.disconnect()
        }

        return Pair(elapsed, result)
    }

    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val httpPort = SettingsManager.getHttpPort()
        val content = HttpUtil.getUrlContent(url, 5000, httpPort) ?: return null
        val ipInfo = JsonUtil.fromJson(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return "(${country ?: "unknown"}) ${ip ?: "unknown"}"
    }

    fun getIpPurityResult(context: Context, guid: String): IpPurityResult {
        val failureDisplay = context.getString(R.string.connection_test_purity_failed)
        val socksPort = findAvailablePort()
        val httpPort = findAvailablePort(setOf(socksPort))
        val configResult = V2rayConfigManager.getV2rayConfig4PurityTest(context, guid, socksPort, httpPort)
        if (!configResult.status) {
            return IpPurityResult.failure("Config unavailable", failureDisplay)
        }

        val controller = V2RayNativeManager.newCoreController(TemporaryCoreCallback())
        try {
            controller.startLoop(configResult.content, 0)
            if (!waitForCore(controller)) {
                return IpPurityResult.failure("Temporary core failed to start", failureDisplay)
            }

            repeat(TEMP_PROXY_REQUEST_RETRY) {
                val content = HttpUtil.getUrlContent(SettingsManager.getIpPurityApiUrl(), TEMP_PROXY_REQUEST_TIMEOUT_MS, httpPort)
                val result = parseIpPurityResult(content)
                if (result != null) {
                    return result
                }
                Thread.sleep(TEMP_PROXY_REQUEST_WAIT_MS)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to check IP purity", e)
            return IpPurityResult.failure(e.message ?: "Unknown error", failureDisplay)
        } finally {
            stopTemporaryCore(controller)
        }

        return IpPurityResult.failure("Empty or invalid response", failureDisplay)
    }

    fun getIpPurityScore(context: Context, guid: String): Int {
        val result = getIpPurityResult(context, guid)
        return result.purityScore.removeSuffix("%").toIntOrNull() ?: -1
    }

    private fun waitForCore(controller: CoreController): Boolean {
        repeat(TEMP_CORE_START_RETRY) {
            if (controller.isRunning) {
                return true
            }
            Thread.sleep(TEMP_CORE_START_WAIT_MS)
        }
        return controller.isRunning
    }

    private fun stopTemporaryCore(controller: CoreController) {
        try {
            if (controller.isRunning) {
                controller.stopLoop()
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to stop temporary core", e)
        }
    }

    private fun parseIpPurityResult(content: String?): IpPurityResult? {
        if (content.isNullOrBlank()) {
            return null
        }
        val json = JsonUtil.parseString(content) ?: return null
        val score = getPercentageString(json, "fraudScore") ?: return null
        val category = when (json.getBooleanValue("isResidential")) {
            true -> "住宅"
            false -> "机房"
            null -> "未知"
        }
        val origin = when (json.getBooleanValue("isBroadcast")) {
            true -> "广播"
            false -> "原生"
            null -> "未知"
        }
        val emoji = getPurityEmoji(score)
        return IpPurityResult(
            exitIp = json.getStringValue("ip").orEmpty(),
            purityScore = score,
            purityEmoji = emoji,
            ipCategory = category,
            ipOrigin = origin,
            purityDisplay = "$emoji $score $category|$origin"
        )
    }

    private fun getPercentageString(json: JsonObject, key: String): String? {
        val element = json.get(key) ?: return null
        return try {
            when {
                element.isJsonNull -> null
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> "${element.asNumber.toInt()}%"
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val raw = element.asString.trim()
                    if (raw.isEmpty()) null else if (raw.endsWith("%")) raw else "$raw%"
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.getStringValue(key: String): String? {
        val element = get(key) ?: return null
        return try {
            if (element.isJsonNull) null else element.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.getBooleanValue(key: String): Boolean? {
        val element = get(key) ?: return null
        return try {
            if (element.isJsonNull) null else element.asBoolean
        } catch (_: Exception) {
            null
        }
    }

    private fun getPurityEmoji(score: String): String {
        val value = score.removeSuffix("%").toIntOrNull() ?: return "❓"
        return when {
            value <= 10 -> "⚪"
            value <= 30 -> "🟢"
            value <= 50 -> "🟡"
            value <= 70 -> "🟠"
            value <= 90 -> "🔴"
            else -> "⚫"
        }
    }

    private fun findAvailablePort(excluded: Set<Int> = emptySet()): Int {
        repeat(10) {
            ServerSocket(0).use { serverSocket ->
                val port = serverSocket.localPort
                if (port !in excluded) {
                    return port
                }
            }
        }
        return if (excluded.contains(18080)) 18081 else 18080
    }

    private class TemporaryCoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0

        override fun shutdown(): Long = 0

        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }
}
