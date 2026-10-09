package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetherCoreTest {

    private fun profile(block: ProfileItem.() -> Unit = {}) =
        ProfileItem.create(EConfigType.AETHER).apply {
            aetherProtocol = AetherProtocol.WIREGUARD.type
            block()
        }

    private val pinned = profile { server = "188.114.96.77"; serverPort = "443" }

    /** What [block] returns with the Aether listen port of the settings at [port]. */
    private fun <T> onListenPort(port: Int, block: () -> T): T {
        val source = AetherCoreManager.listenPortSource
        AetherCoreManager.listenPortSource = { port }
        try {
            return block()
        } finally {
            AetherCoreManager.listenPortSource = source
        }
    }

    private fun valueAfter(arguments: List<String>, flag: String): String? =
        arguments.indexOf(flag).takeIf { it >= 0 }?.let { arguments.getOrNull(it + 1) }

    @Test
    fun theCoreOfAProfileIsItsSettingsOnTheAetherListenPortWithoutALogLevel() {
        val core = onListenPort(20808) { AetherCore.of(pinned) }
        // Obfuscation left automatic is the core's own choice, so the arguments say nothing about it.
        assertEquals(
            listOf(
                "--bind", "127.0.0.1:20808", "--protocol", "wg", "--scan", "balanced", "--ip", "v4",
                "--peer", "188.114.96.77:443", "--quick-reconnect",
            ),
            core.arguments
        )
        assertEquals(20808, core.port)
        assertEquals(AetherProtocol.WIREGUARD, core.protocol)
        assertEquals(AetherCoreManager.socksPort, AetherCore.of(profile()).port)
    }

    @Test
    fun aListenPortAProfileStoredOfItsOwnCountsNoMore() {
        // Profiles stored while each profile had a listen port of its own may carry one still.
        val legacy = pinned.copy(aetherListenPort = "20808")
        assertEquals(AetherCore.of(pinned), AetherCore.of(legacy))
        assertEquals(AetherCoreManager.socksPort, AetherCore.of(legacy).port)
    }

    @Test
    fun theCommandOfAProfileReadsBackAsTheSameCore() {
        val core = AetherCore.of(pinned)
        assertEquals(
            "aether --bind 127.0.0.1:10819 --protocol wg --scan balanced --ip v4 --peer 188.114.96.77:443 --quick-reconnect",
            core.command
        )
        assertEquals(core, AetherCore.ofCommand(core.command))

        val gool = AetherCore.of(profile { aetherProtocol = AetherProtocol.GOOL.type; aetherWiwOuter = "162.159.192.1:2408" })
        assertEquals(gool, AetherCore.ofCommand(gool.command))
        assertEquals(AetherProtocol.GOOL, AetherCore.ofCommand(gool.command)!!.protocol)

        val mim = AetherCore.of(profile { aetherProtocol = AetherProtocol.MIM.type; aetherWiwInner = "188.114.96.1:443" })
        assertEquals(mim, AetherCore.ofCommand(mim.command))
        assertEquals(AetherProtocol.MIM, AetherCore.ofCommand(mim.command)!!.protocol)
    }

    @Test
    fun aCommandIsReadAsWritten() {
        // Whatever the words mean is for the core to say; the app reads the listener and the protocol.
        val core = AetherCore.ofCommand("aether --gool --scan balanced --bind 127.0.0.1:20808 --dns 1.1.1.1")!!
        assertEquals(listOf("--gool", "--scan", "balanced", "--bind", "127.0.0.1:20808", "--dns", "1.1.1.1"), core.arguments)
        assertEquals(20808, core.port)
        assertEquals(AetherProtocol.GOOL, core.protocol)
    }

    @Test
    fun theProgramNameInFrontIsDroppedWhateverItIs() {
        val arguments = listOf("--wg", "--bind", "127.0.0.1:10819")
        assertEquals(arguments, AetherCore.ofCommand("aether --wg --bind 127.0.0.1:10819")!!.arguments)
        assertEquals(arguments, AetherCore.ofCommand("/data/app/lib/libaether.so --wg --bind 127.0.0.1:10819")!!.arguments)
        assertEquals(arguments, AetherCore.ofCommand("--wg --bind 127.0.0.1:10819")!!.arguments)
        assertEquals(arguments, AetherCore.ofCommand("  aether   --wg\t--bind 127.0.0.1:10819\n")!!.arguments)
    }

    @Test
    fun aCommandWithoutABindListensOnTheDefaultPortOfTheApp() {
        // The core's own default is another port, which no outbound of the app dials.
        val core = AetherCore.ofCommand("aether --wg --turbo")!!
        assertEquals(listOf("--wg", "--turbo", "--bind", "127.0.0.1:10819"), core.arguments)
        assertEquals(AetherCoreManager.socksPort, core.port)
    }

    @Test
    fun aCommandThatNamesNothingTheAppCanRunIsNoCore() {
        assertNull(AetherCore.ofCommand(""))
        assertNull(AetherCore.ofCommand("   "))
        assertNull(AetherCore.ofCommand("aether"))
        // A listener whose port cannot be read would otherwise be replaced without a word.
        assertNull(AetherCore.ofCommand("aether --wg --bind 10819"))
        assertNull(AetherCore.ofCommand("aether --wg --bind"))
    }

    @Test
    fun quotesKeepAWordTogether() {
        val bridge = "obfs4 1.2.3.4:443 FINGERPRINT cert=abc iat-mode=0"
        val core = AetherCore.ofCommand("aether --tor-only --tor-bridge \"$bridge\" --bind 127.0.0.1:10819")!!
        assertEquals(listOf("--tor-only", "--tor-bridge", bridge, "--bind", "127.0.0.1:10819"), core.arguments)
        assertEquals("aether --tor-only --tor-bridge \"$bridge\" --bind 127.0.0.1:10819", core.command)

        assertEquals(listOf("--x", "a b"), AetherCore.words("--x 'a b'"))
        assertEquals(listOf("--x", ""), AetherCore.words("--x \"\""))
        assertEquals(listOf("--x", "a b"), AetherCore.words("--x \"a b"))
        assertEquals(listOf("--x", "ab"), AetherCore.words("--x a\"\"b"))
    }

    @Test
    fun aProcessRunsTheCoreOnWhateverPortsAndWhateverItLogs() {
        val core = AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808 --scan turbo")!!

        assertTrue(core.runsAs(core.arguments))
        assertTrue(core.runsAs(core.arguments + listOf("--log-level", "debug")))
        // The port is for the caller to compare; the tunnel is the same.
        assertTrue(core.runsAs(AetherCore.ofCommand("aether --wg --bind 127.0.0.1:41234 --scan turbo")!!.arguments))
        assertFalse(core.runsAs(AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808 --scan thorough")!!.arguments))
        assertFalse(core.runsAs(emptyList()))
    }

    @Test
    fun aProfileWithACommandOfItsOwnRunsThatCommand() {
        val core = AetherCore.of(pinned.copy(aetherCommand = "aether --wg --dns 1.1.1.1 --bind 127.0.0.1:20808"))
        assertEquals(listOf("--wg", "--dns", "1.1.1.1", "--bind", "127.0.0.1:20808"), core.arguments)
        assertEquals(20808, core.port)
        // A command the app cannot read is left aside for the settings; the editor refuses to store one.
        assertEquals(AetherCore.of(pinned), AetherCore.of(pinned.copy(aetherCommand = "aether")))
        assertEquals(AetherCore.of(pinned), AetherCore.of(pinned.copy(aetherCommand = "   ")))
    }

    @Test
    fun withPsiphonInsideTheTunnelTheAppDialsPsiphon() {
        val chain = onListenPort(20808) { AetherCore.of(pinned.copy(aetherPsiphon = "chain")) }
        assertEquals(20808, chain.port)
        assertEquals(listOf(20809, 20808), chain.ports)
        assertEquals("127.0.0.1:20809", valueAfter(chain.arguments, "--bind"))
        assertEquals("127.0.0.1:20808", valueAfter(chain.arguments, "--psiphon-bind"))

        // A hand-written command with Psiphon inside gets the app's port for Psiphon when it names none, and keeps its own otherwise.
        assertEquals(listOf("--psiphon", "--wg", "--psiphon-bind", "127.0.0.1:10819"), AetherCore.ofCommand("aether --psiphon --wg")!!.arguments)
        assertEquals(1821, AetherCore.ofCommand("aether --psiphon --bind 127.0.0.1:10819 --psiphon-bind 127.0.0.1:1821")!!.port)
        assertNull(AetherCore.ofCommand("aether --psiphon --psiphon-bind 1821"))
    }

    @Test
    fun withPsiphonAroundTheTunnelTheAppDialsTheTunnel() {
        val reverse = onListenPort(20808) { AetherCore.of(pinned.copy(aetherProtocol = "masque", aetherPsiphon = "reverse")) }
        assertEquals(20808, reverse.port)
        assertEquals(listOf(20808), reverse.ports)
        assertEquals("127.0.0.1:0", valueAfter(reverse.arguments, "--psiphon-bind"))

        val only = onListenPort(20808) { AetherCore.of(pinned.copy(aetherPsiphon = "only")) }
        assertEquals(20808, only.port)
        assertEquals(listOf(20808), only.ports)
    }

    @Test
    fun withTorInsideTheTunnelTheAppDialsTor() {
        val chain = onListenPort(20808) { AetherCore.of(pinned.copy(aetherTor = "chain")) }
        assertEquals(20808, chain.port)
        assertEquals(listOf(20809, 20808), chain.ports)
        assertEquals("127.0.0.1:20808", valueAfter(chain.arguments, "--tor-bind"))
        assertEquals("127.0.0.1:20809", valueAfter(chain.arguments, "--bind"))

        // A hand-written command with Tor inside gets the app's port for Tor when it names none, and keeps its own otherwise.
        assertEquals(listOf("--tor", "--wg", "--tor-bind", "127.0.0.1:10819"), AetherCore.ofCommand("aether --tor --wg")!!.arguments)
        assertEquals(1820, AetherCore.ofCommand("aether --tor --bind 127.0.0.1:10819 --tor-bind 127.0.0.1:1820")!!.port)
    }

    @Test
    fun withTorAroundTheTunnelTorsOwnListenerFollowsTheTunnel() {
        val reverse = onListenPort(20808) { AetherCore.of(pinned.copy(aetherProtocol = "masque", aetherTor = "reverse")) }
        assertEquals(20808, reverse.port)
        assertEquals(listOf(20808, 20809), reverse.ports)
        assertEquals("127.0.0.1:20808", valueAfter(reverse.arguments, "--bind"))
        assertEquals("127.0.0.1:20809", valueAfter(reverse.arguments, "--tor-bind"))

        // Nested carriers take the ports after it in the order the profile hands them out; Psiphon around the tunnel keeps an ephemeral port.
        val nested = onListenPort(20808) { AetherCore.of(pinned.copy(aetherProtocol = "masque", aetherPsiphon = "chain", aetherTor = "reverse")) }
        assertEquals(20808, nested.port)
        assertEquals(listOf(20809, 20810, 20808), nested.ports)
        assertEquals("127.0.0.1:20808", valueAfter(nested.arguments, "--psiphon-bind"))
        assertEquals("127.0.0.1:20809", valueAfter(nested.arguments, "--bind"))
        assertEquals("127.0.0.1:20810", valueAfter(nested.arguments, "--tor-bind"))

        val torInside = onListenPort(20808) { AetherCore.of(pinned.copy(aetherProtocol = "masque", aetherPsiphon = "reverse", aetherTor = "chain")) }
        assertEquals("127.0.0.1:20808", valueAfter(torInside.arguments, "--tor-bind"))
        assertEquals("127.0.0.1:20809", valueAfter(torInside.arguments, "--bind"))
        assertEquals("127.0.0.1:0", valueAfter(torInside.arguments, "--psiphon-bind"))
    }

    @Test
    fun aCoreNamesItsTunnelFromTheOutsideIn() {
        assertEquals(listOf("WIREGUARD"), AetherCore.of(pinned).path)
        assertEquals(listOf("WIREGUARD", "PSIPHON"), AetherCore.of(pinned.copy(aetherPsiphon = "chain")).path)
        assertEquals(listOf("PSIPHON"), AetherCore.of(pinned.copy(aetherPsiphon = "only")).path)
        assertEquals(listOf("TOR", "MASQUE"), AetherCore.of(pinned.copy(aetherProtocol = "masque", aetherTor = "reverse")).path)
        assertEquals(listOf("GOOL", "TOR"), AetherCore.ofCommand("aether --gool --tor")!!.path)
    }

    @Test
    fun theSameArgumentsAreTheSameCore() {
        assertEquals(AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808"), AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808"))
        assertNotEquals(AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808"), AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20809"))
        assertNotEquals(AetherCore.ofCommand("aether --wg --bind 127.0.0.1:20808"), AetherCore.ofCommand("aether --bind 127.0.0.1:20808 --wg"))
    }

    @Test
    fun aCoreDialsOutThroughXrayAndStaysTheSameTunnel() {
        val core = AetherCore.ofCommand("aether --bind 127.0.0.1:10819 --protocol wg --scan balanced")!!
        val routed = core.through(10821)
        assertFalse(core.hasUpstream)
        assertTrue(routed.hasUpstream)
        assertEquals("socks5://127.0.0.1:10821", valueAfter(routed.arguments, "--upstream"))
        // The session's core is the profile's tunnel still: a process running one runs the other.
        assertTrue(core.runsAs(routed.arguments))
        assertTrue(routed.runsAs(core.arguments))
        assertEquals(core.ports, routed.ports)

        // A command that names an upstream of its own keeps it.
        val own = AetherCore.ofCommand("aether --bind 127.0.0.1:10819 --upstream socks5://127.0.0.1:1080")!!
        assertTrue(own.hasUpstream)
        assertEquals(own, own.through(10821))
    }

    @Test
    fun theSecondarySocksPortIsThreeAboveTheListenPortWhereNoCoreListens() {
        // Psiphon inside the tunnel and Tor around it take the most ports: the listen port and the two above it.
        val most = profile {
            aetherProtocol = AetherProtocol.MASQUE.type
            aetherPsiphon = AetherPsiphon.CHAIN.type
            aetherTor = AetherTor.REVERSE.type
        }
        assertEquals(10819, AetherCoreManager.socksPort)
        assertEquals(10822, AetherCoreManager.secondarySocksPort)
        assertEquals(listOf(10819, 10820, 10821), AetherCore.of(most).ports.sorted())
        onListenPort(20808) {
            assertEquals(20808, AetherCoreManager.socksPort)
            assertEquals(20811, AetherCoreManager.secondarySocksPort)
            assertEquals(listOf(20808, 20809, 20810), AetherCore.of(most).ports.sorted())
        }
    }

    @Test
    fun theHopsOfAChainAreAnExitNodeOfTheirOwn() {
        val vless = ProfileItem.create(EConfigType.VLESS).apply { remarks = "v"; server = "1.2.3.4"; serverPort = "443"; password = "secret-uuid" }
        val trojan = ProfileItem.create(EConfigType.TROJAN).apply { remarks = "t"; server = "5.6.7.8"; serverPort = "443"; password = "secret-password" }
        val through = AetherExit.through(listOf(vless, trojan))
        assertEquals(through, AetherExit.through(listOf(vless.copy(), trojan.copy())))
        assertNotEquals(through, AetherExit.through(listOf(trojan, vless)))
        assertNotEquals(through, AetherExit.through(listOf(vless)))
        assertNotEquals(through, AetherExit.through(listOf(vless.copy(password = "another"), trojan)))
        assertNull(through.finalMask)
        assertNull(through.dialMode)
        // Neither what tells the hops apart nor the key of the exit-node holds what the hops are made of.
        assertFalse(through.toString().contains("secret"))
        assertFalse(through.key.contains("secret"))
    }

    @Test
    fun everyExitNodeHasAKeyOfItsOwn() {
        assertEquals(AetherExit.PLAIN.key, AetherExit().key)
        assertEquals(64, AetherExit.PLAIN.key.length)
        assertEquals(AetherExit(dialMode = "code-1").key, AetherExit.of(pinned.copy(dialMode = "code-1")).key)
        val keys = listOf(
            AetherExit.PLAIN,
            AetherExit(dialMode = "code-1"),
            AetherExit(finalMask = "code-1"),
            AetherExit(finalMask = """{"tcp": []}""", dialMode = "code-1"),
            AetherExit.through(listOf(pinned)),
        ).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theCoreOfAProfileDialsOutThroughTheExitNodeOfItsProfile() {
        val masked = pinned.copy(finalMask = """{"tcp": [{"type": "fragment"}]}""", dialMode = "custom")
        val core = AetherCore.of(masked)
        assertEquals(AetherExit("""{"tcp": [{"type": "fragment"}]}""", "custom"), core.exit)
        // The exit-node is no part of the command line; the same arguments with another exit-node are another core.
        assertEquals(AetherCore.of(pinned).arguments, core.arguments)
        assertNotEquals(AetherCore.of(pinned), core)
        assertEquals(AetherExit.PLAIN, AetherCore.of(pinned.copy(finalMask = " ", dialMode = "")).exit)
        // A command written by hand dials out through the profile's exit-node as well; a custom configuration's, through a plain one.
        assertEquals(core.exit, AetherCore.of(masked.copy(aetherCommand = core.command)).exit)
        assertEquals(AetherExit.PLAIN, AetherCore.ofCommand(core.command)!!.exit)
        // Dialling out through Xray keeps its exit-node.
        assertEquals(core.exit, core.through(41236).exit)
    }
}
