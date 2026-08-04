package com.v2ray.ang.handler

import android.content.Context
import android.os.SystemClock
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PingType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.CustomConfigUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import libv2ray.CoreCallbackHandler
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Runs a single profile measurement with the ping type picked in the settings.
 */
object PingManager {

    const val FAILURE = -1L

    /** Reason of the last failed measurement, for the UI to show after a run. */
    @Volatile
    private var lastError: String? = null

    /**
     * Takes the last failure reason, clearing it.
     *
     * @return The reason, or null when nothing failed since the last call.
     */
    fun consumeLastError(): String? {
        val error = lastError
        lastError = null
        return error
    }

    private const val TCP_TIMEOUT_MS = 1500
    private const val PRECHECK_TIMEOUT_MS = 3000
    private const val PROXY_TIMEOUT_MS = 10_000
    private const val ICMP_TIMEOUT_MS = 4_000L
    private const val TEST_INBOUND_TAG = "ping-in"

    /**
     * Every test spins up its own core instance, and core.New() re-points Xray's system
     * dialer - a process wide singleton holding the DNS client and the outbound manager -
     * at the instance being created. Two instances at once therefore dial through each
     * other's (often already closed) plumbing, which surfaces as
     * `io: read/write on closed pipe` on all but one profile. Core based tests run one at
     * a time; the TCP pre-check and the TCP/ICMP types stay fully parallel.
     */
    private val coreTestMutex = Mutex()

    /**
     * Measures a profile with the configured ping type.
     *
     * @param context The context.
     * @param guid The profile GUID.
     * @param type The ping type, defaulting to the one selected in the settings.
     * @return The delay in milliseconds, or -1 when the test failed.
     */
    suspend fun ping(context: Context, guid: String, type: PingType = SettingsManager.getPingType()): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return FAILURE

        return when (type) {
            PingType.PROXY_GET -> proxyGetPing(context, guid, profile)
            PingType.PROXY_HEAD -> proxyHeadPing(context, guid, profile)
            PingType.TCP -> tcpPing(context, guid, profile)
            PingType.ICMP -> icmpPing(context, guid, profile)
        }
    }

    /**
     * Resolves the server address of a profile, reading raw JSON profiles from their config.
     *
     * @param context The context.
     * @param guid The profile GUID.
     * @param profile The decoded profile.
     * @return The host and port, or null when the profile carries no server address.
     */
    fun serverAddress(context: Context, guid: String, profile: ProfileItem): Pair<String, Int>? {
        if (profile.configType == EConfigType.CUSTOM) {
            val rawConfig = CustomConfigUtil.getRawConfig(context, guid, profile.server)
            CustomConfigUtil.getProxyOutbound(CustomConfigUtil.parseConfig(rawConfig))
                ?.let { CustomConfigUtil.extractHostAndPort(it) }
                ?.let { return it }
        }

        val host = profile.server?.takeIf { it.isNotBlank() && !it.contains("{") } ?: return null
        val port = profile.serverPort?.toIntOrNull() ?: return null
        return host to port
    }

    //region ping types

    /** The core does the GET itself, one instance per test, no listener involved. */
    private suspend fun proxyGetPing(context: Context, guid: String, profile: ProfileItem): Long {
        if (!tcpPrecheckPasses(context, guid, profile)) {
            return fail(profile, "no TCP answer from the server address")
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return fail(profile, configResult.errorMessage)
        }

        val url = SettingsManager.getDelayTestUrl()
        val (delay, error) = coreTestMutex.withLock {
            withContext(Dispatchers.IO) {
                CoreNativeManager.measureOutboundDelayDetailed(configResult.content, url)
            }
        }
        if (delay <= FAILURE) {
            return fail(profile, error ?: "no answer from $url")
        }
        return delay
    }

    /**
     * The library only ever sends GET, so HEAD runs a throwaway core instance with a local
     * SOCKS listener and issues the request from here.
     */
    private suspend fun proxyHeadPing(context: Context, guid: String, profile: ProfileItem): Long {
        if (!tcpPrecheckPasses(context, guid, profile)) {
            return fail(profile, "no TCP answer from the server address")
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return fail(profile, configResult.errorMessage)
        }

        return coreTestMutex.withLock {
            withContext(Dispatchers.IO) {
                val port = Utils.findRandomFreePort()
                val config = withSocksInbound(configResult.content, port)
                    ?: return@withContext fail(profile, "test config is not valid json")

                CoreNativeManager.initCoreEnv(context.applicationContext)
                val controller = CoreNativeManager.newCoreController(PingCallback)
                try {
                    controller.startLoop(config, 0)
                    val (delay, error) = requestThroughSocks(port, SettingsManager.getDelayTestUrl(), head = true)
                    if (delay <= FAILURE) fail(profile, error) else delay
                } catch (e: Exception) {
                    fail(profile, e.message ?: e.javaClass.simpleName)
                } finally {
                    try {
                        controller.stopLoop()
                    } catch (e: Exception) {
                        LogUtil.w(AppConfig.TAG, "Failed to stop ping core instance: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun tcpPing(context: Context, guid: String, profile: ProfileItem): Long {
        val (host, port) = serverAddress(context, guid, profile)
            ?: return fail(profile, "no server address in the profile")

        val delay = withContext(Dispatchers.IO) {
            SpeedtestManager.socketConnectTime(host, port, TCP_TIMEOUT_MS)
        }
        return if (delay <= FAILURE) fail(profile, "$host:$port did not accept a connection") else delay
    }

    private suspend fun icmpPing(context: Context, guid: String, profile: ProfileItem): Long {
        val (host, _) = serverAddress(context, guid, profile)
            ?: return fail(profile, "no server address in the profile")
        val command = if (host.contains(":")) "ping6" else "ping"

        // runInterruptible so the timeout can actually break the blocking read
        val delay = withTimeoutOrNull(ICMP_TIMEOUT_MS) {
            runInterruptible(Dispatchers.IO) {
                var process: Process? = null
                try {
                    process = Runtime.getRuntime().exec(arrayOf(command, "-c", "1", "-W", "2", host))
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    parseIcmpTime(output)
                } catch (e: IOException) {
                    LogUtil.w(AppConfig.TAG, "ICMP ping failed for $host: ${e.message}")
                    FAILURE
                } finally {
                    process?.destroy()
                }
            }
        } ?: FAILURE

        return if (delay <= FAILURE) fail(profile, "$host is not answering ICMP") else delay
    }

    //endregion

    //region helpers

    /**
     * Records why a profile failed and reports it as a failure.
     *
     * @param profile The profile being measured.
     * @param reason The reason, as reported by the core or by us.
     * @return Always [FAILURE], so callers can `return fail(...)`.
     */
    private fun fail(profile: ProfileItem, reason: String?): Long {
        val message = reason?.takeIf { it.isNotBlank() } ?: "unknown error"
        lastError = message
        LogUtil.w(AppConfig.TAG, "Ping failed for ${profile.remarks}: $message")
        return FAILURE
    }

    /**
     * Reads the round trip out of a `ping` report, e.g. "64 bytes from ...: time=12.3 ms".
     */
    private fun parseIcmpTime(output: String): Long {
        val match = Regex("time[=<]\\s*([0-9.]+)\\s*ms").find(output) ?: return FAILURE
        val millis = match.groupValues[1].toDoubleOrNull() ?: return FAILURE
        // A sub-millisecond answer is still an answer, so never report it as a failure
        return millis.toLong().coerceAtLeast(0L)
    }

    /**
     * A dead TCP endpoint fails fast instead of waiting out the request timeout.
     * Skipped for anything not speaking plain TCP to its server address.
     *
     * The timeout is deliberately generous: a working but distant server used to be reported
     * as a timeout here without its proxy ever being tried.
     */
    private suspend fun tcpPrecheckPasses(context: Context, guid: String, profile: ProfileItem): Boolean {
        if (profile.configType.isComplexType()
            || profile.configType == EConfigType.HYSTERIA2
            || profile.configType == EConfigType.WIREGUARD
            || profile.alpn?.startsWith("h3") == true
            || !profile.server.isNotNullEmpty()
            || profile.serverPort?.toIntOrNull() == null
        ) {
            return true
        }

        val (host, port) = serverAddress(context, guid, profile) ?: return true
        return withContext(Dispatchers.IO) {
            SpeedtestManager.socketConnectTime(host, port, PRECHECK_TIMEOUT_MS) > FAILURE
        }
    }

    /**
     * Adds a loopback SOCKS listener to a speedtest config so a request can be sent through it.
     *
     * @param configContent The speedtest config.
     * @param port The port the listener should bind to.
     * @return The patched config, or null when the config cannot be parsed.
     */
    private fun withSocksInbound(configContent: String, port: Int): String? {
        val json = JsonUtil.parseString(configContent) ?: return null

        val inbound = JsonObject().apply {
            addProperty("tag", TEST_INBOUND_TAG)
            addProperty("port", port)
            addProperty("listen", AppConfig.LOOPBACK)
            addProperty("protocol", "socks")
            add("settings", JsonObject().apply {
                addProperty("auth", "noauth")
                addProperty("udp", false)
            })
        }
        json.add("inbounds", JsonArray().apply { add(inbound) })

        return json.toString()
    }

    /**
     * Times a single request sent through the local SOCKS listener.
     *
     * @param port The listener port.
     * @param url The test url.
     * @param head True to send HEAD instead of GET.
     * @return The delay in milliseconds and null, or -1 and why the request failed.
     */
    private fun requestThroughSocks(port: Int, url: String, head: Boolean): Pair<Long, String?> {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(AppConfig.LOOPBACK, port)))
            .connectTimeout(PROXY_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(PROXY_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(PROXY_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        val request = Request.Builder()
            .url(url)
            .apply { if (head) head() else get() }
            .header("Connection", "close")
            .build()

        return try {
            val start = SystemClock.elapsedRealtime()
            var status = 0
            client.newCall(request).execute().use { response -> status = response.code }
            if (status >= 400) {
                FAILURE to "$url answered with $status"
            } else {
                (SystemClock.elapsedRealtime() - start) to null
            }
        } catch (e: Exception) {
            FAILURE to (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /** The throwaway instances have nothing to report back. */
    private object PingCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    //endregion
}
