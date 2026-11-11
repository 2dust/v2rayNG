package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Proxy
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * Measures configs so the app can rank them and suggest the best ones for the current network.
 *
 * What is measured, and why in this shape:
 *  - **Ping** goes through the core itself (`measureOutboundDelay`), i.e. a real round trip over the
 *    config's own protocol, so it is comparable across config types.
 *  - **Upload/download** are measured by pushing traffic through the running tunnel's local proxy.
 *    Real throughput needs the core up, so these numbers belong to the connected config; the ranking
 *    falls back to ping for everything else instead of inventing numbers.
 *
 * Results are stored per operator ([NetworkOperator]) because the ranking is only meaningful on the
 * network it was measured on.
 */
object ConfigBenchmark {

    data class Measurement(
        val guid: String,
        val pingMs: Long = NOT_MEASURED,
        val downloadMbps: Double? = null,
        val uploadMbps: Double? = null,
        val measuredAt: Long = 0L,
    )

    data class Throughput(val downloadMbps: Double?, val uploadMbps: Double?)

    /** Successful measurements always win; a ping-only run keeps the speeds measured earlier. */
    fun merge(
        stored: Map<String, Measurement>,
        fresh: Collection<Measurement>
    ): Map<String, Measurement> {
        val result = stored.toMutableMap()
        fresh.forEach { update ->
            val old = result[update.guid]
            result[update.guid] = Measurement(
                guid = update.guid,
                pingMs = update.pingMs.takeIf { it > 0 } ?: old?.pingMs ?: NOT_MEASURED,
                downloadMbps = update.downloadMbps ?: old?.downloadMbps,
                uploadMbps = update.uploadMbps ?: old?.uploadMbps,
                measuredAt = if (update.pingMs > 0 || update.downloadMbps != null) {
                    maxOf(update.measuredAt, old?.measuredAt ?: 0L)
                } else {
                    old?.measuredAt ?: 0L
                },
            )
        }
        return result
    }

    /** Lowest ping first; equal pings are ordered by the faster download. */
    fun rank(measurements: Collection<Measurement>): List<Measurement> =
        measurements.sortedWith(
            compareBy<Measurement> { if (it.pingMs > 0) it.pingMs else Long.MAX_VALUE }
                .thenByDescending { it.downloadMbps ?: 0.0 }
        )

    fun suggestions(measurements: Collection<Measurement>, limit: Int = 3): List<Measurement> =
        rank(measurements.filter { it.pingMs > 0 }).take(limit)

    // -------------------------------------------------------------------------
    // Ping, through the core
    // -------------------------------------------------------------------------

    /** Real round-trip per config; unreachable ones come back as [NOT_MEASURED]. */
    suspend fun pingConfigs(
        context: Context,
        guids: List<String>,
        concurrency: Int = 4,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Map<String, Long> = coroutineScope {
        val gate = Semaphore(concurrency)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val total = guids.size
        val results = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())

        guids.map { guid ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val latency = measurePing(context, guid)
                    results[guid] = latency
                    onProgress(done.incrementAndGet(), total)
                }
            }
        }.forEach { it.join() }

        results.toMap()
    }

    private suspend fun measurePing(context: Context, guid: String): Long =
        withContext(Dispatchers.IO) {
            try {
                val built = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
                if (!built.status) return@withContext NOT_MEASURED
                CoreNativeManager.measureOutboundDelay(
                    built.content,
                    SettingsManager.getDelayTestUrl()
                )
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "ConfigBenchmark: ping failed for $guid", e)
                NOT_MEASURED
            }
        }

    // -------------------------------------------------------------------------
    // Throughput, through the running tunnel
    // -------------------------------------------------------------------------

    suspend fun measureThroughput(): Throughput = withContext(Dispatchers.IO) {
        val port = SettingsManager.getHttpPort()
        if (port <= 0) return@withContext Throughput(null, null)
        Throughput(downloadMbps(port), uploadMbps(port))
    }

    private fun client(port: Int, timeoutSeconds: Long): OkHttpClient {
        // The local inbound only asks for a password when the user set one; OkHttp rejects a null
        // Credentials argument, so the header is built once and reused by the authenticator.
        val username = SettingsManager.getSocksUsername().orEmpty()
        val password = SettingsManager.getSocksPassword().orEmpty()
        val authHeader = if (username.isEmpty()) null else Credentials.basic(username, password)
        return OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, port)))
            .proxyAuthenticator { _, response ->
                if (authHeader == null || response.request.header("Proxy-Authorization") != null) {
                    null
                } else {
                    response.request.newBuilder()
                        .header("Proxy-Authorization", authHeader)
                        .build()
                }
            }
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    private fun downloadMbps(port: Int): Double? = try {
        val call = client(port, DOWNLOAD_TIMEOUT_S)
            .newCall(Request.Builder().url("$SPEED_URL/__down?bytes=$DOWNLOAD_BYTES").build())

        val started = System.nanoTime()
        var received = 0L
        call.execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    received += read
                }
            }
        }
        mbps(received, started)
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "ConfigBenchmark: download test failed", e)
        null
    }

    private fun uploadMbps(port: Int): Double? = try {
        val payload = ByteArray(UPLOAD_BYTES)
        val request = Request.Builder()
            .url("$SPEED_URL/__up")
            .post(payload.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val started = System.nanoTime()
        val ok = client(port, UPLOAD_TIMEOUT_S).newCall(request).execute().use { it.isSuccessful }
        if (!ok) null else mbps(UPLOAD_BYTES.toLong(), started)
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "ConfigBenchmark: upload test failed", e)
        null
    }

    /** Bits per second over the elapsed time, ignoring runs too short to mean anything. */
    private fun mbps(bytes: Long, startedNanos: Long): Double? {
        val seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0
        if (seconds < MIN_MEASURED_SECONDS || bytes <= 0) return null
        return bytes * 8.0 / seconds / 1_000_000.0
    }

    // -------------------------------------------------------------------------
    // Storage, per operator
    // -------------------------------------------------------------------------

    fun load(operator: String): Map<String, Measurement> {
        val json = MmkvManager.decodeSettingsString(key(operator)) ?: return emptyMap()
        val stored = try {
            JsonUtil.fromJsonSafe(json, Array<Measurement>::class.java)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ConfigBenchmark: could not read results", e)
            null
        } ?: return emptyMap()
        return stored.associateBy { it.guid }
    }

    fun store(operator: String, measurements: Collection<Measurement>) {
        try {
            MmkvManager.encodeSettings(
                key(operator),
                JsonUtil.toJson(measurements.toTypedArray())
            )
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "ConfigBenchmark: could not save results", e)
        }
    }

    private fun key(operator: String) =
        "${AppConfig.PREF_BENCHMARK_PREFIX}${operator.lowercase().replace(' ', '_')}"

    const val NOT_MEASURED = -1L

    private const val SPEED_URL = "https://speed.cloudflare.com"
    private const val DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val UPLOAD_BYTES = 2 * 1024 * 1024
    private const val DOWNLOAD_TIMEOUT_S = 30L
    private const val UPLOAD_TIMEOUT_S = 40L
    private const val MIN_MEASURED_SECONDS = 0.15
}
