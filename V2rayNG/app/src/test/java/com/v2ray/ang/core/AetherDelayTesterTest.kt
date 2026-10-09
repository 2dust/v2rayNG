package com.v2ray.ang.core

import com.v2ray.ang.core.AetherDelayTester.Route
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AetherDelayTesterTest {

    private fun aether(protocol: AetherProtocol) =
        ProfileItem.create(EConfigType.AETHER).apply { aetherProtocol = protocol.type }

    /** The live session, running [running] on its own unless [exit] says what its core dials out through. */
    private fun session(
        running: ProfileItem? = null,
        listening: Boolean = true,
        port: Int = AetherCoreManager.socksPort,
        exit: String? = running?.let { AetherExit.of(it).key },
    ) = AetherDelayTester.LiveSession(running?.let { AetherCoreManager.buildArguments(it, port) }, port, listening, exit)

    @Test
    fun aListenerOnTheAetherPortIsNoSessionWhereTheProcessesTellNone() {
        // The core of another test listens on the one Aether port; the selected Aether profile does not run.
        val selected = aether(AetherProtocol.WIREGUARD)
        val session = AetherDelayTester.liveSessionOf(process = null, processesListed = true, active = { selected }, answers = { true })
        assertEquals(null, session)
        assertEquals(Route.NEW_TUNNEL, AetherDelayTester.route("a", AetherCore.of(aether(AetherProtocol.MASQUE)), "b", session))
    }

    @Test
    fun withoutTheProcessesAListenerOnTheSelectedProfilesPortStandsInForTheSession() {
        val selected = aether(AetherProtocol.WIREGUARD)
        val port = AetherCore.of(selected).port
        val session = AetherDelayTester.liveSessionOf(process = null, processesListed = false, active = { selected }, answers = { it == port })!!
        assertEquals(null, session.arguments)
        assertEquals(port, session.port)
        assertTrue(session.listening)

        assertEquals(null, AetherDelayTester.liveSessionOf(null, processesListed = false, active = { selected }, answers = { false }))
        val vless = ProfileItem.create(EConfigType.VLESS)
        assertEquals(null, AetherDelayTester.liveSessionOf(null, processesListed = false, active = { vless }, answers = { true }))
        assertEquals(null, AetherDelayTester.liveSessionOf(null, processesListed = false, active = { null }, answers = { true }))
    }

    @Test
    fun theSessionIsReadFromItsCoreProcess() {
        val argv = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:20808", "--protocol", "wg")
        val process = AetherCoreManager.CoreProcess(pid = 4242, argv = argv, ownerAlive = true, sessionMarked = true, exit = "key")
        var asked: Int? = null
        val session = AetherDelayTester.liveSessionOf(process, processesListed = true, active = { error("not asked") }) { port ->
            asked = port
            false
        }!!
        assertEquals(argv.drop(1), session.arguments)
        assertEquals(20808, session.port)
        assertEquals(20808, asked)
        assertFalse(session.listening)
        assertEquals("key", session.exit)
    }

    @Test
    fun withoutAnAetherSessionEachTestGetsItsOwnTunnel() {
        val masque = aether(AetherProtocol.MASQUE)
        assertEquals(Route.NEW_TUNNEL, AetherDelayTester.route("a", AetherCore.of(masque), "a", session = null))
        assertEquals(Route.NEW_TUNNEL, AetherDelayTester.route("a", AetherCore.of(masque), null, session = null))
    }

    @Test
    fun theRunningProfileIsMeasuredThroughTheLiveSession() {
        val wireguard = aether(AetherProtocol.WIREGUARD).apply { server = "162.159.192.1"; serverPort = "2408" }
        // Told by the session's arguments, whichever profile is selected.
        assertEquals(Route.ACTIVE_SESSION, AetherDelayTester.route("a", AetherCore.of(wireguard), "b", session(wireguard)))
        // Without the process, the selected profile stands in for the running one.
        assertEquals(Route.ACTIVE_SESSION, AetherDelayTester.route("a", AetherCore.of(wireguard), "a", session()))
        assertEquals(Route.SKIP, AetherDelayTester.route("a", AetherCore.of(wireguard), "b", session()))
        // The same tunnel on another port, as once the Aether listen port is changed under a running session, is not
        // where the configuration of the test dials, nor can a test core take the port the session holds.
        assertEquals(Route.SKIP, AetherDelayTester.route("a", AetherCore.of(wireguard), "b", session(wireguard, port = 20808)))
    }

    @Test
    fun theRunningProfileIsLeftUntestedWhileItsSessionIsStillConnecting() {
        val wireguard = aether(AetherProtocol.WIREGUARD).apply { server = "162.159.192.1"; serverPort = "2408" }
        val connecting = session(wireguard, listening = false)
        // A request through a listener that is not up yet would fail, and that is not a failure of the profile.
        assertEquals(Route.NOT_READY, AetherDelayTester.route("a", AetherCore.of(wireguard), "a", connecting))
        // The other routes do not depend on the listener.
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.GOOL)), "a", connecting))
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.MASQUE)), "a", connecting))
    }

    @Test
    fun theSessionMeasuresOnlyACoreThatDialsOutThroughTheSameExitNode() {
        val wireguard = aether(AetherProtocol.WIREGUARD).apply { server = "162.159.192.1"; serverPort = "2408" }
        val hop = ProfileItem.create(EConfigType.VLESS).apply { remarks = "hop"; server = "1.2.3.4"; serverPort = "443" }
        val alone = AetherCore.of(wireguard)
        val chained = alone.copy(exit = AetherExit.through(listOf(hop)))
        // The session runs the profile on its own: the profile is measured through it, a chain that dials out
        // through a hop is not, though it runs the same tunnel on the same port.
        val plainSession = session(wireguard)
        assertEquals(Route.ACTIVE_SESSION, AetherDelayTester.route("a", alone, "b", plainSession))
        assertEquals(Route.SKIP, AetherDelayTester.route("c", chained, "b", plainSession))
        // And the other way round.
        val chainSession = session(wireguard, exit = chained.exit.key)
        assertEquals(Route.ACTIVE_SESSION, AetherDelayTester.route("c", chained, "b", chainSession))
        assertEquals(Route.SKIP, AetherDelayTester.route("a", alone, "b", chainSession))
        // Without the key, only the selected profile stands for what the session runs.
        assertEquals(Route.ACTIVE_SESSION, AetherDelayTester.route("c", chained, "c", session(wireguard, exit = null)))
        assertEquals(Route.SKIP, AetherDelayTester.route("c", chained, "b", session(wireguard, exit = null)))
    }

    @Test
    fun everyOtherCoreIsLeftUntestedBesideTheLiveSession() {
        // The session's core holds the one port every core listens on, whichever key either of them uses.
        val running = aether(AetherProtocol.MASQUE).apply { server = "162.159.198.1"; serverPort = "443" }
        val live = session(running)
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.MASQUE)), "a", live))
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.WIREGUARD)), "a", live))
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.GOOL)), "a", live))
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.MIM)), "a", live))
        // Selected, but not what the session runs: it is not measured through that session.
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.MASQUE)), "b", live))
        // Without the session's process, every profile but the selected one.
        assertEquals(Route.SKIP, AetherDelayTester.route("b", AetherCore.of(aether(AetherProtocol.WIREGUARD)), "a", session()))
    }

    @Test
    fun aTestCoreThatDialsPsiphonIsReadyOnlyOnTheCoresWord() = runBlocking {
        SocksStub().use { socks ->
            val output = Channel<String>(Channel.UNLIMITED)
            // The listener answers, but nothing said yet: not ready within a short budget.
            assertFalse(AetherDelayTester.awaitListening(socks.port, output, deadlineAfterMs(600), needsWord = true))
            // Without the need, the answering listener is enough.
            assertTrue(AetherDelayTester.awaitListening(socks.port, output, deadlineAfterMs(2_000), needsWord = false))

            output.trySend("[2026-09-24T10:00:00.000Z INFO  aether] [*] starting psiphon through the tunnel at 127.0.0.1:10820")
            output.trySend("[2026-09-24T10:00:00.000Z INFO  aether] [+] psiphon is ready; 127.0.0.1:${socks.port} leaves through psiphon, carried by the tunnel")
            assertTrue(AetherDelayTester.awaitListening(socks.port, output, deadlineAfterMs(2_000), needsWord = true))

            // A core that ends before its word is a failed test, not a wait.
            val ended = Channel<String>(Channel.UNLIMITED)
            ended.close()
            assertFalse(AetherDelayTester.awaitListening(socks.port, ended, deadlineAfterMs(2_000), needsWord = true))
        }
    }

    private fun deadlineAfterMs(ms: Long): Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)

    @Test
    fun aTcpPingProbesThePinnedEdgeInsteadOfOpeningATunnel() {
        val probed = mutableListOf<Pair<String, Int>>()
        val connect = { host: String, port: Int -> probed.add(host to port); 42L }

        val pinned = aether(AetherProtocol.MASQUE).apply { server = "162.159.198.1"; serverPort = "2408" }
        assertEquals(42L, AetherDelayTester.reachability(pinned, connect))
        assertEquals(listOf("162.159.198.1" to 443), probed)

        probed.clear()
        val hops = aether(AetherProtocol.GOOL).apply { aetherWiwOuter = "162.159.192.1:2408"; aetherWiwInner = "188.114.96.1:894" }
        assertEquals(42L, AetherDelayTester.reachability(hops, connect))
        assertEquals(listOf("162.159.192.1" to 443), probed)

        probed.clear()
        val masqueHops = aether(AetherProtocol.MIM).apply { aetherWiwOuter = "162.159.197.3:443"; aetherWiwInner = "188.114.96.1:443" }
        assertEquals(42L, AetherDelayTester.reachability(masqueHops, connect))
        assertEquals(listOf("162.159.197.3" to 443), probed)

        probed.clear()
        val unreachable = { _: String, _: Int -> -1L }
        assertEquals(-1L, AetherDelayTester.reachability(pinned, unreachable))
    }

    @Test
    fun aProfileLeftToTheScannerHasNothingToProbe() {
        val probes = { _: String, _: Int -> throw AssertionError("must not probe") }

        assertEquals(AetherDelayTester.UNTESTED, AetherDelayTester.reachability(aether(AetherProtocol.MASQUE), probes))
        assertEquals(AetherDelayTester.UNTESTED, AetherDelayTester.reachability(aether(AetherProtocol.GOOL), probes))
        assertEquals(AetherDelayTester.UNTESTED, AetherDelayTester.reachability(aether(AetherProtocol.MIM), probes))
        val halfPinned = aether(AetherProtocol.WIREGUARD).apply { server = "162.159.198.1" }
        assertEquals(AetherDelayTester.UNTESTED, AetherDelayTester.reachability(halfPinned, probes))
        val hostName = aether(AetherProtocol.WIREGUARD).apply { server = "engage.cloudflareclient.com"; serverPort = "2408" }
        assertEquals(AetherDelayTester.UNTESTED, AetherDelayTester.reachability(hostName, probes))
        assertEquals(0L, AetherDelayTester.UNTESTED)
    }

    @Test
    fun aDelayIsMeasuredThroughTheSocksPort() {
        HttpStub("204 No Content").use { http ->
            SocksStub().use { socks ->
                val delay = AetherDelayTester.requestDelay(socks.port, "http://127.0.0.1:${http.port}/generate_204")
                assertTrue(delay >= 0, "delay was $delay")
            }
        }
    }

    @Test
    fun aPlainOkAlsoCountsAsAnAnswer() {
        HttpStub("200 OK").use { http ->
            SocksStub().use { socks ->
                assertTrue(AetherDelayTester.requestDelay(socks.port, "http://127.0.0.1:${http.port}/") >= 0)
            }
        }
    }

    @Test
    fun anErrorStatusIsNotADelay() {
        HttpStub("500 Internal Server Error").use { http ->
            SocksStub().use { socks ->
                assertEquals(-1L, AetherDelayTester.requestDelay(socks.port, "http://127.0.0.1:${http.port}/"))
            }
        }
    }

    @Test
    fun nothingListeningOrABrokenUrlIsNotADelay() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertEquals(-1L, AetherDelayTester.requestDelay(closedPort, "http://127.0.0.1:1/generate_204"))
        assertEquals(-1L, AetherDelayTester.requestDelay(closedPort, "not a url"))
    }

    @Test
    fun aProbeThatGetsNoAnswerGivesUpWithinItsBudget() {
        StallingSocksStub().use { socks ->
            val started = System.nanoTime()
            val deadline = started + TimeUnit.MILLISECONDS.toNanos(1_500)
            val delay = AetherDelayTester.requestDelay(socks.port, "http://127.0.0.1:1/generate_204", deadline)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(-1L, delay)
            // One attempt within the budget, no second one, and nothing left to OkHttp's own 10-second connect timeout.
            assertTrue(elapsedMs < 6_000, "gave up after $elapsedMs ms")
        }
    }

    @Test
    fun aCancelledTestEndsItsProbeAtOnce() {
        StallingSocksStub().use { socks ->
            runBlocking {
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AetherDelayTester.TEST_BUDGET_MS)
                val probe = launch(Dispatchers.Default) {
                    AetherDelayTester.cancellableRequestDelay(socks.port, "http://127.0.0.1:1/generate_204", deadline)
                }
                assertTrue(socks.awaitClient(5_000), "the probe did not connect")
                val started = System.nanoTime()
                probe.cancelAndJoin()
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                // Its request would otherwise wait for the rest of the 12-second budget, and keep the tunnel up.
                assertTrue(elapsedMs < 2_000, "ended after $elapsedMs ms")
            }
        }
    }

    @Test
    fun aProbeRequestThatStartsAfterItsTestEndedIsCancelled() {
        val client = OkHttpClient()
        val request = Request.Builder().url("http://127.0.0.1:1/").build()
        val calls = AetherDelayTester.ProbeCalls()

        val running = calls.start(client.newCall(request))
        calls.cancel()
        val late = calls.start(client.newCall(request))

        assertTrue(running.isCanceled())
        assertTrue(late.isCanceled())
    }

    /** Accepts connections and never answers, like a tunnel whose far end is gone. */
    private class StallingSocksStub : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val clients = mutableListOf<Socket>()
        private val connected = CountDownLatch(1)
        val port: Int get() = server.localPort

        /** Whether a client connected within [timeoutMs]. */
        fun awaitClient(timeoutMs: Long): Boolean = connected.await(timeoutMs, TimeUnit.MILLISECONDS)

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                    synchronized(clients) { clients.add(client) }
                    connected.countDown()
                }
            }
        }

        override fun close() {
            server.close()
            synchronized(clients) { clients.forEach { runCatching { it.close() } } }
        }
    }

    private class HttpStub(private val status: String) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                    thread(isDaemon = true) { answer(client) }
                }
            }
        }

        private fun answer(client: Socket) = client.use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            while (reader.readLine()?.isNotEmpty() == true) Unit
            val response = "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(response.toByteArray())
        }

        override fun close() = server.close()
    }

    private class SocksStub : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val port: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        return@thread
                    }
                    thread(isDaemon = true) { runCatching { relay(client) } }
                }
            }
        }

        private fun relay(client: Socket) = client.use {
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()
            input.readByte()
            repeat(input.readUnsignedByte()) { input.readByte() }
            output.write(byteArrayOf(5, 0))
            repeat(3) { input.readByte() }
            val host = when (input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also(input::readFully)).hostAddress
                3 -> String(ByteArray(input.readUnsignedByte()).also(input::readFully))
                else -> return@use
            }
            val targetPort = input.readUnsignedShort()
            Socket(host, targetPort).use { target ->
                output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                thread(isDaemon = true) {
                    runCatching { client.getInputStream().copyTo(target.getOutputStream()) }
                }
                runCatching { target.getInputStream().copyTo(output) }
            }
        }

        override fun close() = server.close()
    }
}
