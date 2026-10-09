package com.v2ray.ang.core

import android.util.Log
import com.google.gson.JsonParser
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherScanMode
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class AetherCoreManagerTest {

    private fun profile(
        protocol: AetherProtocol = AetherProtocol.MASQUE,
        transport: AetherTransport = AetherTransport.HTTP3,
        server: String? = null,
        port: String? = null,
        outer: String? = null,
        inner: String? = null,
        fragment: Boolean? = null,
    ) = ProfileItem(
        configType = EConfigType.AETHER,
        remarks = "test",
        server = server,
        serverPort = port,
        aetherProtocol = protocol.type,
        aetherTransport = transport.type,
        aetherScanMode = AetherScanMode.VERIFIED.type,
        aetherObfuscation = AetherObfuscation.AGGRESSIVE.type,
        aetherIpVersion = AetherIpVersion.DUAL.type,
        aetherWiwOuter = outer,
        aetherWiwInner = inner,
        aetherFragment = fragment,
    )

    private fun valueAfter(arguments: List<String>, flag: String): String? =
        arguments.indexOf(flag).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }

    @Test
    fun bindsToTheLoopbackPortItIsGiven() {
        assertEquals("127.0.0.1:10819", valueAfter(AetherCoreManager.buildArguments(profile(), 10819), "--bind"))
        assertEquals("127.0.0.1:0", valueAfter(AetherCoreManager.buildArguments(profile(), 0, scan = true), "--bind"))
    }

    @Test
    fun theChosenModesAreForwarded() {
        val arguments = AetherCoreManager.buildArguments(profile(), 10819)
        assertEquals("masque", valueAfter(arguments, "--protocol"))
        assertEquals("verified", valueAfter(arguments, "--scan"))
        assertEquals("aggressive", valueAfter(arguments, "--noize"))
        assertEquals("both", valueAfter(arguments, "--ip"))
        assertEquals("info", valueAfter(arguments, "--log-level"))
    }

    @Test
    fun eachProtocolIsNamedToTheCore() {
        assertEquals("wg", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.WIREGUARD), 10819), "--protocol"))
        assertEquals("gool", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL), 10819), "--protocol"))
        assertEquals("mim", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.MIM), 10819), "--protocol"))
    }

    @Test
    fun http2AndFragmentationOnlyApplyToTunnelsOverMasque() {
        assertFalse(AetherCoreManager.buildArguments(profile(fragment = true), 10819).contains("--h2"))
        assertFalse(AetherCoreManager.buildArguments(profile(fragment = true), 10819).contains("--fragment"))

        val http2 = AetherCoreManager.buildArguments(profile(transport = AetherTransport.HTTP2), 10819)
        assertTrue(http2.contains("--h2"))
        assertFalse(http2.contains("--fragment"))

        val fragmented = AetherCoreManager.buildArguments(profile(transport = AetherTransport.HTTP2, fragment = true), 10819)
        assertTrue(fragmented.contains("--fragment"))

        val wireguard = AetherCoreManager.buildArguments(
            profile(AetherProtocol.WIREGUARD, AetherTransport.HTTP2, fragment = true),
            10819
        )
        assertFalse(wireguard.contains("--h2"))
        assertFalse(wireguard.contains("--fragment"))

        // Both masque-in-masque hops ride on the carrier chosen here.
        val mim = AetherCoreManager.buildArguments(profile(AetherProtocol.MIM, AetherTransport.HTTP2, fragment = true), 10819)
        assertTrue(mim.contains("--h2"))
        assertTrue(mim.contains("--fragment"))
        assertFalse(AetherCoreManager.buildArguments(profile(AetherProtocol.MIM, fragment = true), 10819).contains("--h2"))
    }

    @Test
    fun fragmentValuesReachTheCoreOnlyWhenFragmentingIsOn() {
        val tuned = profile(transport = AetherTransport.HTTP2, fragment = true).apply {
            aetherFragmentSize = "32-16"
            aetherFragmentDelay = "5"
        }
        val arguments = AetherCoreManager.buildArguments(tuned, 10819)
        assertEquals("16-32", valueAfter(arguments, "--fragment-size"))
        assertEquals("5", valueAfter(arguments, "--fragment-delay"))

        val off = AetherCoreManager.buildArguments(tuned.copy(aetherFragment = false), 10819)
        assertFalse(off.contains("--fragment-size"))
        assertFalse(off.contains("--fragment-delay"))

        val invalid = AetherCoreManager.buildArguments(tuned.copy(aetherFragmentSize = "0", aetherFragmentDelay = "x"), 10819)
        assertTrue(invalid.contains("--fragment"))
        assertFalse(invalid.contains("--fragment-size"))
        assertFalse(invalid.contains("--fragment-delay"))
    }

    @Test
    fun aPinnedEndpointIsForwardedInTheCoreFormat() {
        assertEquals(
            "162.159.198.1:443",
            valueAfter(AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "443"), 10819), "--peer")
        )
        assertEquals(
            "[2606:4700:d0::a29f:c001]:2408",
            valueAfter(
                AetherCoreManager.buildArguments(
                    profile(AetherProtocol.WIREGUARD, server = "2606:4700:d0::a29f:c001", port = "2408"),
                    10819
                ),
                "--peer"
            )
        )
    }

    @Test
    fun anEndpointTheCoreCannotReadIsLeftToTheScan() {
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(), 10819), "--peer"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(server = "162.159.198.1"), 10819), "--peer"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(port = "443"), 10819), "--peer"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "0"), 10819), "--peer"))
        assertNull(
            valueAfter(AetherCoreManager.buildArguments(profile(server = "engage.cloudflareclient.com", port = "2408"), 10819), "--peer")
        )
    }

    @Test
    fun bothGoolHopsReachTheCoreWhenNamedByHand() {
        val arguments = AetherCoreManager.buildArguments(
            profile(AetherProtocol.GOOL, outer = "162.159.192.1:2408", inner = "188.114.96.1:894"),
            10819
        )
        assertEquals("162.159.192.1:2408", valueAfter(arguments, "--wiw-outer"))
        assertEquals("188.114.96.1:894", valueAfter(arguments, "--wiw-inner"))
        assertFalse(arguments.contains("--wiw-scan"))
        assertFalse(arguments.contains("--peer"))
    }

    @Test
    fun namingOneGoolHopLeavesTheOtherToTheScan() {
        val arguments = AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL, inner = "188.114.96.1:894"), 10819)
        assertNull(valueAfter(arguments, "--wiw-outer"))
        assertEquals("188.114.96.1:894", valueAfter(arguments, "--wiw-inner"))
        assertFalse(arguments.contains("--wiw-scan"))
    }

    @Test
    fun goolScansForHopsItCannotUse() {
        assertTrue(AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL), 10819).contains("--wiw-scan"))

        val malformed = AetherCoreManager.buildArguments(profile(AetherProtocol.GOOL, outer = "162.159.192.1"), 10819)
        assertNull(valueAfter(malformed, "--wiw-outer"))
        assertTrue(malformed.contains("--wiw-scan"))

        val pinnedEndpoint = AetherCoreManager.buildArguments(
            profile(AetherProtocol.GOOL, server = "162.159.198.1", port = "443"),
            10819
        )
        assertFalse(pinnedEndpoint.contains("--peer"))
        assertTrue(pinnedEndpoint.contains("--wiw-scan"))
    }

    @Test
    fun aRunReusesTheLastGatewayButAScanLooksAfresh() {
        val run = AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "443"), 10819)
        assertTrue(run.contains("--quick-reconnect"))
        assertFalse(run.contains("--no-quick-reconnect"))

        val scan = AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "443"), 0, scan = true)
        assertTrue(scan.contains("--no-quick-reconnect"))
        assertFalse(scan.contains("--quick-reconnect"))
        assertFalse(scan.contains("--peer"))
    }

    @Test
    fun mimHopsReachTheCoreUnderTheirOwnFlags() {
        val both = AetherCoreManager.buildArguments(
            profile(AetherProtocol.MIM, outer = "162.159.192.1:443", inner = "188.114.96.1:443"),
            10819
        )
        assertEquals("162.159.192.1:443", valueAfter(both, "--mim-outer"))
        assertEquals("188.114.96.1:443", valueAfter(both, "--mim-inner"))
        assertFalse(both.contains("--mim-scan"))
        assertFalse(both.contains("--peer"))
        assertFalse(both.contains("--wiw-outer"))

        val innerOnly = AetherCoreManager.buildArguments(profile(AetherProtocol.MIM, inner = "188.114.96.1:443"), 10819)
        assertNull(valueAfter(innerOnly, "--mim-outer"))
        assertEquals("188.114.96.1:443", valueAfter(innerOnly, "--mim-inner"))
        assertFalse(innerOnly.contains("--mim-scan"))
    }

    @Test
    fun mimScansForHopsItCannotUse() {
        assertTrue(AetherCoreManager.buildArguments(profile(AetherProtocol.MIM), 10819).contains("--mim-scan"))

        val malformed = AetherCoreManager.buildArguments(profile(AetherProtocol.MIM, outer = "162.159.192.1"), 10819)
        assertNull(valueAfter(malformed, "--mim-outer"))
        assertTrue(malformed.contains("--mim-scan"))

        val pinnedEndpoint = AetherCoreManager.buildArguments(
            profile(AetherProtocol.MIM, server = "162.159.198.1", port = "443"),
            10819
        )
        assertFalse(pinnedEndpoint.contains("--peer"))
        assertTrue(pinnedEndpoint.contains("--mim-scan"))

        val scan = AetherCoreManager.buildArguments(
            profile(AetherProtocol.MIM, outer = "162.159.192.1:443", inner = "188.114.96.1:443"),
            0,
            scan = true
        )
        assertFalse(scan.contains("--mim-outer"))
        assertFalse(scan.contains("--mim-inner"))
        assertTrue(scan.contains("--mim-scan"))
    }

    @Test
    fun aGoolScanIgnoresTheHopsItWasGiven() {
        val scan = AetherCoreManager.buildArguments(
            profile(AetherProtocol.GOOL, outer = "162.159.192.1:2408", inner = "188.114.96.1:894"),
            0,
            scan = true
        )
        assertFalse(scan.contains("--wiw-outer"))
        assertFalse(scan.contains("--wiw-inner"))
        assertTrue(scan.contains("--wiw-scan"))
    }

    @Test
    fun coreOutputKeepsItsLogLevel() {
        assertEquals(Log.ERROR, AetherCoreManager.outputPriority("[2026-09-11T10:00:00.000Z ERROR aether] config parse failed"))
        assertEquals(Log.WARN, AetherCoreManager.outputPriority("[2026-09-11T10:00:00.000Z WARN  aether] [-] tunnel ended"))
        assertEquals(Log.INFO, AetherCoreManager.outputPriority("[2026-09-11T10:00:00.000Z INFO  aether] [+] identity ready"))
        assertEquals(Log.DEBUG, AetherCoreManager.outputPriority("[2026-09-11T10:00:00.000Z DEBUG aether::quic] packet"))
        assertEquals(Log.DEBUG, AetherCoreManager.outputPriority("[2026-09-11T10:00:00.000Z TRACE aether] packet"))
    }

    @Test
    fun theTestWaitsUntilTheCoreStartsListening() = runBlocking {
        var polls = 0
        assertTrue(AetherCoreManager.awaitReady(5_000, 10, { true }, { ++polls >= 3 }))
        assertEquals(3, polls)
    }

    @Test
    fun theTestGivesUpWhenTheCoreStopsOrTakesTooLong() = runBlocking {
        assertFalse(AetherCoreManager.awaitReady(5_000, 10, { false }, { true }))

        var polls = 0
        assertFalse(AetherCoreManager.awaitReady(5_000, 10, { polls < 2 }, { polls++; false }))

        assertFalse(AetherCoreManager.awaitReady(100, 10, { true }, { false }))
    }

    @Test
    fun theWarmUpReportsAListenerACoreExitOrNothingAtAll() {
        assertEquals(
            AetherCoreManager.WarmUpOutcome.LISTENING,
            AetherCoreManager.warmUpOutcome(listening = true, active = true, serviceRunning = true)
        )
        assertEquals(
            AetherCoreManager.WarmUpOutcome.CORE_EXITED,
            AetherCoreManager.warmUpOutcome(listening = false, active = true, serviceRunning = true)
        )
        assertEquals(
            AetherCoreManager.WarmUpOutcome.ABANDONED,
            AetherCoreManager.warmUpOutcome(listening = false, active = false, serviceRunning = true)
        )
        assertEquals(
            AetherCoreManager.WarmUpOutcome.ABANDONED,
            AetherCoreManager.warmUpOutcome(listening = true, active = true, serviceRunning = false)
        )
        assertEquals(
            AetherCoreManager.WarmUpOutcome.ABANDONED,
            AetherCoreManager.warmUpOutcome(listening = true, active = false, serviceRunning = true)
        )
    }

    @Test
    fun aFatalErrorFromTheCoreIsAnError() {
        assertEquals(Log.ERROR, AetherCoreManager.outputPriority("Error: Api(\"too many registrations\")"))
    }

    @Test
    fun theLogHeaderIsStrippedFromCoreOutput() {
        assertEquals(
            "[+] candidate ok 162.159.197.3:443 rtt=84ms",
            AetherCoreManager.outputMessage("[2026-09-11T10:00:00.000Z INFO  aether::prober] [+] candidate ok 162.159.197.3:443 rtt=84ms")
        )
        assertEquals("[+] selected protocol: MASQUE", AetherCoreManager.outputMessage("[+] selected protocol: MASQUE"))
        assertEquals("fallback over tcp 443", AetherCoreManager.outputMessage("  fallback over tcp 443  "))
        assertEquals("", AetherCoreManager.outputMessage("   "))
    }

    @Test
    fun outputWithoutALogHeaderIsInformational() {
        assertEquals(Log.INFO, AetherCoreManager.outputPriority("  fallback over tcp 443 (--masque-http2) on this network."))
        assertEquals(Log.INFO, AetherCoreManager.outputPriority("[unterminated header"))
        assertEquals(Log.INFO, AetherCoreManager.outputPriority("[]"))
    }

    @Test
    fun theSessionLogLevelFollowsTheAppSetting() {
        assertEquals("debug", AetherCoreManager.coreLogLevel("debug"))
        assertEquals("info", AetherCoreManager.coreLogLevel("info"))
        assertEquals("warn", AetherCoreManager.coreLogLevel("warning"))
        assertEquals("warn", AetherCoreManager.coreLogLevel("Warn"))
        assertEquals("error", AetherCoreManager.coreLogLevel("error"))
        assertEquals("error", AetherCoreManager.coreLogLevel("none"))
        assertEquals("info", AetherCoreManager.coreLogLevel(null))
        assertEquals("info", AetherCoreManager.coreLogLevel("verbose"))

        assertEquals("warn", valueAfter(AetherCoreManager.buildArguments(profile(), 10819, logLevel = "warn"), "--log-level"))
        assertEquals("info", valueAfter(AetherCoreManager.buildArguments(profile(), 0, scan = true), "--log-level"))
    }

    @Test
    fun aLeftoverCoreIsRecognisedByItsPortOrItsDeadOwner() {
        val session = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:10819", "--protocol", "masque")
        val scan = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:0", "--protocol", "masque")

        assertEquals("127.0.0.1:10819", AetherCoreManager.bindAddressOf(session))
        assertNull(AetherCoreManager.bindAddressOf(listOf("/data/app/lib/libaether.so", "--bind")))
        assertNull(AetherCoreManager.bindAddressOf(emptyList()))

        assertTrue(AetherCoreManager.isStale(session, ownerAlive = true, bindAddress = "127.0.0.1:10819"))
        assertTrue(AetherCoreManager.isStale(session, ownerAlive = null, bindAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isStale(scan, ownerAlive = true, bindAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isStale(scan, ownerAlive = null, bindAddress = "127.0.0.1:10819"))
        assertTrue(AetherCoreManager.isStale(scan, ownerAlive = false, bindAddress = "127.0.0.1:10819"))
        assertTrue(AetherCoreManager.isStale(scan, ownerAlive = false, bindAddress = null))
        assertFalse(AetherCoreManager.isStale(session, ownerAlive = true, bindAddress = null))
        assertFalse(AetherCoreManager.isStale(session, ownerAlive = null, bindAddress = null))
    }

    @Test
    fun theSessionIsRecognisedWhileItScansAndWhileItListens() {
        val session = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:10819", "--protocol", "masque")
        val scan = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:0", "--protocol", "masque")

        assertTrue(AetherCoreManager.isSession(session, ownerAlive = true, sessionMarked = true, sessionAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isSession(session, ownerAlive = false, sessionMarked = true, sessionAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isSession(scan, ownerAlive = true, sessionMarked = false, sessionAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isSession(emptyList(), ownerAlive = true, sessionMarked = false, sessionAddress = "127.0.0.1:10819"))
    }

    @Test
    fun theSessionOfACustomConfigurationIsRecognisedOnWhateverPortItListens() {
        val session = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:20808", "--protocol", "wg")
        val test = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:41234", "--protocol", "wg")

        // The mark tells them apart, not the port: a test core listens on a port of its own as well.
        assertTrue(AetherCoreManager.isSession(session, ownerAlive = true, sessionMarked = true, sessionAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isSession(test, ownerAlive = true, sessionMarked = false, sessionAddress = "127.0.0.1:10819"))
        assertEquals(20808, AetherCoreManager.bindPortOf(session))
        assertNull(AetherCoreManager.bindPortOf(listOf("/data/app/lib/libaether.so", "--bind")))
        assertNull(AetherCoreManager.bindPortOf(listOf("/data/app/lib/libaether.so", "--bind", "20808")))
    }

    @Test
    fun withoutAReadableEnvironmentTheSessionIsToldByTheAddressEveryProfileUses() {
        val session = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:10819", "--protocol", "masque")
        val scan = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:0", "--protocol", "masque")

        assertTrue(AetherCoreManager.isSession(session, ownerAlive = null, sessionMarked = null, sessionAddress = "127.0.0.1:10819"))
        assertFalse(AetherCoreManager.isSession(scan, ownerAlive = null, sessionMarked = null, sessionAddress = "127.0.0.1:10819"))
    }

    @Test
    fun theSessionMarkIsReadFromTheEnvironmentTheAppGaveTheCore() {
        assertTrue(AetherCoreManager.isSessionMarked(listOf("HOME=/x", "${AetherCoreManager.OWNER_ENV}=4242", "${AetherCoreManager.SESSION_ENV}=1")))
        assertFalse(AetherCoreManager.isSessionMarked(listOf("HOME=/x", "${AetherCoreManager.OWNER_ENV}=4242")))
        assertFalse(AetherCoreManager.isSessionMarked(emptyList()))
    }

    @Test
    fun theSessionProtocolIsReadFromItsArguments() {
        val gool = listOf("/data/app/lib/libaether.so", "--bind", "127.0.0.1:10819", "--protocol", "gool", "--scan", "balanced")
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(gool))
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf("/data/app/lib/libaether.so", "--protocol", "wg")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf("/data/app/lib/libaether.so", "--protocol")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(emptyList()))
    }

    @Test
    fun theProtocolOfAHandWrittenCommandIsReadTheWayTheCoreReadsIt() {
        val bin = "/data/app/lib/libaether.so"
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf(bin, "--wg")))
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf(bin, "--warp")))
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--wiw")))
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--mim")))
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--masque-in-masque")))
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--protocol", "mim")))
        // After --protocol the core takes other names as well, in any case; an unknown one is masque.
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--protocol", "M2")))
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--protocol", "warp-in-warp")))
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf(bin, "--protocol", "WireGuard")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--wg", "--protocol", "something-else")))
        // The last word wins, as it does for the core.
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--wg", "--protocol", "gool")))
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf(bin, "--protocol", "gool", "--wg")))
        // A warp-in-warp hop named without a protocol selects gool; asking for a scan of the hops does not.
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--wiw-outer", "162.159.192.1:2408")))
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--wiw-peers", "162.159.192.1:2408,188.114.96.1:2408")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--wiw-peers", "auto")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--wiw-scan")))
        assertEquals(AetherProtocol.WIREGUARD, AetherCoreManager.protocolOf(listOf(bin, "--wg", "--wiw-outer", "162.159.192.1:2408")))
        // A masque-in-masque hop selects mim the same way, and a warp-in-warp hop comes first when both are named.
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--mim-outer", "162.159.192.1:443")))
        assertEquals(AetherProtocol.MIM, AetherCoreManager.protocolOf(listOf(bin, "--mim-peers", "162.159.192.1:443,188.114.96.1:443")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--mim-peers", "auto")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--mim-scan")))
        assertEquals(AetherProtocol.GOOL, AetherCoreManager.protocolOf(listOf(bin, "--mim-outer", "162.159.192.1:443", "--wiw-inner", "188.114.96.1:894")))
        assertEquals(AetherProtocol.MASQUE, AetherCoreManager.protocolOf(listOf(bin, "--masque", "--mim-outer", "162.159.192.1:443")))
    }

    @Test
    fun theSessionTakesTheAppLogLevelUnlessItsCommandNamesOne() {
        assertEquals(listOf("--wg", "--log-level", "warn"), AetherCoreManager.withLogLevel(listOf("--wg"), "warn"))
        assertEquals(listOf("--wg", "--log-level", "debug"), AetherCoreManager.withLogLevel(listOf("--wg", "--log-level", "debug"), "warn"))
        assertEquals(listOf("--wg", "--verbose"), AetherCoreManager.withLogLevel(listOf("--wg", "--verbose"), "warn"))
    }

    @Test
    fun theListenerIsReplacedWhereverItStands() {
        assertEquals(listOf("--wg", "--bind", "127.0.0.1:41234"), AetherCoreManager.withListener(listOf("--bind", "127.0.0.1:10819", "--wg"), "--bind", 41234))
        assertEquals(listOf("--wg", "--bind", "127.0.0.1:41234"), AetherCoreManager.withListener(listOf("--wg"), "--bind", 41234))
        assertEquals(
            listOf("--wg", "--bind", "127.0.0.1:41234"),
            AetherCoreManager.withListener(listOf("--wg", "--bind", "127.0.0.1:1", "--bind", "127.0.0.1:2"), "--bind", 41234)
        )
        assertEquals(41234, AetherCoreManager.bindPortOf(AetherCoreManager.withListener(listOf("--bind", "127.0.0.1:10819", "--wg"), "--bind", 41234)))
    }

    @Test
    fun psiphonInsideTheTunnelIsWhatTheAppDialsAndTheTunnelTakesThePortAfterIt() {
        val chain = AetherCoreManager.buildArguments(
            profile().copy(aetherPsiphon = "chain", aetherPsiphonMode = "cdn", aetherPsiphonCdnIps = "1.1.1.1,1.0.0.1", aetherPsiphonRegion = "DE"),
            10819,
        )
        assertEquals("127.0.0.1:10820", valueAfter(chain, "--bind"))
        assertTrue("--psiphon" in chain)
        assertEquals("127.0.0.1:10819", valueAfter(chain, "--psiphon-bind"))
        assertEquals("cdn", valueAfter(chain, "--psiphon-mode"))
        assertEquals("1.1.1.1,1.0.0.1", valueAfter(chain, "--psiphon-cdn-ips"))
        assertEquals("DE", valueAfter(chain, "--psiphon-region"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(chain))
        assertEquals("127.0.0.1:10819", AetherCoreManager.listenerAddressOf(chain))
        // WARP is still set up underneath.
        assertEquals("masque", valueAfter(chain, "--protocol"))
        assertTrue("--quick-reconnect" in chain)
    }

    @Test
    fun theCdnListsReachTheCoreOnlyWhereAFrontedTransportCanReadThem() {
        val fronted = profile().copy(aetherPsiphon = "only", aetherPsiphonCdnIps = "1.1.1.1", aetherPsiphonCdnSni = "a.example")
        assertEquals("1.1.1.1", valueAfter(AetherCoreManager.buildArguments(fronted, 10819), "--psiphon-cdn-ips"))
        assertEquals("a.example", valueAfter(AetherCoreManager.buildArguments(fronted, 10819), "--psiphon-cdn-sni"))
        assertEquals("1.1.1.1", valueAfter(AetherCoreManager.buildArguments(fronted.copy(aetherPsiphonMode = "cdn"), 10819), "--psiphon-cdn-ips"))

        // The direct shape never fronts, so the lists would only be carried for nothing.
        val direct = AetherCoreManager.buildArguments(fronted.copy(aetherPsiphonMode = "direct"), 10819)
        assertEquals("direct", valueAfter(direct, "--psiphon-mode"))
        assertNull(valueAfter(direct, "--psiphon-cdn-ips"))
        assertNull(valueAfter(direct, "--psiphon-cdn-sni"))

        // Without an IP list of one's own the built-in list comes whole, names included.
        val namesAlone = AetherCoreManager.buildArguments(fronted.copy(aetherPsiphonCdnIps = null), 10819)
        assertNull(valueAfter(namesAlone, "--psiphon-cdn-ips"))
        assertNull(valueAfter(namesAlone, "--psiphon-cdn-sni"))
    }

    @Test
    fun psiphonAroundTheTunnelKeepsTheTunnelOnTheListenPortAndLetsPsiphonPickItsOwn() {
        val reverse = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "reverse"), 10819)
        assertEquals("127.0.0.1:10819", valueAfter(reverse, "--bind"))
        assertTrue("--psiphon-reverse" in reverse)
        assertEquals("127.0.0.1:0", valueAfter(reverse, "--psiphon-bind"))
        assertEquals("auto", valueAfter(reverse, "--psiphon-mode"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(reverse))
    }

    @Test
    fun psiphonAloneLeavesWarpOut() {
        val only = AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "443").copy(aetherPsiphon = "only"), 10819)
        assertEquals("127.0.0.1:10819", valueAfter(only, "--bind"))
        assertTrue("--psiphon-only" in only)
        assertFalse("--protocol" in only)
        assertFalse("--peer" in only)
        assertFalse("--quick-reconnect" in only)
        assertEquals("info", valueAfter(only, "--log-level"))
    }

    @Test
    fun torInsideTheTunnelIsWhatTheAppDialsAndTheTunnelTakesThePortAfterIt() {
        val chain = AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain"), 10819)
        assertTrue("--tor" in chain)
        assertEquals("127.0.0.1:10819", valueAfter(chain, "--tor-bind"))
        assertEquals("127.0.0.1:10820", valueAfter(chain, "--bind"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(chain))
        assertEquals("127.0.0.1:10819", AetherCoreManager.listenerAddressOf(chain))
        // WARP is still set up underneath, and without a word about bridges Tor tries plainly first.
        assertEquals("masque", valueAfter(chain, "--protocol"))
        assertTrue("--quick-reconnect" in chain)
        assertFalse("--tor-bridges" in chain)
        assertFalse("--no-tor-bridges" in chain)
        assertFalse("--tor-bridge" in chain)
    }

    @Test
    fun torAroundTheTunnelKeepsTheTunnelOnTheListenPortAndGivesTorThePortAfterIt() {
        val reverse = AetherCoreManager.buildArguments(profile().copy(aetherTor = "reverse"), 10819)
        assertTrue("--tor-reverse" in reverse)
        assertEquals("127.0.0.1:10819", valueAfter(reverse, "--bind"))
        // Unlike Psiphon's, Tor's listener gets a real port: the core dials the address Tor was told to listen on.
        assertEquals("127.0.0.1:10820", valueAfter(reverse, "--tor-bind"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(reverse))
    }

    @Test
    fun torAloneLeavesWarpOut() {
        val only = AetherCoreManager.buildArguments(profile(server = "162.159.198.1", port = "443").copy(aetherTor = "only"), 10819)
        assertTrue("--tor-only" in only)
        assertEquals("127.0.0.1:10819", valueAfter(only, "--bind"))
        assertNull(valueAfter(only, "--tor-bind"))
        assertNull(valueAfter(only, "--protocol"))
        assertNull(valueAfter(only, "--peer"))
        assertFalse("--quick-reconnect" in only)
        assertEquals(10819, AetherCoreManager.listenerPortOf(only))
    }

    @Test
    fun theBridgeSettingReachesTheCoreAsItsFlags() {
        assertTrue("--tor-bridges" in AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain", aetherTorBridges = "first"), 10819))
        assertTrue("--no-tor-bridges" in AetherCoreManager.buildArguments(profile().copy(aetherTor = "only", aetherTorBridges = "never"), 10819))

        val lines = "obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0\n\n# mine\nBridge webtunnel [2001:db8::1]:443 7DD627 url=https://example.com/x ver=0.0.1\n"
        val own = AetherCoreManager.buildArguments(profile().copy(aetherTor = "only", aetherTorBridges = "own", aetherTorBridgeLines = lines), 10819)
        assertEquals(
            listOf(
                "obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0",
                "webtunnel [2001:db8::1]:443 7DD627 url=https://example.com/x ver=0.0.1",
            ),
            valuesAfter(own, "--tor-bridge")
        )
        assertFalse("--tor-bridges" in own)
        // With Tor off, the bridge setting says nothing.
        assertFalse("--tor-bridges" in AetherCoreManager.buildArguments(profile().copy(aetherTorBridges = "first"), 10819))
    }

    @Test
    fun torAndPsiphonNestWithTheInnerOneOnTheListenPort() {
        // Psiphon inside, Tor around: the app dials Psiphon, the tunnel takes the next port, Tor the one after.
        val psiphonInside = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain", aetherTor = "reverse"), 10819)
        assertEquals("127.0.0.1:10819", valueAfter(psiphonInside, "--psiphon-bind"))
        assertEquals("127.0.0.1:10820", valueAfter(psiphonInside, "--bind"))
        assertEquals("127.0.0.1:10821", valueAfter(psiphonInside, "--tor-bind"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(psiphonInside))
        assertTrue("--psiphon" in psiphonInside)
        assertTrue("--tor-reverse" in psiphonInside)

        // Tor inside, Psiphon around: the app dials Tor, and Psiphon picks its own port as the core reads it.
        val torInside = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "reverse", aetherTor = "chain"), 10819)
        assertEquals("127.0.0.1:10819", valueAfter(torInside, "--tor-bind"))
        assertEquals("127.0.0.1:10820", valueAfter(torInside, "--bind"))
        assertEquals("127.0.0.1:0", valueAfter(torInside, "--psiphon-bind"))
        assertEquals(10819, AetherCoreManager.listenerPortOf(torInside))
    }

    @Test
    fun aScanLooksForWarpEndpointsWithoutTor() {
        val scan = AetherCoreManager.buildArguments(profile().copy(aetherTor = "only", aetherTorBridges = "first"), 0, scan = true)
        assertFalse("--tor-only" in scan)
        assertFalse("--tor-bridges" in scan)
        assertNull(valueAfter(scan, "--tor-bind"))
        assertEquals("masque", valueAfter(scan, "--protocol"))
    }

    @Test
    fun theTunnelIsToldApartWithoutItsListenersOrLogLevel() {
        assertEquals(
            listOf("--tor", "--wg"),
            AetherCoreManager.tunnelArguments(listOf("--tor", "--tor-bind", "127.0.0.1:1820", "--wg", "--bind", "127.0.0.1:1819", "--log-level", "info"))
        )
    }

    @Test
    fun aListenerCountsOnceItAnswersTheSocksGreeting() {
        // Bound but not served yet, as Tor's listener is before Tor has bootstrapped: the connection is taken, nothing is said.
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { silent ->
            assertFalse(AetherCoreManager.answersSocks(silent.localPort))
        }
        val closedPort = ServerSocket(0).use { it.localPort }
        assertFalse(AetherCoreManager.answersSocks(closedPort))
        SocksGreeter().use { assertTrue(AetherCoreManager.answersSocks(it.port)) }
    }

    private fun valuesAfter(arguments: List<String>, flag: String): List<String> =
        arguments.indices.filter { arguments[it] == flag }.mapNotNull { arguments.getOrNull(it + 1) }

    /** Answers the SOCKS5 greeting and nothing more, as a served listener does. */
    private class SocksGreeter : AutoCloseable {
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
                    thread(isDaemon = true) {
                        runCatching {
                            client.use {
                                val input = DataInputStream(it.getInputStream())
                                input.readByte()
                                repeat(input.readUnsignedByte()) { input.readByte() }
                                it.getOutputStream().write(byteArrayOf(5, 0))
                            }
                        }
                    }
                }
            }
        }

        override fun close() = server.close()
    }

    @Test
    fun masqueOverHttp2TakesNoObfuscation() {
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(transport = AetherTransport.HTTP2), 10819), "--noize"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.MIM, AetherTransport.HTTP2), 10819), "--noize"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(transport = AetherTransport.HTTP2), 0, scan = true), "--noize"))
        // Tor or Psiphon around the tunnel carry TCP alone, so the core takes HTTP/2 whatever the transport says.
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "reverse"), 10819), "--noize"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "reverse"), 10819), "--noize"))
        // Over HTTP/3, with a carrier inside the tunnel, and on WireGuard whatever the transport says, it stays.
        assertEquals("aggressive", valueAfter(AetherCoreManager.buildArguments(profile(), 10819), "--noize"))
        assertEquals("aggressive", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain"), 10819), "--noize"))
        assertEquals("aggressive", valueAfter(AetherCoreManager.buildArguments(profile(AetherProtocol.WIREGUARD, AetherTransport.HTTP2), 10819), "--noize"))

        assertTrue(AetherCoreManager.masqueOverHttp2(AetherProtocol.MIM, AetherTransport.HTTP2, AetherTor.OFF, AetherPsiphon.OFF))
        assertTrue(AetherCoreManager.masqueOverHttp2(AetherProtocol.MASQUE, AetherTransport.HTTP3, AetherTor.REVERSE, AetherPsiphon.OFF))
        assertFalse(AetherCoreManager.masqueOverHttp2(AetherProtocol.MASQUE, AetherTransport.HTTP3, AetherTor.CHAIN, AetherPsiphon.CHAIN))
        assertFalse(AetherCoreManager.masqueOverHttp2(AetherProtocol.GOOL, AetherTransport.HTTP2, AetherTor.OFF, AetherPsiphon.OFF))
    }

    @Test
    fun theFingerprintShapesEveryMasqueTunnelAndNoOther() {
        for (protocol in listOf(AetherProtocol.MASQUE, AetherProtocol.MIM)) {
            for (transport in AetherTransport.entries) {
                // Chrome's rule is named, whatever the core's default; BoringSSL orders it by the phone's AES
                // instructions as it does the TLS 1.3 suites. Chrome sends GREASE.
                val chrome = AetherCoreManager.buildArguments(profile(protocol, transport), 10819)
                assertEquals("ALL:!aPSK:!ECDSA+SHA1:!3DES", valueAfter(chrome, "--tls-ciphers"))
                assertFalse("--disable-grease" in chrome)
                for (fingerprint in listOf(AetherFingerprint.FIREFOX, AetherFingerprint.SEMI_PYTHON, AetherFingerprint.GO)) {
                    val arguments = AetherCoreManager.buildArguments(profile(protocol, transport).copy(aetherFingerprint = fingerprint.type), 10819)
                    assertEquals(fingerprint.ciphers, valueAfter(arguments, "--tls-ciphers"))
                    assertTrue("--disable-grease" in arguments)
                }
            }
        }
        // A profile from before the setting is Chrome's.
        val older = AetherCoreManager.buildArguments(profile().copy(aetherFingerprint = null), 10819)
        assertEquals(AetherCoreManager.buildArguments(profile().copy(aetherFingerprint = "chrome"), 10819), older)
        assertEquals(AetherFingerprint.CHROME.ciphers, valueAfter(older, "--tls-ciphers"))
        // WireGuard has no TLS handshake of its own to shape.
        for (protocol in listOf(AetherProtocol.WIREGUARD, AetherProtocol.GOOL)) {
            val arguments = AetherCoreManager.buildArguments(profile(protocol, AetherTransport.HTTP2).copy(aetherFingerprint = "go"), 10819)
            assertFalse("--tls-ciphers" in arguments)
            assertFalse("--disable-grease" in arguments)
        }
        // A scan's probes send the ClientHello the tunnel will.
        assertTrue("--disable-grease" in AetherCoreManager.buildArguments(profile().copy(aetherFingerprint = "firefox"), 0, scan = true))
    }

    @Test
    fun obfuscationIsTheCoresOwnChoiceUnlessAProfileNamesIt() {
        // The core takes firewall for MASQUE and balanced for WireGuard and gool; automatic says nothing.
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherObfuscation = "auto"), 10819), "--noize"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherObfuscation = null), 10819), "--noize"))
        assertEquals("firewall", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherObfuscation = "firewall"), 10819), "--noize"))
        assertEquals("gfw", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherObfuscation = "gfw"), 10819), "--noize"))
        assertEquals("off", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherObfuscation = "off"), 10819), "--noize"))
    }

    @Test
    fun aSettingThatReadsAsAnOptionNeverReachesTheCoreWhichStillDialsOutThroughXray() {
        val crafted = profile().copy(
            aetherDns = "--upstream",
            aetherExitLoc = " --upstream",
            aetherEch = true,
            aetherEchDns = "--upstream",
            aetherEchDomain = "--bind",
        )
        val arguments = AetherCoreManager.buildArguments(crafted, 10819)
        assertFalse("--upstream" in arguments, arguments.toString())
        assertNull(valueAfter(arguments, "--dns"))
        assertNull(valueAfter(arguments, "--exit-loc"))
        assertEquals("udp://1.1.1.1", valueAfter(arguments, "--ech-dns"))
        assertEquals("cloudflare-ech.com", valueAfter(arguments, "--ech-domain"))
        assertEquals("127.0.0.1:10819", valueAfter(arguments, "--bind"))
        // The core of the session is still told to dial out through Xray.
        val session = AetherCore.of(crafted, 10819).through(10822)
        assertEquals("socks5://127.0.0.1:10822", valueAfter(session.arguments, "--upstream"))

        val psiphon = profile().copy(
            aetherPsiphon = AetherPsiphon.CHAIN.type,
            aetherPsiphonMode = "cdn",
            aetherPsiphonCdnIps = "--upstream",
            aetherPsiphonCdnSni = "-x",
            aetherPsiphonRegion = "--upstream",
        )
        val carried = AetherCoreManager.buildArguments(psiphon, 10819)
        assertFalse("--upstream" in carried, carried.toString())
        assertNull(valueAfter(carried, "--psiphon-cdn-ips"))
        assertNull(valueAfter(carried, "--psiphon-cdn-sni"))
        assertNull(valueAfter(carried, "--psiphon-region"))

        val tor = profile().copy(
            aetherTor = AetherTor.CHAIN.type,
            aetherTorBridges = "own",
            aetherTorBridgeLines = "--upstream\nobfs4 192.0.2.1:443 FP cert=x iat-mode=0",
        )
        val bridged = AetherCoreManager.buildArguments(tor, 10819)
        assertFalse("--upstream" in bridged, bridged.toString())
        assertEquals(listOf("obfs4 192.0.2.1:443 FP cert=x iat-mode=0"), valuesAfter(bridged, "--tor-bridge"))
    }

    @Test
    fun encryptedClientHelloTheResolversAndTheExitRuleReachTheCore() {
        val tuned = profile().copy(aetherEch = true, aetherDns = "1.1.1.1,10.0.0.1:5353", aetherExitLoc = "!IR,RU")
        val arguments = AetherCoreManager.buildArguments(tuned, 10819)
        assertEquals("auto", valueAfter(arguments, "--ech"))
        assertEquals("1.1.1.1,10.0.0.1:5353", valueAfter(arguments, "--dns"))
        assertEquals("!IR,RU", valueAfter(arguments, "--exit-loc"))

        // The key is asked of the default resolver for the default domain, unless the profile names others.
        assertEquals("udp://1.1.1.1", valueAfter(arguments, "--ech-dns"))
        assertEquals("cloudflare-ech.com", valueAfter(arguments, "--ech-domain"))
        val named = AetherCoreManager.buildArguments(
            tuned.copy(aetherEchDns = " https://doq.dns4all.eu/dns-query ", aetherEchDomain = "ip.gs"),
            10819,
        )
        assertEquals("https://doq.dns4all.eu/dns-query", valueAfter(named, "--ech-dns"))
        assertEquals("ip.gs", valueAfter(named, "--ech-domain"))
        val blank = AetherCoreManager.buildArguments(tuned.copy(aetherEchDns = " ", aetherEchDomain = ""), 10819)
        assertEquals("udp://1.1.1.1", valueAfter(blank, "--ech-dns"))
        assertEquals("cloudflare-ech.com", valueAfter(blank, "--ech-domain"))
        val off = AetherCoreManager.buildArguments(profile().copy(aetherEchDns = "tcp://1.1.1.1", aetherEchDomain = "ip.gs"), 10819)
        assertNull(valueAfter(off, "--ech-dns"))
        assertNull(valueAfter(off, "--ech-domain"))

        // ECH belongs to the MASQUE handshake, on either carrier and both hops.
        assertEquals("auto", valueAfter(AetherCoreManager.buildArguments(tuned.copy(aetherProtocol = "mim"), 10819), "--ech"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(tuned.copy(aetherProtocol = "wg"), 10819), "--ech"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile(), 10819), "--ech"))

        // A scan runs under the same conditions, the exit rule included, so it ends on an endpoint the session will accept.
        val scan = AetherCoreManager.buildArguments(tuned, 0, scan = true)
        assertEquals("auto", valueAfter(scan, "--ech"))
        assertEquals("udp://1.1.1.1", valueAfter(scan, "--ech-dns"))
        assertEquals("cloudflare-ech.com", valueAfter(scan, "--ech-domain"))
        assertEquals("1.1.1.1,10.0.0.1:5353", valueAfter(scan, "--dns"))
        assertEquals("!IR,RU", valueAfter(scan, "--exit-loc"))

        // Without a WARP tunnel there is nothing for them to apply to.
        val alone = AetherCoreManager.buildArguments(tuned.copy(aetherPsiphon = "only"), 10819)
        assertNull(valueAfter(alone, "--dns"))
        assertNull(valueAfter(alone, "--exit-loc"))
        assertNull(valueAfter(alone, "--ech"))
    }

    @Test
    fun theBridgePoolReachesTheCoreOnlyWhereBridgesAreFetched() {
        assertEquals("only", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain", aetherTorRelays = "only"), 10819), "--tor-relays"))
        assertEquals(
            "off",
            valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "only", aetherTorBridges = "first", aetherTorRelays = "off"), 10819), "--tor-relays")
        )
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain", aetherTorRelays = "auto"), 10819), "--tor-relays"))
        // With the profile's own lines, or no bridges at all, nothing is fetched.
        assertNull(
            valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTor = "only", aetherTorBridges = "never", aetherTorRelays = "only"), 10819), "--tor-relays")
        )
        val own = profile().copy(aetherTor = "only", aetherTorBridges = "own", aetherTorBridgeLines = "obfs4 192.0.2.55:38114 316E64 cert=abc iat-mode=0", aetherTorRelays = "only")
        assertNull(valueAfter(AetherCoreManager.buildArguments(own, 10819), "--tor-relays"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherTorRelays = "only"), 10819), "--tor-relays"))
    }

    @Test
    fun theTunnelIsNamedFromTheOutsideIn() {
        fun path(vararg words: String) = AetherCoreManager.pathOf(words.toList())
        assertEquals(listOf("MASQUE"), path("--bind", "127.0.0.1:10819"))
        assertEquals(listOf("WIREGUARD", "PSIPHON"), path("--wg", "--psiphon"))
        assertEquals(listOf("PSIPHON", "MASQUE"), path("--psiphon-reverse"))
        assertEquals(listOf("MASQUE", "TOR"), path("--tor"))
        assertEquals(listOf("TOR", "MIM"), path("--mim", "--tor-reverse"))
        assertEquals(listOf("TOR", "GOOL", "PSIPHON"), path("--gool", "--psiphon", "--tor-reverse"))
        assertEquals(listOf("PSIPHON", "WIREGUARD", "TOR"), path("--wg", "--tor", "--psiphon-reverse"))
        // A carrier alone is the whole tunnel, whatever else is written; Tor alone comes first, as it does for the core.
        assertEquals(listOf("PSIPHON"), path("--wg", "--psiphon-only"))
        assertEquals(listOf("TOR"), path("--tor-only", "--psiphon"))
        assertEquals(listOf("TOR"), path("--tor-only", "--psiphon-only"))
        // The last mode flag wins, as it does for the core.
        assertEquals(listOf("TOR"), path("--tor", "--tor-only"))
        assertEquals(AetherTor.ONLY, AetherCoreManager.torModeOf(listOf("--tor", "--tor-only")))
        assertEquals(AetherPsiphon.CHAIN, AetherCoreManager.psiphonModeOf(listOf("--psiphon-reverse", "--psiphon")))
        assertEquals("--bind", AetherCoreManager.listenerFlagOf(listOf("--psiphon", "--psiphon-only")))
    }

    @Test
    fun whereTheAppDialsPsiphonReadinessNeedsTheCoresWord() {
        assertTrue(AetherCoreManager.readyNeedsWord(listOf("--wg", "--psiphon", "--psiphon-bind", "127.0.0.1:10819")))
        assertTrue(AetherCoreManager.readyNeedsWord(listOf("--psiphon-only", "--bind", "127.0.0.1:10819")))
        // Around the tunnel the app dials WARP, whose listener comes up only once the tunnel stands.
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--masque", "--psiphon-reverse")))
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--wg", "--tor")))
        assertFalse(AetherCoreManager.readyNeedsWord(listOf("--wg")))

        assertTrue(AetherCoreManager.isReadyWord("[2026-09-24T10:00:00.000Z INFO  aether] [+] psiphon is ready; 127.0.0.1:10819 leaves through psiphon, carried by the tunnel"))
        assertTrue(AetherCoreManager.isReadyWord("[2026-09-24T10:00:00.000Z INFO  aether] [+] psiphon is ready; 127.0.0.1:10819 leaves through psiphon"))
        assertFalse(AetherCoreManager.isReadyWord("[2026-09-24T10:00:00.000Z INFO  aether] [*] starting psiphon through the tunnel at 127.0.0.1:10820"))
        assertFalse(AetherCoreManager.isReadyWord("[2026-09-24T10:00:00.000Z INFO  aether] [+] psiphon reached a server at 203.0.113.9"))

        // The word is an info line: a quieter core never writes it, and the listener alone decides then.
        assertTrue(AetherCoreManager.showsInfo(listOf("--psiphon")))
        assertTrue(AetherCoreManager.showsInfo(listOf("--psiphon", "--log-level", "debug")))
        assertTrue(AetherCoreManager.showsInfo(listOf("--psiphon", "--verbose")))
        assertFalse(AetherCoreManager.showsInfo(listOf("--psiphon", "--log-level", "warn")))
        assertFalse(AetherCoreManager.showsInfo(listOf("--psiphon", "--log-level", "error")))
    }

    @Test
    fun aCoreThatStoppedForWantOfAnEchKeyIsReportedSo() {
        // The core's last line as its main prints the error it ends with (fork aether/src/lib.rs, session_ech_key).
        val stopped = "Error: Ech(\"ECH is on but there is no ECH key to offer (udp://1.1.1.1:53 did not answer for " +
            "cloudflare-ech.com); stopping rather than send the server name in the clear\")"
        assertTrue(AetherCoreManager.isNoEchKeyWord(stopped))
        assertEquals(Log.ERROR, AetherCoreManager.outputPriority(stopped))
        assertFalse(AetherCoreManager.isNoEchKeyWord("[2026-10-01T10:00:00.000Z INFO  aether] [+] fetched ECHConfigList automatically (71 bytes)"))
        assertFalse(AetherCoreManager.isNoEchKeyWord("[2026-10-01T10:00:00.000Z INFO  aether] [+] ECH off; the server name goes out in cleartext"))
        assertFalse(AetherCoreManager.isNoEchKeyWord("Error: Api(\"too many registrations\")"))

        assertEquals(R.string.aether_core_stopped_no_ech_key, AetherCoreManager.stoppedMessage(noEchKey = true))
        assertEquals(R.string.aether_core_stopped, AetherCoreManager.stoppedMessage(noEchKey = false))
    }

    @Test
    fun theGoProgramsAreToldWhereAndroidKeepsItsRootCertificates() {
        assertEquals(
            "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts",
            AetherCoreManager.certificateDirectories { true }
        )
        // Android 14 moved them into the Conscrypt module; older systems have the system image alone.
        assertEquals("/apex/com.android.conscrypt/cacerts", AetherCoreManager.certificateDirectories { it.path.contains("apex") })
        assertEquals("/system/etc/security/cacerts", AetherCoreManager.certificateDirectories { it.path.contains("system") })
        assertNull(AetherCoreManager.certificateDirectories { false })
        assertEquals("SSL_CERT_DIR", AetherCoreManager.CERT_DIR_ENV)
        // What Psiphon is told beyond the core's built-in configuration: resolvers for the names it looks up itself.
        assertTrue(AetherCoreManager.PSIPHON_OVERLAY.contains("\"DNSResolverAlternateServers\""))
        assertTrue(AetherCoreManager.PSIPHON_OVERLAY.contains("1.1.1.1"))
    }

    @Test
    fun thePsiphonDatastoreLivesBesideTheIdentityDirectoryAndMovesOutOfItOnce() {
        assertEquals("AETHER_PSIPHON_DIR", AetherCoreManager.PSIPHON_DIR_ENV)
        val files = Files.createTempDirectory("pattng-files").toFile()
        val work = File(files, "aether").apply { mkdirs() }
        try {
            // Nothing to move: the directory is named, not made; the core makes it.
            val dir = AetherCoreManager.psiphonStateDir(files, work)
            assertEquals(File(files, "psiphon"), dir)
            assertFalse(dir.exists())

            // A datastore the core had put inside the identity directory moves out.
            val inside = File(work, "${AetherIdentityManager.BASE_FILE}-psiphon").apply { mkdirs() }
            File(inside, "datastore").writeText("servers")
            assertEquals(dir, AetherCoreManager.psiphonStateDir(files, work))
            assertEquals("servers", File(dir, "datastore").readText())
            assertFalse(inside.exists())

            // Once out, whatever turns up inside later is left alone.
            inside.mkdirs()
            File(inside, "datastore").writeText("stale")
            AetherCoreManager.psiphonStateDir(files, work)
            assertEquals("servers", File(dir, "datastore").readText())
            assertTrue(inside.exists())
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun theCdnSetsGoToTheCoreWhereverFrontingIsInPlay() {
        val sets = profile().copy(aetherPsiphon = "chain", aetherPsiphonCdnSets = "fastly,cloudflare")
        assertEquals("cloudflare,fastly", valueAfter(AetherCoreManager.buildArguments(sets, 0), "--psiphon-cdn-sets"))
        // Beside addresses of one's own as well: the core scans both.
        val own = sets.copy(aetherPsiphonMode = "cdn", aetherPsiphonCdnIps = "203.0.113.7")
        assertEquals("cloudflare,fastly", valueAfter(AetherCoreManager.buildArguments(own, 0), "--psiphon-cdn-sets"))
        // Not where nothing is fronted, not for a stranger, not without Psiphon.
        assertNull(valueAfter(AetherCoreManager.buildArguments(sets.copy(aetherPsiphonMode = "direct"), 0), "--psiphon-cdn-sets"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(sets.copy(aetherPsiphonCdnSets = "nowhere"), 0), "--psiphon-cdn-sets"))
        assertNull(valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherPsiphonCdnSets = "fastly"), 0), "--psiphon-cdn-sets"))
    }

    @Test
    fun psiphonStartsFromTheShippedListUnlessTheProfileSaysNo() {
        val chain = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain"), 0)
        assertEquals("shipped-list", valueAfter(chain, "--psiphon-server-entries"))
        assertEquals(AetherCoreManager.SHIPPED_LIST, valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "only"), 0), AetherCoreManager.PSIPHON_SERVER_ENTRIES))
        assertFalse(AetherCoreManager.PSIPHON_SERVER_ENTRIES in AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain", aetherPsiphonBundledList = false), 0))
        assertFalse(AetherCoreManager.PSIPHON_SERVER_ENTRIES in AetherCoreManager.buildArguments(profile(), 0))
        // A scan keeps a carrier around the tunnel, list and all, and drops one inside it, list and all.
        assertEquals("shipped-list", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "reverse"), 0, scan = true), "--psiphon-server-entries"))
        assertFalse(AetherCoreManager.PSIPHON_SERVER_ENTRIES in AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain"), 0, scan = true))

        // At start the word gives way to the app's file, or goes when there is none; a file of one's own stays.
        val entries = File("/data/app/psiphon-servers.txt")
        assertEquals(
            listOf("--psiphon", "--psiphon-server-entries", entries.absolutePath),
            AetherCoreManager.withShippedList(listOf("--psiphon", "--psiphon-server-entries", "shipped-list"), entries)
        )
        assertEquals(listOf("--psiphon"), AetherCoreManager.withShippedList(listOf("--psiphon", "--psiphon-server-entries", "shipped-list"), null))
        assertEquals(
            listOf("--psiphon", "--psiphon-server-entries", "/sdcard/mine.txt"),
            AetherCoreManager.withShippedList(listOf("--psiphon", "--psiphon-server-entries", "/sdcard/mine.txt"), entries)
        )
        assertEquals(listOf("--psiphon"), AetherCoreManager.withShippedList(listOf("--psiphon"), entries))
    }

    @Test
    fun clearingThePsiphonDataRemovesTheDatastoreWhereverItIs() {
        val files = Files.createTempDirectory("pattng-files").toFile()
        val work = File(files, "aether").apply { mkdirs() }
        try {
            assertTrue(AetherCoreManager.clearPsiphonState(files, work))
            val outside = File(files, "psiphon").apply { mkdirs() }
            File(outside, "datastore").writeText("servers")
            val inside = File(work, "${AetherIdentityManager.BASE_FILE}-psiphon").apply { mkdirs() }
            File(inside, "datastore").writeText("older servers")
            val probe = AetherCoreManager.psiphonProbeDir(files).apply { mkdirs() }
            File(probe, "datastore").writeText("a test's servers")
            assertTrue(AetherCoreManager.clearPsiphonState(files, work))
            assertFalse(outside.exists())
            assertFalse(inside.exists())
            assertFalse(probe.exists())
            assertTrue(work.exists())
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun theHelpersACoreLeftBehindAreToldApartByTheirParent() {
        // /proc/<pid>/stat: the command name sits in parentheses and may hold spaces and parentheses of its own.
        assertEquals(1200, AetherCoreManager.parentPidOf("1234 (libpsiphon-tunnel-core.so) S 1200 1200 0 -1 4194560 0"))
        assertEquals(7, AetherCoreManager.parentPidOf("55 (a (weird) name) R 7 55 0"))
        assertNull(AetherCoreManager.parentPidOf("no stat at all"))
        assertNull(AetherCoreManager.parentPidOf("9 (x) S"))

        // A helper whose parent is a living core stays; one whose parent is gone, or unknown, is an orphan.
        assertEquals(
            listOf(11, 12),
            AetherCoreManager.orphanedHelpers(listOf(10 to 5, 11 to 6, 12 to null), liveCores = setOf(5))
        )
        assertTrue(AetherCoreManager.orphanedHelpers(emptyList(), liveCores = emptySet()).isEmpty())
    }

    @Test
    fun aSessionStartWaitsForTheCoresOfTestsAndScansButNotForever() {
        // A probe: a living process's core without the session mark. The session's own, a dead owner's
        // and one whose environment could not be read are not waited for.
        assertTrue(AetherCoreManager.isProbe(ownerAlive = true, sessionMarked = false))
        assertTrue(AetherCoreManager.isProbe(ownerAlive = null, sessionMarked = false))
        assertFalse(AetherCoreManager.isProbe(ownerAlive = true, sessionMarked = true))
        assertFalse(AetherCoreManager.isProbe(ownerAlive = false, sessionMarked = false))
        assertFalse(AetherCoreManager.isProbe(ownerAlive = true, sessionMarked = null))

        var looks = 0
        assertTrue(AetherCoreManager.awaitUntil(timeoutMs = 2_000, pollMs = 5) { ++looks >= 3 })
        assertEquals(3, looks)
        assertTrue(AetherCoreManager.awaitUntil(timeoutMs = 0, pollMs = 5) { true })
        assertFalse(AetherCoreManager.awaitUntil(timeoutMs = 40, pollMs = 5) { false })
        // The probes' Psiphon datastore is not the session's.
        val files = File("/data/files")
        assertEquals(File(files, "psiphon-probe"), AetherCoreManager.psiphonProbeDir(files))
        assertNotEquals(AetherCoreManager.psiphonStateDir(files, File(files, "aether")), AetherCoreManager.psiphonProbeDir(files))
    }

    @Test
    fun aProfileThatNamesNoIpVersionConnectsOverIPv4() {
        // IPv4 works on an IPv4-only network and on a dual-stack one; a profile says so when it wants more.
        assertEquals("v4", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherIpVersion = null), 0), "--ip"))
        assertEquals("v4", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherIpVersion = null), 0, scan = true), "--ip"))
        assertEquals("both", valueAfter(AetherCoreManager.buildArguments(profile().copy(aetherIpVersion = "both"), 0), "--ip"))
    }

    @Test
    fun aScanLeavesOutACarrierInsideTheTunnelAndKeepsOneAroundIt() {
        val inside = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain", aetherPsiphonRegion = "DE"), 0, scan = true)
        assertFalse(inside.any { it.startsWith("--psiphon") })
        assertEquals("127.0.0.1:0", valueAfter(inside, "--bind"))
        assertFalse("--tor" in AetherCoreManager.buildArguments(profile().copy(aetherTor = "chain"), 0, scan = true))

        // Around the tunnel the carrier is where the session looks from, so the scan looks from there as well.
        val around = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "reverse", aetherPsiphonMode = "cdn"), 0, scan = true)
        assertTrue("--psiphon-reverse" in around)
        assertEquals("cdn", valueAfter(around, "--psiphon-mode"))
        assertEquals("127.0.0.1:0", valueAfter(around, "--psiphon-bind"))
        assertEquals("127.0.0.1:0", valueAfter(around, "--bind"))
        val torAround = AetherCoreManager.buildArguments(profile().copy(aetherTor = "reverse", aetherTorBridges = "first"), 41234, scan = true)
        assertTrue("--tor-reverse" in torAround)
        assertTrue("--tor-bridges" in torAround)
        assertEquals("127.0.0.1:41234", valueAfter(torAround, "--bind"))
        assertEquals("127.0.0.1:41235", valueAfter(torAround, "--tor-bind"))
    }

    @Test
    fun aScanBindsNoPortUnlessTorAroundTheTunnelNeedsARealOne() {
        assertEquals(0, AetherCoreManager.scanPort(profile()))
        assertEquals(0, AetherCoreManager.scanPort(profile().copy(aetherPsiphon = "reverse")))
        assertEquals(0, AetherCoreManager.scanPort(profile().copy(aetherTor = "chain")))
        assertTrue(AetherCoreManager.scanPort(profile().copy(aetherTor = "reverse")) > 0)
    }

    @Test
    fun aCoreReachesWarpThroughACarrierAroundTheTunnelOnly() {
        fun scanOf(profile: ProfileItem) = AetherCoreManager.buildArguments(profile, 0, scan = true)
        assertFalse(AetherCoreManager.reachesWarpThroughCarrier(scanOf(profile())))
        assertFalse(AetherCoreManager.reachesWarpThroughCarrier(scanOf(profile().copy(aetherPsiphon = "chain", aetherTor = "chain"))))
        assertTrue(AetherCoreManager.reachesWarpThroughCarrier(scanOf(profile().copy(aetherPsiphon = "reverse"))))
        assertTrue(AetherCoreManager.reachesWarpThroughCarrier(scanOf(profile().copy(aetherTor = "reverse"))))

        // A command written by hand, as the WARP keys page may run, is read the way the core reads it.
        assertTrue(AetherCoreManager.reachesWarpThroughCarrier(listOf("--register", "all", "--tor-reverse")))
        assertFalse(AetherCoreManager.reachesWarpThroughCarrier(listOf("--register", "all", "--psiphon-reverse", "--psiphon")))
        assertFalse(AetherCoreManager.reachesWarpThroughCarrier(listOf("--register", "all")))
    }

    @Test
    fun aRunThatWaitsForAnInfoLineIsNeverQuieterThanInfo() {
        val run = listOf("--register", "all")
        // The core writes its info lines unless told otherwise.
        assertEquals(run, AetherCoreManager.withInfoLines(run))
        assertEquals(run + listOf("--log-level", "debug"), AetherCoreManager.withInfoLines(run + listOf("--log-level", "debug")))
        assertEquals(run + "--verbose", AetherCoreManager.withInfoLines(run + "--verbose"))
        // A quieter level gives way, wherever it stands.
        assertEquals(run + listOf("--log-level", "info"), AetherCoreManager.withInfoLines(listOf("--log-level", "warn") + run))
        assertEquals(run + listOf("--log-level", "info"), AetherCoreManager.withInfoLines(run + listOf("--log-level", "error")))
    }

    @Test
    fun theListenersAreLeftOutWhenTunnelsAreCompared() {
        val session = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain"), 10819)
        val elsewhere = AetherCoreManager.buildArguments(profile().copy(aetherPsiphon = "chain"), 20808)
        assertEquals(AetherCoreManager.tunnelArguments(session), AetherCoreManager.tunnelArguments(elsewhere))
        assertFalse("--psiphon-bind" in AetherCoreManager.tunnelArguments(session))
    }

    @Test
    fun theRunningProfileIsToldByTheArgumentsOfItsSession() {
        val pinned = profile(server = "162.159.198.1", port = "443")
        val session = AetherCoreManager.buildArguments(pinned, 10819, logLevel = "warn")
        assertTrue(AetherCoreManager.runsProfile(session, pinned))
        assertTrue(AetherCoreManager.runsProfile(session, pinned.copy(remarks = "another name")))
        assertFalse(AetherCoreManager.runsProfile(session, pinned.copy(serverPort = "2408")))
        assertFalse(AetherCoreManager.runsProfile(session, profile(AetherProtocol.WIREGUARD, server = "162.159.198.1", port = "443")))
        assertFalse(AetherCoreManager.runsProfile(AetherCoreManager.buildArguments(pinned, 0, scan = true), pinned))
        assertFalse(AetherCoreManager.runsProfile(emptyList(), pinned))
        // A custom configuration runs the same tunnel behind a port of its own.
        assertTrue(AetherCoreManager.runsProfile(AetherCoreManager.buildArguments(pinned, 20808), pinned))
        assertFalse(AetherCoreManager.runsProfile(AetherCoreManager.buildArguments(pinned, 20808), pinned.copy(serverPort = "2408")))
    }

    @Test
    fun theOwnerIsReadFromTheEnvironmentTheAppGaveTheCore() {
        val environ = listOf("HOME=/data/user/0/app/files/aether", "${AetherCoreManager.OWNER_ENV}=4242", "TMPDIR=/tmp")
        assertEquals(4242, AetherCoreManager.ownerPid(environ))
        assertNull(AetherCoreManager.ownerPid(listOf("HOME=/x", "${AetherCoreManager.OWNER_ENV}=")))
        assertNull(AetherCoreManager.ownerPid(listOf("HOME=/x")))
        assertNull(AetherCoreManager.ownerPid(null))
    }

    @Test
    fun theAetherListenPortIsAPortAnAppCanListenOnWithThreeMoreAfterIt() {
        assertEquals(20808, AetherCoreManager.listenPortOf(" 20808 "))
        assertEquals(1024, AetherCoreManager.listenPortOf("1024"))
        assertEquals(65532, AetherCoreManager.listenPortOf("65532"))
        for (text in listOf(null, "", "socks", "0", "80", "1023", "65533", "70000", "-1", "10819.5")) {
            assertEquals(10819, AetherCoreManager.listenPortOf(text), text.toString())
        }
    }

    @Test
    fun theAetherListenPortAndTheThreeAfterItStayClearOfTheLocalProxy() {
        val local = setOf(10808, 10809)
        assertNull(AetherCoreManager.listenPortProblem("10819", local))
        assertNull(AetherCoreManager.listenPortProblem(" 10810 ", local))
        assertNull(AetherCoreManager.listenPortProblem("10804", local))
        for (text in listOf("10805", "10806", "10808", "10809")) {
            assertEquals(AetherCoreManager.ListenPortProblem.LOCAL_PROXY, AetherCoreManager.listenPortProblem(text, local), text)
        }
        for (text in listOf("", "socks", "1023", "65533")) {
            assertEquals(AetherCoreManager.ListenPortProblem.NOT_A_PORT, AetherCoreManager.listenPortProblem(text, local), text)
        }
        // A local proxy on a port picked anew at every start is no matter here.
        assertNull(AetherCoreManager.listenPortProblem("10808", emptySet()))
    }

    @Test
    fun theExitOfACoreOfItsOwnOpensWithItsExitNode() {
        val plain = JsonParser.parseString(
            AetherCoreManager.exitConfiguration(AetherExit(dialMode = "code-1"), configuration = """{"outbounds": []}""", logLevel = "none")
        ).asJsonObject
        val exitNode = plain.getAsJsonArray("outbounds").single().asJsonObject
        assertEquals("exit-node", exitNode.get("tag").asString)
        assertEquals("freedom", exitNode.get("protocol").asString)
        assertEquals("code-1", exitNode.getAsJsonObject("streamSettings").getAsJsonObject("sockopt").get("dialMode").asString)
        // It logs at the level of the app, should it start the shared Xray of the process.
        assertEquals("none", plain.getAsJsonObject("log").get("loglevel").asString)

        // A core that dials out through a hop of its chain takes the hop from the configuration under test.
        val chained = """{"outbounds": [{"tag": "proxy", "protocol": "socks"}, {"tag": "exit-node", "protocol": "vless"}]}"""
        assertEquals(chained, AetherCoreManager.exitConfiguration(AetherExit.through(listOf(profile())), chained, "warning"))
        // So does the core of a custom configuration exported from a session, whose exit-node it carries.
        assertEquals(chained, AetherCoreManager.exitConfiguration(AetherExit.PLAIN, chained, "warning"))
        // A configuration without one gives its core the plain exit-node, whatever its core is.
        val bare = """{"outbounds": [{"tag": "proxy", "protocol": "socks"}]}"""
        for (configuration in listOf(bare, null, "not json {", "[]")) {
            assertEquals(
                AetherCoreManager.exitConfiguration(AetherExit.through(listOf(profile())), configuration = null, logLevel = "warning"),
                AetherCoreManager.exitConfiguration(AetherExit.through(listOf(profile())), configuration, "warning"),
            )
        }
    }

    @Test
    fun theKeyOfTheExitNodeIsReadFromTheEnvironmentOfACore() {
        assertEquals("abc", AetherCoreManager.exitKeyOf(listOf("HOME=/x", "${AetherCoreManager.EXIT_ENV}=abc")))
        assertNull(AetherCoreManager.exitKeyOf(listOf("HOME=/x", "${AetherCoreManager.SESSION_ENV}=1")))
        assertNull(AetherCoreManager.exitKeyOf(listOf("${AetherCoreManager.EXIT_ENV}=")))
    }

    /** An exit that opens on [port], or not at all with null, and what was done with it, in order. */
    private class CountedExit(private val port: Int? = 41234) {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

        fun open(): Int? = port.also { events += "open" }

        fun close() {
            events += "close"
        }
    }

    @Test
    fun theExitOfACoreOfItsOwnClosesOnceTheCoreIsOver() = runBlocking {
        val exit = CountedExit()
        val found = AetherCoreManager.throughExit(Mutex(), exit::open, exit::close) { port ->
            exit.events += "core on $port"
            "endpoint"
        }
        assertEquals("endpoint", found)
        assertEquals(listOf("open", "core on 41234", "close"), exit.events)
    }

    @Test
    fun theExitClosesWhenTheCoreDoesNotStart() = runBlocking {
        // withProcess has nothing to give back then.
        val exit = CountedExit()
        assertNull(AetherCoreManager.throughExit<String>(Mutex(), exit::open, exit::close) { null })
        assertEquals(listOf("open", "close"), exit.events)
    }

    @Test
    fun anExitThatDoesNotOpenRunsNoCoreAndIsNotClosed() = runBlocking {
        val exit = CountedExit(port = null)
        val found = AetherCoreManager.throughExit(Mutex(), exit::open, exit::close) { _ ->
            exit.events += "core"
            "endpoint"
        }
        assertNull(found)
        assertEquals(listOf("open"), exit.events)
    }

    @Test
    fun theExitClosesWhenTheCoreFails() {
        val exit = CountedExit()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { AetherCoreManager.throughExit<String>(Mutex(), exit::open, exit::close) { error("the core failed") } }
        }
        assertEquals(listOf("open", "close"), exit.events)
    }

    @Test
    fun theExitClosesWhenTheScanIsCancelled() = runBlocking {
        val exit = CountedExit()
        val running = CompletableDeferred<Unit>()
        val scan = launch(Dispatchers.Default) {
            AetherCoreManager.throughExit<String>(Mutex(), exit::open, exit::close) {
                running.complete(Unit)
                awaitCancellation()
            }
        }
        running.await()
        scan.cancelAndJoin()
        assertEquals(listOf("open", "close"), exit.events)
    }

    @Test
    fun theExitClosesWhenTheCancellationLandsWhileItOpens() = runBlocking {
        // The exit opens on a thread a cancellation does not stop, so it opens after the scan was
        // cancelled: nothing runs through it, and it is closed all the same.
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val opening = CountDownLatch(1)
        val mayOpen = CountDownLatch(1)
        val open = {
            opening.countDown()
            mayOpen.await()
            events += "open"
            41234
        }
        val scan = launch(Dispatchers.Default) {
            AetherCoreManager.throughExit(Mutex(), open, { events += "close" }) { _ ->
                events += "core"
                "endpoint"
            }
        }
        withContext(Dispatchers.IO) { opening.await() }
        scan.cancel()
        mayOpen.countDown()
        scan.join()
        assertEquals(listOf("open", "close"), events)
    }

    @Test
    fun theCoresOfAProcessTakeTurnsAtTheExit() = runBlocking {
        val turns = Mutex()
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val firstRuns = CompletableDeferred<Unit>()
        val firstMayEnd = CompletableDeferred<Unit>()
        val secondOpened = CompletableDeferred<Unit>()
        val first = launch(Dispatchers.Default) {
            AetherCoreManager.throughExit(turns, { events += "open 1"; 1 }, { events += "close 1" }) { _ ->
                firstRuns.complete(Unit)
                firstMayEnd.await()
                "one"
            }
        }
        firstRuns.await()
        val second = launch(Dispatchers.Default) {
            AetherCoreManager.throughExit(turns, { events += "open 2"; secondOpened.complete(Unit); 2 }, { events += "close 2" }) { _ -> "two" }
        }
        // While the first core runs, the second waits for its turn.
        assertNull(withTimeoutOrNull(300) { secondOpened.await() })
        firstMayEnd.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("open 1", "close 1", "open 2", "close 2"), events)
    }

    /** A core process that ends when asked, or, without [endsOnRequest], only when it is killed. */
    private class FakeCoreProcess(private val endsOnRequest: Boolean) : Process() {
        @Volatile
        private var running = true

        @Volatile
        var killed = false
            private set

        override fun destroy() {
            if (endsOnRequest) running = false
        }

        override fun destroyForcibly(): Process {
            killed = true
            running = false
            return this
        }

        override fun exitValue(): Int = if (running) throw IllegalThreadStateException("running") else 0
        override fun isAlive(): Boolean = running
        override fun waitFor(): Int = 0
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    @Test
    fun aCoreThatEndsWhenAskedIsNotKilled() {
        val core = FakeCoreProcess(endsOnRequest = true)
        assertTrue(AetherCoreManager.endProcess(core, waitMs = 300))
        assertFalse(core.killed)
        assertFalse(core.isAlive)
    }

    @Test
    fun aCoreThatDoesNotEndWhenAskedIsKilled() {
        val core = FakeCoreProcess(endsOnRequest = false)
        assertFalse(AetherCoreManager.endProcess(core, waitMs = 300))
        assertTrue(core.killed)
        assertFalse(core.isAlive)
    }

    @Test
    fun aCoreOfItsOwnDialsOutThroughTheExit() {
        val scan = AetherCoreManager.buildArguments(profile(), 0, scan = true)
        assertEquals(
            scan + listOf(AetherCoreManager.UPSTREAM, "socks5://127.0.0.1:41234"),
            AetherCoreManager.standaloneArguments(scan, 41234)
        )
        // A command exported from a session names the session's Xray, which a core of its own does not dial.
        val exported = listOf("--wg", AetherCoreManager.UPSTREAM, "socks5://127.0.0.1:10822", "--bind", "127.0.0.1:10820")
        assertEquals(
            listOf("--wg", "--bind", "127.0.0.1:10820", AetherCoreManager.UPSTREAM, "socks5://127.0.0.1:41234"),
            AetherCoreManager.standaloneArguments(exported, 41234)
        )
    }
}
