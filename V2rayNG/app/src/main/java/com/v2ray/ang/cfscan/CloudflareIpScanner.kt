package com.v2ray.ang.cfscan

import com.v2ray.ang.cfscan.model.SpeedTestResult
import com.v2ray.ang.cfscan.speedtest.SpeedTestEngine
import com.v2ray.ang.cfscan.utils.IpRangeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloudflare IP scanner facade for v2rayNG.
 *
 * Credits: https://github.com/Murtaza-codes/cf-scanner-android
 */
object CloudflareIpScanner {

    private const val CLOUDFLARE_IPV4_URL = "https://www.cloudflare.com/ips-v4"

    /** Progress status keys — resolve via app language string resources. */
    const val STATUS_FETCHING = "fetching"
    const val STATUS_PING = SpeedTestEngine.Status.PING
    const val STATUS_DOWNLOAD = SpeedTestEngine.Status.DOWNLOAD
    const val STATUS_TESTING = SpeedTestEngine.Status.TESTING

    // Built-in Cloudflare IPv4 ranges (fallback when remote fetch fails)
    private val BUILTIN_IPV4_RANGES = """
173.245.48.0/20
103.21.244.0/22
103.22.200.0/22
103.31.4.0/22
141.101.64.0/18
108.162.192.0/18
190.93.240.0/20
188.114.96.0/20
197.234.240.0/22
198.41.128.0/17
162.158.0.0/15
104.16.0.0/12
172.64.0.0/17
172.64.128.0/18
172.64.192.0/19
172.64.224.0/22
172.64.229.0/24
172.64.230.0/23
172.64.232.0/21
172.64.240.0/21
172.64.248.0/21
172.65.0.0/16
172.66.0.0/16
172.67.0.0/16
131.0.72.0/22
    """.trimIndent()

    data class ScanProgress(
        val current: Int,
        val total: Int,
        val status: String,
        val currentIp: String = ""
    )

    data class ScanBestResult(
        val ipAddress: String,
        val avgLatencyMs: Double,
        val downloadSpeedMBps: Double = 0.0,
        val packetLoss: Double = 0.0
    )

    /**
     * Full default search:
     * 1) TCP ping 100 IPs (3 times each, concurrency 8)
     * 2) Download-speed test top 20 (10s each, concurrency 5)
     * 3) Pick best by loss → latency → download speed
     *
     * Warning: download stage can use a lot of mobile data.
     */
    suspend fun findBestCloudflareIp(
        testCount: Int = 100,
        pingCount: Int = 3,
        maxConcurrentPings: Int = 8,
        downloadTest: Boolean = true,
        downloadTestCount: Int = 20,
        downloadDurationMs: Long = 10_000L,
        maxConcurrentDownloads: Int = 5,
        speedLimit: Double = 0.0,
        latencyLimit: Double = 0.0,
        onProgress: (ScanProgress) -> Unit = {}
    ): ScanBestResult? {
        onProgress(ScanProgress(0, testCount, STATUS_FETCHING))
        val ranges = withContext(Dispatchers.IO) { loadIpv4Ranges() }
        if (ranges.isEmpty()) {
            throw IllegalStateException("No Cloudflare IP ranges available")
        }

        val engine = SpeedTestEngine()
        val config = SpeedTestEngine.TestConfig(
            testCount = testCount,
            pingCount = pingCount,
            pingTimeout = 1000,
            downloadTest = downloadTest,
            downloadDuration = downloadDurationMs,
            speedLimit = speedLimit,
            latencyLimit = latencyLimit,
            port = 443,
            pingOnly = false,
            downloadTestCount = downloadTestCount,
            pingFirst = true,
            maxConcurrentDownloads = maxConcurrentDownloads,
            maxConcurrentPings = maxConcurrentPings
        )

        return try {
            val results = runEngine(engine, ranges, config, onProgress)
            val best = results
                .sortedWith(
                    compareBy<SpeedTestResult> { it.packetLoss }
                        .thenBy { it.avgLatency }
                        .thenByDescending { it.downloadSpeed }
                )
                .firstOrNull()
                ?: return null
            ScanBestResult(
                ipAddress = best.ipAddress,
                avgLatencyMs = best.avgLatency,
                downloadSpeedMBps = best.downloadSpeed,
                packetLoss = best.packetLoss
            )
        } finally {
            engine.stopTest()
        }
    }

    private suspend fun runEngine(
        engine: SpeedTestEngine,
        ranges: List<IpRangeParser.IpRange>,
        config: SpeedTestEngine.TestConfig,
        onProgress: (ScanProgress) -> Unit
    ): List<SpeedTestResult> = suspendCancellableCoroutine { cont ->
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                var completed: List<SpeedTestResult> = emptyList()
                engine.startTest(
                    ipRanges = ranges,
                    config = config,
                    onProgress = { progress ->
                        onProgress(
                            ScanProgress(
                                current = progress.current,
                                total = progress.total,
                                status = progress.status,
                                currentIp = progress.currentIp
                            )
                        )
                    },
                    onResult = {},
                    onComplete = { sorted ->
                        completed = sorted
                    }
                )
                if (cont.isActive) {
                    cont.resume(completed)
                }
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }
        }
        cont.invokeOnCancellation {
            engine.stopTest()
            job.cancel()
        }
    }

    private fun loadIpv4Ranges(): List<IpRangeParser.IpRange> {
        val remote = fetchCloudflareIpv4Content()
        val content = if (!remote.isNullOrBlank()) {
            remote.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } else {
            BUILTIN_IPV4_RANGES
        }
        val parsed = IpRangeParser.parseIpFile(content)
        return parsed.ifEmpty { IpRangeParser.parseIpFile(BUILTIN_IPV4_RANGES) }
    }

    private fun fetchCloudflareIpv4Content(): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(CLOUDFLARE_IPV4_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
