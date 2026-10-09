package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.AetherEndpoint
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.blackholeSink
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object AetherDelayTester {

    /**
     * The whole test, tunnel start included. The native probe gives every other profile a 12-second
     * HTTP client and the current-server test a 12-second context; an Aether profile gets the same
     * budget, so a tunnel that cannot come up fails within it instead of after a minute-long scan.
     */
    internal const val TEST_BUDGET_MS = 12_000L
    private const val POLL_INTERVAL_MS = 250L
    private const val ATTEMPTS = 2

    /** No further attempt starts with less than this left; it could not tell anything. */
    private const val MIN_REQUEST_MS = 1_000L

    /** Port every Cloudflare edge answers on TCP, whatever port the tunnel itself uses. */
    private const val EDGE_TCP_PORT = 443
    private const val REACH_TIMEOUT_MS = 1000

    /** Stored for "no result": the server row shows nothing instead of a stale or a failed number. */
    const val UNTESTED = 0L

    private val tunnels = Mutex()

    internal enum class Route {
        ACTIVE_SESSION,
        NEW_TUNNEL,
        SKIP,
        NOT_READY,
    }

    suspend fun measure(context: Context, guid: String, profile: ProfileItem, url: String): Long =
        measureVia(context, guid, AetherCore.of(profile)) { port, deadline -> cancellableRequestDelay(port, url, deadline) }

    /**
     * Runs [probe] against [core]: the live session when it runs this core, a test tunnel on the
     * Aether listen port while no session runs on Aether, and none at all beside a session that does,
     * whose core holds that port. [probe] gets the core's SOCKS port and the deadline of the budget. A
     * chain, a routing target, a policy group or a custom configuration that runs on an Aether core is
     * measured this way, with its own Xray configuration, [configuration], which dials that port. The
     * test tunnel of a core that dials out through a hop of its chain takes that hop from [configuration].
     */
    suspend fun measureVia(
        context: Context,
        guid: String,
        core: AetherCore,
        configuration: String? = null,
        probe: suspend (port: Int, deadline: Long) -> Long,
    ): Long {
        val activeGuid = MmkvManager.getSelectServer()
        val session = withContext(Dispatchers.IO) { liveSession(context, activeGuid) }
        return when (route(guid, core, activeGuid, session)) {
            Route.ACTIVE_SESSION -> probe(session?.port ?: AetherCoreManager.socksPort, deadlineAfter(TEST_BUDGET_MS))
            Route.NEW_TUNNEL -> tunnels.withLock { throughNewTunnel(context, guid, core, configuration, probe) }
            Route.SKIP -> {
                // The live session's core holds the port every core listens on.
                LogUtil.i(AppConfig.TAG, "AetherTest: left untested beside the live Aether session, guid=$guid")
                UNTESTED
            }
            Route.NOT_READY -> {
                // The running profile is not a failure before its tunnel is up; it is just not measurable yet.
                LogUtil.i(AppConfig.TAG, "AetherTest: left untested, the live session is still connecting, guid=$guid")
                UNTESTED
            }
        }
    }

    /**
     * The daemon's live Aether session: its arguments when its process could be read, the port of its
     * SOCKS listener, whether that listener accepts connections yet, and the key of the exit-node its
     * core dials out through when its environment could be read, see [AetherExit.key].
     */
    internal class LiveSession(val arguments: List<String>?, val port: Int, val listening: Boolean, val exit: String? = null)

    private fun liveSession(context: Context, activeGuid: String?): LiveSession? =
        liveSessionOf(
            process = AetherCoreManager.sessionProcess(context),
            processesListed = AetherCoreManager.canListProcesses(),
            active = { activeGuid?.let(MmkvManager::decodeServerConfig) },
            answers = AetherCoreManager::answersSocks,
        )

    /**
     * The daemon's live Aether session, or null without one. Its core [process] names the running
     * profile and the port it listens on, whether it is still scanning or already listening. Only
     * where the processes cannot be listed does a listener on the port of the selected Aether profile,
     * [active], stand in for it; elsewhere such a listener is the core of another test, which listens
     * on that same port. [answers] tells whether a listener on a port answers.
     */
    internal fun liveSessionOf(
        process: AetherCoreManager.CoreProcess?,
        processesListed: Boolean,
        active: () -> ProfileItem?,
        answers: (Int) -> Boolean,
    ): LiveSession? {
        if (process != null) {
            val arguments = process.argv.drop(1)
            val port = AetherCoreManager.listenerPortOf(arguments) ?: AetherCoreManager.socksPort
            return LiveSession(arguments, port, answers(port), process.exit)
        }
        if (processesListed) return null
        val selected = active()?.takeIf { it.configType == EConfigType.AETHER } ?: return null
        val port = AetherCore.of(selected).port
        if (!answers(port)) return null
        return LiveSession(arguments = null, port = port, listening = true)
    }

    /**
     * The cheap probe behind "TCP ping": a TCP connect to the pinned edge address instead of a
     * full tunnel. It tells whether that edge is reachable, not whether the tunnel works, and a
     * profile left to the scanner has nothing to probe.
     */
    fun reachability(
        profile: ProfileItem,
        connect: (host: String, port: Int) -> Long = { host, port -> SpeedtestManager.socketConnectTime(host, port, REACH_TIMEOUT_MS) },
    ): Long {
        val host = probeHost(profile) ?: return UNTESTED
        return connect(host, EDGE_TCP_PORT)
    }

    internal fun probeHost(profile: ProfileItem): String? =
        if (AetherProtocol.fromString(profile.aetherProtocol).twoHops) {
            AetherEndpoint.parse(profile.aetherWiwOuter)?.host
        } else {
            AetherEndpoint.of(profile.server, profile.serverPort)?.host
        }

    /**
     * Where a test goes: through the live session for the core it runs, on the port the test dials and
     * out through the same exit-node; nowhere for any other core while the session runs, since every
     * core listens on the one Aether listen port, which the session's core holds; through a tunnel of
     * its own on that port while no session runs on Aether. The running core is told by the session's
     * arguments and the key of its exit-node; without the arguments, or without the key, the selected
     * profile stands in. While the session is still connecting, the running core is left untested
     * rather than failed.
     */
    internal fun route(guid: String, core: AetherCore, activeGuid: String?, session: LiveSession?): Route {
        if (session == null) return Route.NEW_TUNNEL
        val running = session.arguments?.let { arguments ->
            core.runsAs(arguments) && core.port == session.port && (session.exit?.let { it == core.exit.key } ?: (guid == activeGuid))
        } ?: (guid == activeGuid)
        if (running) return if (session.listening) Route.ACTIVE_SESSION else Route.NOT_READY
        return Route.SKIP
    }

    private suspend fun throughNewTunnel(
        context: Context,
        guid: String,
        core: AetherCore,
        configuration: String?,
        probe: suspend (port: Int, deadline: Long) -> Long,
    ): Long {
        // The clock starts before the spawn: the budget covers the whole test, as it does for every other profile.
        val deadline = deadlineAfter(TEST_BUDGET_MS)
        val arguments = core.arguments
        val port = core.port
        return AetherCoreManager.withProcess(
            context = context,
            arguments = arguments,
            exit = core.exit,
            source = "aether-test",
            onOutput = {},
            configuration = configuration,
        ) { output ->
            val needsWord = AetherCoreManager.readyNeedsWord(arguments) && AetherCoreManager.showsInfo(arguments)
            if (!awaitListening(port, output, deadline, needsWord)) {
                LogUtil.w(AppConfig.TAG, "AetherTest: the tunnel did not come up, guid=$guid")
                return@withProcess -1L
            }
            val delay = probe(port, deadline)
            if (delay < 0) LogUtil.w(AppConfig.TAG, "AetherTest: no answer through the tunnel, guid=$guid")
            delay
        } ?: -1L
    }

    /**
     * Waits until the listener on [port] answers, and, with [needsWord], until the core has also
     * written its word that the listener carries traffic; false when the core ends or [deadline] passes.
     */
    internal suspend fun awaitListening(port: Int, output: ReceiveChannel<String>, deadline: Long, needsWord: Boolean = false): Boolean {
        var wordSeen = !needsWord
        while (System.nanoTime() < deadline) {
            while (true) {
                val line = output.tryReceive().getOrNull() ?: break
                if (!wordSeen && AetherCoreManager.isReadyWord(line)) wordSeen = true
            }
            if (output.isClosedForReceive) return false
            if (wordSeen && withContext(Dispatchers.IO) { AetherCoreManager.answersSocks(port) }) return true
            delay(POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * [requestDelay], which a cancelled test ends at once. A cancelled coroutine does not interrupt the
     * thread that its requests block, so they are cancelled from here: they would otherwise keep a test
     * tunnel up for the rest of the budget, also beside a session that starts meanwhile.
     */
    internal suspend fun cancellableRequestDelay(port: Int, url: String, deadline: Long): Long = coroutineScope {
        val calls = ProbeCalls()
        val probe = async(Dispatchers.IO) { requestDelay(port, url, deadline, calls) }
        try {
            probe.await()
        } catch (e: CancellationException) {
            calls.cancel()
            throw e
        }
    }

    /** The requests of a probe, which [cancel] ends; a request that starts after it is cancelled as it starts. */
    internal class ProbeCalls {
        private val calls = mutableListOf<Call>()
        private var cancelled = false

        @Synchronized
        fun start(call: Call): Call {
            if (cancelled) call.cancel() else calls += call
            return call
        }

        @Synchronized
        fun cancel() {
            cancelled = true
            calls.forEach(Call::cancel)
        }
    }

    /**
     * The best of up to [ATTEMPTS] requests through the SOCKS port at [port], each given what is
     * left of the budget ending at [deadline]; -1 when none of them was answered in time. Its
     * requests start through [calls].
     */
    internal fun requestDelay(
        port: Int,
        url: String,
        deadline: Long = deadlineAfter(TEST_BUDGET_MS),
        calls: ProbeCalls = ProbeCalls(),
    ): Long {
        val request = try {
            Request.Builder().url(url).build()
        } catch (_: IllegalArgumentException) {
            return -1L
        }
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(AppConfig.LOOPBACK, port)))
            .build()
        return try {
            var best = -1L
            repeat(ATTEMPTS) {
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining < MIN_REQUEST_MS) return best
                // Derived clients share the pool, so a second attempt measures a warm connection. The connect
                // timeout is set as well because the SOCKS handshake happens inside the connect.
                val attempt = client.newBuilder()
                    .connectTimeout(remaining, TimeUnit.MILLISECONDS)
                    .callTimeout(remaining, TimeUnit.MILLISECONDS)
                    .build()
                val time = timedRequest(attempt, request, calls) ?: return@repeat
                if (best < 0 || time < best) best = time
            }
            best
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun deadlineAfter(ms: Long): Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)

    private fun timedRequest(client: OkHttpClient, request: Request, calls: ProbeCalls): Long? = try {
        val started = System.nanoTime()
        calls.start(client.newCall(request)).execute().use { response ->
            response.body.source().readAll(blackholeSink())
            if (response.code == 200 || response.code == 204) {
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            } else {
                null
            }
        }
    } catch (_: IOException) {
        null
    }
}
