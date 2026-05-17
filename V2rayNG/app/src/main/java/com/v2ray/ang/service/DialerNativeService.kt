package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.net.SocketException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// This class is responsible for forwarding xray's HTTP/WS requests through okhttp,
// so that its TLS and traffic characteristics are okhttp instead of golang/utls.
// Only WS and xhttp package-up are supported for now.
// ws:
// DialerNativeService connects to the control WebSocket provided by the xray core.
// Then xray sends a task message with method "WS" and the server URL to the control WebSocket.
// DialerNativeService opens a WebSocket connection to the server URL, and forwards messages between the control WebSocket and the target WebSocket.
// xhttp(package-up):
// A task message with streaming down (method == "GET" and streamResponse == true), let's call it Task A.
// A task message with unary down (streamResponse == false), let's call it Task B.
// 1. DialerNativeService connects to the control WebSocket provided by the xray core.
// 2. Xray sends Task A, DialerNativeService sends "ok" and connects to the target URL, then sends the "GET" request to the server; let's call this "Connection A".
// 3. Xray sends a Task B, called B_1. DialerNativeService sends "ok" and sends the data body to the target URL. Whatever the response is, DialerNativeService sends "ok" or "fail" back to the control WebSocket and closes the connection for Task B_1.
// 4. The server returns the response for Task B_1 through Connection A, and DialerNativeService forwards the response body to the control WebSocket.
// 5. Xray sends another Task B, called B_2.
// ...
// Finally, the xray client core sends all data through the B_1, B_2, ... tasks.
// The server closes Connection A, and DialerNativeService closes Task A.
// The above is a complete cycle.
class DialerNativeService : IDialerService {
    companion object {
        private const val DEBUG_LOG = false
        private val NEXT_SOCKET_ID = AtomicLong(0L)

        private const val CONTROL_SOCKET_IDLE = 0
        private const val CONTROL_SOCKET_OPENING = 1
        private const val CONTROL_LOOP_DELAY_MS = 1000L
        private const val UNARY_BODY_WAIT_TIMEOUT_MS = 15_000L
        private val TOKEN_REGEX = Regex("""/websocket\?token=([^"'\s]+)""")
        private val METHODS_WITHOUT_BODY = setOf("GET", "HEAD")
        private val HEADERS_BLACKLIST = hashSetOf(
            // AI suggest:
            "host",
            "content-length",
            "transfer-encoding",
            "content-encoding",
            "connection",
            "upgrade",
            "sec-websocket-key",
            "sec-websocket-version",
            "sec-websocket-protocol",
            "Timing-Allow-Origin",
            // xray
            "Set-Cookie",
            "Cookie",
            "Origin",
            "Sec-CH-UA",
            "Sec-CH-UA-Mobile",
            "Sec-CH-UA-Platform",
            "DNT",
            "User-Agent",
            "Accept-Language",
            "Cache-Control",
            "Upgrade-Insecure-Requests",
            "Sec-Fetch-Site",
            "Sec-Fetch-Mode",
            "Sec-Fetch-User",
            "Sec-Fetch-Dest",
            "Referer",
            "Accept",
            "Priority",
            "Pragma",
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Methods",
            "Access-Control-Allow-Headers",
            "Access-Control-Expose-Headers",
            "Access-Control-Max-Age",
        )
    }

    @Volatile
    private var serviceJob = SupervisorJob()

    @Volatile
    private var scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val controlSocketState = AtomicInteger(CONTROL_SOCKET_IDLE)
    private val controlSockets = ConcurrentHashMap.newKeySet<WebSocket>()

    @Volatile
    private var controlUrl: String? = null
    private var loopJob: Job? = null
    private var client: OkHttpClient? = null

    @Suppress("UNUSED_PARAMETER")
    override fun start(context: Context, dialerAddr: String) {
        stop()
        serviceJob = SupervisorJob()
        scope = CoroutineScope(serviceJob + Dispatchers.IO)
        if (dialerAddr.isEmpty()) return

        val nativeClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)  // Disable read timeout for long-running streams
            .build()

        client = nativeClient
        running.set(true)
        loopJob = scope.launch {
            val resolvedControlUrl = resolveControlWsUrl(dialerAddr, nativeClient)
            if (resolvedControlUrl == null) {
                debug(
                    "BrowserDialer: failed to resolve control url from dialer endpoint: $dialerAddr"
                )
                running.set(false)
                return@launch
            }
            controlUrl = resolvedControlUrl
            debug("BrowserDialer: started dialerAddr=$dialerAddr controlUrl=$resolvedControlUrl idleGate=${controlSocketState.get()}")
            maintainControlSocketPool()
            while (isActive && running.get()) {
                maintainControlSocketPool()
                delay(CONTROL_LOOP_DELAY_MS)
            }
        }
    }

    override fun stop() {
        running.set(false)
        loopJob?.cancel()
        loopJob = null
        serviceJob.cancel()

        debug("BrowserDialer: stopping ${poolState()} controlUrl=$controlUrl")

        controlSockets.toTypedArray().forEach { socket ->
            runCatching { socket.close(1000, "stopped") }
        }
        controlSockets.clear()

        controlSocketState.set(CONTROL_SOCKET_IDLE)
        controlUrl = null

        serviceJob = SupervisorJob()
        scope = CoroutineScope(serviceJob + Dispatchers.IO)

        val oldClient = client
        client = null
        oldClient?.dispatcher?.cancelAll()
        oldClient?.connectionPool?.evictAll()
    }

    private fun maintainControlSocketPool() {
        debug("BrowserDialer: maintaining single idle control socket ${poolState()}")
        openControlSocket()
    }

    private fun openControlSocket(): Boolean {
        val localClient = client ?: return false
        if (!running.get()) return false
        val url = controlUrl ?: return false
        if (!controlSocketState.compareAndSet(
                CONTROL_SOCKET_IDLE,
                CONTROL_SOCKET_OPENING
            )
        ) return false

        val request = Request.Builder().url(url).build()
        return runCatching {
            val socket = localClient.newWebSocket(request, ControlSocketListener(url))
            controlSockets.add(socket)
            debug("BrowserDialer: opening control socket url=$url ${poolState()}")
            true
        }.getOrElse {
            controlSocketState.set(CONTROL_SOCKET_IDLE)
            debug("BrowserDialer: failed to open control socket", it)
            false
        }
    }

    private fun poolState(): String {
        return "idleGate=${controlSocketState.get()} liveSockets=${controlSockets.size}"
    }

    private fun debug(message: String, throwable: Throwable? = null) {
        @Suppress("KotlinConstantConditions")
        if (!DEBUG_LOG) return
        if (throwable == null) {
            LogUtil.d(AppConfig.TAG, message)
        } else {
            LogUtil.d(AppConfig.TAG, message, throwable)
        }
    }

    private fun resolveControlWsUrl(rawAddr: String, probeClient: OkHttpClient): String? {
        val uri = parseDialerUri(rawAddr) ?: return null
        val probeUrl = buildDialerProbeUrl(rawAddr) ?: return null
        val request = Request.Builder().url(probeUrl).get().build()
        val token = runCatching {
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                extractControlToken(response.body.string())
            }
        }.getOrNull() ?: return null
        val host = uri.host ?: return null
        return URI(
            "ws",
            uri.userInfo,
            host,
            uri.port,
            "/websocket",
            "token=$token",
            null
        ).toString()
    }

    private fun buildDialerProbeUrl(rawAddr: String): String? {
        val normalized = rawAddr.trim()
        if (normalized.isEmpty()) return null

        val uri = parseDialerUri(normalized) ?: return null
        val host = uri.host ?: return null
        val probeScheme = when (uri.scheme?.lowercase()) {
            "https", "wss" -> "https"
            else -> "http"
        }
        return URI(probeScheme, uri.userInfo, host, uri.port, "/", null, null).toString()
    }

    private fun parseDialerUri(rawAddr: String): URI? {
        val normalized = rawAddr.trim()
        if (normalized.isEmpty()) return null
        return runCatching {
            if (normalized.contains("://")) URI(normalized) else URI("http://$normalized")
        }.getOrNull()
    }

    private fun extractControlToken(html: String): String? {
        val match = TOKEN_REGEX.find(html) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private inner class ControlSocketListener(
        private val controlUrl: String
    ) : WebSocketListener() {
        private val socketId = if (DEBUG_LOG) NEXT_SOCKET_ID.incrementAndGet() else 0L
        private val taskAccepted = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private val taskStartedAtMs = AtomicLong(0L)

        @Volatile
        private var taskKind = "none"
        private var upstreamSocket: WebSocket? = null
        private var upstreamCall: Call? = null
        private var timeoutJob: Job? = null
        private var binaryHandler: ((ByteArray) -> Unit)? = null
        private var textHandler: ((String) -> Unit)? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            debug("BrowserDialer: control socket opened socketId=$socketId url=$controlUrl ${poolState()}")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (taskAccepted.compareAndSet(false, true)) {
                controlSocketState.set(CONTROL_SOCKET_IDLE)
                debug(
                    "BrowserDialer: control socket accepted task socketId=$socketId url=$controlUrl textSize=${text.length} ${poolState()}"
                )
                tryOpenNextControlSocket()
                handleTask(webSocket, BrowserDialerTask.parse(text))
                return
            }
            textHandler?.invoke(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!taskAccepted.get()) {
                failAndClose(webSocket, 1002, "task must be text json")
                return
            }
            binaryHandler?.invoke(bytes.toByteArray())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            cleanup(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val status = response?.code?.toString() ?: "no-http-response"
            val stateText = "${poolState()} accepted=${taskAccepted.get()} closed=${closed.get()}"
            if (isExpectedControlFailure(t, status)) {
                debug(
                    "BrowserDialer: control socket closed socketId=$socketId url=$controlUrl status=$status cause=${t.javaClass.simpleName} $stateText"
                )
                debug(
                    "BrowserDialer: control socket failure detail socketId=$socketId url=$controlUrl status=$status $stateText",
                    t
                )
            } else {
                debug(
                    "BrowserDialer: control socket failure socketId=$socketId url=$controlUrl status=$status $stateText",
                    t
                )
            }
            cleanup(webSocket)
        }

        private fun isExpectedControlFailure(t: Throwable, status: String): Boolean {
            if (!running.get() || closed.get()) return true
            if (status != "no-http-response") return false
            if (t is EOFException) return true
            if (t is SocketException) {
                val message = t.message.orEmpty().lowercase()
                if ("socket closed" in message || "software caused connection abort" in message) {
                    return true
                }
            }
            val message = t.message.orEmpty().lowercase()
            return "canceled" in message || "cancelled" in message
        }


        private fun tryOpenNextControlSocket() {
            scope.launch {
                if (running.get()) maintainControlSocketPool()
            }
        }

        private fun cleanup(webSocket: WebSocket) {
            if (!closed.compareAndSet(false, true)) return
            val removed = controlSockets.remove(webSocket)
            if (!taskAccepted.get()) {
                controlSocketState.set(CONTROL_SOCKET_IDLE)
                tryOpenNextControlSocket()
            }
            val started = taskStartedAtMs.get()
            val duration =
                if (started > 0L) (System.currentTimeMillis() - started).coerceAtLeast(0L) else -1L
            debug(
                "BrowserDialer: cleanup socketId=$socketId url=$controlUrl task=$taskKind taskAccepted=${taskAccepted.get()} removed=$removed durationMs=$duration ${poolState()}"
            )
            binaryHandler = null
            textHandler = null
            timeoutJob?.cancel()
            timeoutJob = null
            upstreamCall?.cancel()
            upstreamCall = null
            upstreamSocket?.close(1000, "control closed")
            upstreamSocket = null
            taskKind = "closed"
            taskStartedAtMs.set(0L)
        }

        private fun handleTask(webSocket: WebSocket, task: BrowserDialerTask?) {
            if (task == null) {
                failAndClose(webSocket, 1007, "invalid task")
                return
            }

            taskStartedAtMs.set(System.currentTimeMillis())
            taskKind = when {
                task.method == "WS" -> "ws"
                task.method == "GET" && task.streamResponse -> "streaming_get"
                !task.streamResponse -> "unary_${task.method.lowercase()}"
                else -> "unsupported"
            }

            when {
                task.method == "WS" -> handleWsTask(webSocket, task)
                task.method == "GET" && task.streamResponse -> handleStreamingGetTask(
                    webSocket,
                    task
                )

                !task.streamResponse -> handleUnaryTask(webSocket, task)
                else -> {
                    failAndClose(webSocket, 1003, "unsupported task")
                }
            }
        }

        private fun handleWsTask(controlSocket: WebSocket, task: BrowserDialerTask) {
            val localClient = client ?: run {
                failAndClose(controlSocket, 1011, "client unavailable")
                return
            }
            debug("BrowserDialer: handling WS task socketId=$socketId url=${task.url} protocols=${task.extra.protocols.size}")
            val requestBuilder = Request.Builder().url(task.url)
            if (task.extra.protocols.isNotEmpty()) {
                requestBuilder.header(
                    "Sec-WebSocket-Protocol",
                    task.extra.protocols.joinToString(",")
                )
            }

            val opened = AtomicBoolean(false)
            upstreamSocket =
                localClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.set(true)
                        try {
                            controlSocket.send("ok")
                        } catch (e: Exception) {
                            debug(
                                "BrowserDialer: failed to send ok for WS task",
                                e
                            )
                            webSocket.close(1000, "control failed")
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            controlSocket.send(text)
                        } catch (e: Exception) {
                            debug(
                                "BrowserDialer: control socket closed during WS message transfer socketId=$socketId",
                                e
                            )
                            webSocket.close(1000, "control closed")
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            controlSocket.send(bytes)
                        } catch (e: Exception) {
                            debug(
                                "BrowserDialer: control socket closed during WS binary transfer socketId=$socketId",
                                e
                            )
                            webSocket.close(1000, "control closed")
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        try {
                            controlSocket.close(1000, "upstream closed")
                        } catch (_: Exception) {
                            debug("BrowserDialer: control socket already closed")
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        if (!opened.get()) {
                            try {
                                controlSocket.send("fail")
                            } catch (e: Exception) {
                                debug(
                                    "BrowserDialer: control socket send failed socketId=$socketId",
                                    e
                                )
                            }
                        }
                        try {
                            controlSocket.close(1011, "upstream failure")
                        } catch (_: Exception) {
                            debug(
                                "BrowserDialer: control socket already closed socketId=$socketId"
                            )
                        }
                    }
                })

            textHandler = { message ->
                try {
                    upstreamSocket?.send(message)
                } catch (e: Exception) {
                    debug(
                        "BrowserDialer: upstream socket send failed socketId=$socketId",
                        e
                    )
                }
            }
            binaryHandler = { data ->
                try {
                    upstreamSocket?.send(data.toByteString())
                } catch (e: Exception) {
                    debug(
                        "BrowserDialer: upstream socket binary send failed socketId=$socketId",
                        e
                    )
                }
            }
        }

        private fun handleStreamingGetTask(controlSocket: WebSocket, task: BrowserDialerTask) {
            val localClient = client ?: run {
                failAndClose(controlSocket, 1011, "client unavailable")
                return
            }
            debug("BrowserDialer: handling streaming GET task socketId=$socketId url=${task.url}")
            val request = buildRequest(task, null)

            try {
                controlSocket.send("ok")
            } catch (e: Exception) {
                debug(
                    "BrowserDialer: failed to send ok for streaming GET socketId=$socketId",
                    e
                )
                return
            }

            scope.launch {
                val call = localClient.newCall(request)
                upstreamCall = call
                try {
                    call.execute().use { response ->
                        val source = response.body.source()
                        val buffer = Buffer()
                        while (running.get() && !closed.get()) {
                            try {
                                val read = source.read(buffer, DEFAULT_BUFFER_SIZE.toLong())
                                if (read < 0) break

                                // Send data, catch exception if WebSocket is closed
                                try {
                                    controlSocket.send(buffer.readByteString())
                                } catch (e: Exception) {
                                    debug(
                                        "BrowserDialer: WebSocket send failed during streaming, stopping stream socketId=$socketId",
                                        e
                                    )
                                    break
                                }
                            } catch (_: Exception) {
                                // Error reading from source, stop streaming
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    debug(
                        "BrowserDialer: streaming GET failed socketId=$socketId",
                        e
                    )
                    try {
                        controlSocket.send("fail")
                    } catch (_: Exception) {
                        // WebSocket may already be closed
                    }
                } finally {
                    upstreamCall = null
                    try {
                        controlSocket.close(1000, "streaming done")
                    } catch (_: Exception) {
                        debug(
                            "BrowserDialer: WebSocket already closed socketId=$socketId"
                        )
                    }
                }
            }
        }

        private fun handleUnaryTask(controlSocket: WebSocket, task: BrowserDialerTask) {
            val localClient = client ?: run {
                failAndClose(controlSocket, 1011, "client unavailable")
                return
            }
            debug("BrowserDialer: handling unary task socketId=$socketId method=${task.method} url=${task.url}")

            try {
                controlSocket.send("ok")
            } catch (e: Exception) {
                debug(
                    "BrowserDialer: failed to send ok for unary task socketId=$socketId",
                    e
                )
                return
            }

            val done = AtomicBoolean(false)

            timeoutJob = scope.launch {
                delay(UNARY_BODY_WAIT_TIMEOUT_MS)
                if (done.compareAndSet(false, true)) {
                    binaryHandler = null
                    textHandler = null
                    debug("BrowserDialer: unary task timed out waiting for payload socketId=$socketId method=${task.method} url=${task.url}")
                    failAndClose(controlSocket, 1000, "unary payload timeout")
                }
            }

            val executeRequest: (ByteArray?) -> Unit = { payload ->
                if (done.compareAndSet(false, true)) {
                    timeoutJob?.cancel()
                    timeoutJob = null
                    binaryHandler = null
                    textHandler = null
                    scope.launch {
                        val request = buildRequest(task, payload)
                        val call = localClient.newCall(request)
                        upstreamCall = call
                        try {
                            call.execute().use { response ->
                                try {
                                    controlSocket.send(if (response.isSuccessful) "ok" else "fail")
                                } catch (e: Exception) {
                                    debug(
                                        "BrowserDialer: WebSocket send failed for unary response socketId=$socketId",
                                        e
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            debug(
                                "BrowserDialer: unary request failed socketId=$socketId",
                                e
                            )
                            try {
                                controlSocket.send("fail")
                            } catch (_: Exception) {
                                // WebSocket may already be closed
                            }
                        } finally {
                            upstreamCall = null
                            try {
                                controlSocket.close(1000, "request done")
                            } catch (_: Exception) {
                                debug(
                                    "BrowserDialer: WebSocket already closed socketId=$socketId"
                                )
                            }
                        }
                    }
                }
            }

            binaryHandler = { body -> executeRequest(body.takeIf { it.isNotEmpty() }) }
            textHandler = { body -> executeRequest(body.toByteArray().takeIf { it.isNotEmpty() }) }
        }

        private fun buildRequest(task: BrowserDialerTask, payload: ByteArray?): Request {
            val requestBuilder = Request.Builder().url(task.url)
            // task.extra.headers.forEach { (key, value) -> requestBuilder.header(key, value) }
            // Just set no cache headers
            requestBuilder.header("Cache-Control", "no-cache, no-store, must-revalidate")
            task.extra.referrer?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("Referer", it) }
            val taskHeaders = task.extra.headers.filterKeys { key ->
                val lowerKey = key.lowercase()
                !HEADERS_BLACKLIST.any { blackKey -> blackKey.equals(lowerKey, ignoreCase = true) }
            }
            taskHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

            val method = task.method.uppercase()
            val methodAllowsBody = method !in METHODS_WITHOUT_BODY
            val body = when {
                methodAllowsBody && payload != null && payload.isNotEmpty() -> payload.toRequestBody(null)
                methodAllowsBody -> ByteArray(0).toRequestBody(null)
                else -> null
            }
            requestBuilder.method(method, body)
            return requestBuilder.build()
        }

        private fun failAndClose(socket: WebSocket, code: Int, reason: String) {
            try {
                socket.send("fail")
            } catch (e: Exception) {
                debug("BrowserDialer: failed to send fail message", e)
            }
            try {
                socket.close(code, reason)
            } catch (e: Exception) {
                debug("BrowserDialer: failed to close socket", e)
            }
        }
    }

    private data class BrowserDialerTask(
        val method: String,
        val url: String,
        val streamResponse: Boolean,
        val extra: Extra
    ) {
        data class Extra(
            val headers: Map<String, String> = emptyMap(),
            // val cookies: Map<String, String> = emptyMap(),
            val protocols: List<String> = emptyList(),
            val referrer: String? = null
        )

        companion object {
            fun parse(payload: String): BrowserDialerTask? {
                return runCatching {
                    val root = JSONObject(payload)
                    val method = root.optString("method")
                    val url = root.optString("url")
                    if (method.isBlank() || url.isBlank()) return null

                    val streamResponse = root.optBoolean("streamResponse", false)
                    val extraObject = root.optJSONObject("extra")
                    val headers = extraObject.optStringMap("headers")
                    // val cookies = extraObject.optStringMap("cookies")
                    val referrer = extraObject?.optString("referrer")?.takeIf { it.isNotBlank() }
                    val protocols = extraObject.optProtocols()

                    BrowserDialerTask(
                        method = method,
                        url = url,
                        streamResponse = streamResponse,
                        extra = Extra(
                            headers = headers,
                            // cookies = cookies,
                            protocols = protocols,
                            referrer = referrer
                        )
                    )
                }.getOrNull()
            }

            private fun JSONObject?.optStringMap(name: String): Map<String, String> {
                val child = this?.optJSONObject(name) ?: return emptyMap()
                val map = LinkedHashMap<String, String>()
                val iter = child.keys()
                while (iter.hasNext()) {
                    val key = iter.next()
                    map[key] = child.optString(key)
                }
                return map
            }

            private fun JSONObject?.optProtocols(): List<String> {
                val raw = this?.opt("protocol") ?: return emptyList()
                return when (raw) {
                    is String -> raw.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
                    is JSONArray -> buildList {
                        for (i in 0 until raw.length()) {
                            val item = raw.optString(i)
                            if (item.isNotBlank()) {
                                add(item)
                            }
                        }
                    }

                    else -> emptyList()
                }
            }
        }
    }
}
