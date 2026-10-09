package com.v2ray.ang.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetherDependencyTest {

    private val masque = ProfileItem.create(EConfigType.AETHER).apply { remarks = "warp"; aetherProtocol = AetherProtocol.MASQUE.type }
    private val masqueCopy = ProfileItem.create(EConfigType.AETHER).apply { remarks = "warp again"; aetherProtocol = AetherProtocol.MASQUE.type }
    private val wireguard = ProfileItem.create(EConfigType.AETHER).apply { remarks = "wg"; aetherProtocol = AetherProtocol.WIREGUARD.type }
    private val vless = ProfileItem.create(EConfigType.VLESS).apply { remarks = "vless"; server = "1.2.3.4"; serverPort = "443" }
    private val trojan = ProfileItem.create(EConfigType.TROJAN).apply { remarks = "trojan"; server = "5.6.7.8"; serverPort = "443" }

    private fun outbound(tag: String, type: CoreResolvedType, vararg profiles: ProfileItem) =
        CoreConfigContext.ResolvedOutbound(tag, profiles.first(), profiles.toList(), type)

    /** The dependency on the core of [profile]. */
    private fun single(profile: ProfileItem) = AetherDependency.Single(AetherCore.of(profile))

    private fun coreOf(dependency: AetherDependency): AetherCore = (dependency as AetherDependency.Single).core

    @Test
    fun aConfigurationWithoutAetherNeedsNoCore() {
        assertEquals(AetherDependency.None, AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, vless))))
        assertEquals(AetherDependency.None, AetherDependency.of(emptyList()))
    }

    @Test
    fun theSelectedAetherProfileIsTheDependency() {
        assertEquals(single(masque), AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, masque))))
    }

    /** The dependency on the core of [profile] dialling out through [hops], the hops on its entry side in a chain. */
    private fun through(profile: ProfileItem, vararg hops: ProfileItem) =
        AetherDependency.Single(AetherCore.of(profile).copy(exit = AetherExit.through(hops.toList())))

    @Test
    fun anAetherEntryHopDialsOutThroughItsOwnExitNode() {
        // Chain profiles are stored exit first, entry last: this chain dials the Aether core first.
        assertEquals(
            single(masque),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, vless, masque)))
        )
    }

    @Test
    fun anyOtherAetherHopDialsOutThroughTheHopsOnItsEntrySide() {
        val exitHop = AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless)))
        assertEquals(through(masque, vless), exitHop)
        assertEquals(through(masque, trojan), AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, vless, masque, trojan))))
        assertEquals(
            through(masque, vless, trojan),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless, trojan)))
        )
        // The exit-node settings of the profile do not count there: the hop is the exit-node.
        val masked = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = "warp masked"; aetherProtocol = AetherProtocol.MASQUE.type; finalMask = """{"tcp": []}"""; dialMode = "code-1"
        }
        assertEquals(exitHop, AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masked, vless))))
    }

    @Test
    fun theHopsAnAetherHopDialsOutThroughAreTheCoresOwn() {
        // Two chains through the same hops share the core; one core cannot dial out two ways.
        assertEquals(
            through(masque, vless),
            AetherDependency.of(
                listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless), outbound("warp", CoreResolvedType.PROXYCHAIN, masqueCopy, vless))
            )
        )
        assertEquals(
            AetherDependency.TwoExits,
            AetherDependency.of(
                listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless), outbound("warp", CoreResolvedType.PROXYCHAIN, masque, trojan))
            )
        )
        assertEquals(
            AetherDependency.TwoExits,
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless), outbound("warp", CoreResolvedType.NORMAL, masque)))
        )
        assertEquals(
            AetherDependency.TwoExits,
            AetherDependency.of(
                listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless), outbound("warp", CoreResolvedType.PROXYCHAIN, trojan, masque))
            )
        )
        // Another tunnel is another core, wherever it dials out.
        assertEquals(
            AetherDependency.Conflicting,
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless), outbound("warp", CoreResolvedType.NORMAL, wireguard)))
        )
    }

    @Test
    fun aChainCanHaveOneAetherHop() {
        assertEquals(
            AetherDependency.TwoAetherHops("proxy"),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, masque, vless, wireguard)))
        )
        assertEquals(
            AetherDependency.TwoAetherHops("warp"),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, vless), outbound("warp", CoreResolvedType.PROXYCHAIN, masque, masqueCopy)))
        )
    }

    @Test
    fun aRoutingTargetOrAGroupMemberMayBeAetherAnywhere() {
        assertEquals(
            single(wireguard),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, vless), outbound("warp", CoreResolvedType.NORMAL, wireguard)))
        )
        assertEquals(
            single(masque),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.POLICYGROUP, vless, masque, trojan)))
        )
    }

    @Test
    fun theSameSettingsUnderTwoNamesAreOneDependency() {
        assertEquals(
            single(masque),
            AetherDependency.of(
                listOf(outbound("proxy", CoreResolvedType.PROXYCHAIN, vless, masque), outbound("warp", CoreResolvedType.NORMAL, masqueCopy))
            )
        )
    }

    @Test
    fun aListenPortAProfileStoredOfItsOwnSplitsNoCore() {
        // Profiles stored while each profile had a listen port of its own may carry one still; every core listens on the one port now.
        val elsewhere = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = "warp on 20808"; aetherProtocol = AetherProtocol.MASQUE.type; aetherListenPort = "20808"
        }
        assertEquals(
            single(masque),
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, masque), outbound("warp", CoreResolvedType.NORMAL, elsewhere)))
        )
    }

    @Test
    fun twoDifferentAetherProfilesCannotShareOneCore() {
        assertEquals(
            AetherDependency.Conflicting,
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, masque), outbound("warp", CoreResolvedType.NORMAL, wireguard)))
        )
        assertEquals(
            AetherDependency.Conflicting,
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.POLICYGROUP, masque, wireguard)))
        )
    }

    @Test
    fun twoProfilesOnOneTunnelWithDifferentExitNodesCannotShareOneCore() {
        // A process dials out through one exit-node.
        val masked = ProfileItem.create(EConfigType.AETHER).apply {
            remarks = "warp masked"; aetherProtocol = AetherProtocol.MASQUE.type; finalMask = """{"tcp": [{"type": "fragment"}]}"""
        }
        assertEquals(
            AetherDependency.TwoExits,
            AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.NORMAL, masque), outbound("warp", CoreResolvedType.NORMAL, masked)))
        )
        val alsoMasked = masked.copy(remarks = "warp masked again")
        val dependency = AetherDependency.of(listOf(outbound("proxy", CoreResolvedType.POLICYGROUP, masked, alsoMasked)))
        assertEquals(single(masked), dependency)
        assertEquals(AetherExit("""{"tcp": [{"type": "fragment"}]}"""), coreOf(dependency).exit)
    }

    // ---- custom configurations, with the command line of the core at the top

    /** A custom configuration; [aetherCommand] is the JSON of the value, null for no key. */
    private fun custom(vararg outbounds: String, aetherCommand: String? = null): JsonObject {
        val command = aetherCommand?.let { "\"aetherCommand\": $it, " }.orEmpty()
        return JsonParser.parseString("""{$command"inbounds": [], "outbounds": [${outbounds.joinToString(",")}], "routing": {}}""").asJsonObject
    }

    /** [command] as the JSON string an aetherCommand holds. */
    private fun quoted(command: String) = "\"$command\""

    private fun socksTo(tag: String, port: Any = 10819, address: String = "127.0.0.1") =
        """{"tag": "$tag", "protocol": "socks", "settings": {"address": "$address", "port": $port}}"""

    private val freedom = """{"tag": "direct", "protocol": "freedom"}"""

    @Test
    fun aCustomConfigurationWithoutAnAetherCommandNeedsNoCore() {
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(custom(freedom, socksTo("proxy"))))
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(JsonParser.parseString("{}").asJsonObject))
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(custom(socksTo("proxy"), aetherCommand = "null")))
    }

    @Test
    fun theCoreOfACustomConfigurationIsItsCommandAsWritten() {
        val dependency = AetherDependency.ofCustom(
            custom(freedom, socksTo("proxy", port = 20808), aetherCommand = quoted("aether --gool --scan balanced --bind 127.0.0.1:20808"))
        )

        val core = coreOf(dependency)
        assertEquals(listOf("--gool", "--scan", "balanced", "--bind", "127.0.0.1:20808"), core.arguments)
        assertEquals(20808, core.port)
        assertEquals(AetherProtocol.GOOL, core.protocol)
    }

    @Test
    fun aCommandWithoutABindListensWhereTheOutboundsOfTheAppDialByDefault() {
        val core = coreOf(AetherDependency.ofCustom(custom(socksTo("proxy"), aetherCommand = quoted("aether --wg"))))
        assertEquals(AetherCoreManager.socksPort, core.port)
        assertEquals(listOf("--wg", "--bind", "127.0.0.1:10819"), core.arguments)
    }

    @Test
    fun theCommandHasToBeDialedByASocksOutbound() {
        val command = quoted("aether --wg --bind 127.0.0.1:20808")
        assertEquals(AetherDependency.NoOutbound(20808), AetherDependency.ofCustom(custom(freedom, aetherCommand = command)))
        assertEquals(AetherDependency.NoOutbound(20808), AetherDependency.ofCustom(custom(socksTo("proxy"), aetherCommand = command)))
        assertEquals(
            AetherDependency.NoOutbound(20808),
            AetherDependency.ofCustom(custom(socksTo("proxy", port = 20808, address = "10.0.0.2"), aetherCommand = command))
        )
        assertEquals(AetherDependency.NoOutbound(20808), AetherDependency.ofCustom(custom(socksTo("proxy", port = "\"20808\""), aetherCommand = command)))
        assertEquals(
            AetherDependency.NoOutbound(20808),
            AetherDependency.ofCustom(custom("""{"protocol": "http", "settings": {"address": "127.0.0.1", "port": 20808}}""", aetherCommand = command))
        )
        // The other outbounds take no part, wherever they dial.
        assertEquals(
            20808,
            coreOf(AetherDependency.ofCustom(custom(socksTo("local", port = 1080), freedom, socksTo("warp", port = 20808), aetherCommand = command))).port
        )
    }

    @Test
    fun anAetherCommandThatIsNoCommandLineIsReported() {
        fun written(aetherCommand: String) = AetherDependency.ofCustom(custom(socksTo("proxy"), aetherCommand = aetherCommand))
        assertEquals(AetherDependency.UnusableCommand("42"), written("42"))
        assertEquals(AetherDependency.UnusableCommand("""{"protocol":"wg"}"""), written("""{"protocol": "wg"}"""))
        assertEquals(AetherDependency.UnusableCommand("""["aether","--wg"]"""), written("""["aether", "--wg"]"""))
        assertEquals(AetherDependency.UnusableCommand(""), written(quoted("")))
        assertEquals(AetherDependency.UnusableCommand("aether"), written(quoted("aether")))
        // A listener whose port cannot be read: the port is what ties the outbounds to the core.
        assertEquals(AetherDependency.UnusableCommand("aether --wg --bind 10819"), written(quoted("aether --wg --bind 10819")))
        // What was written reaches the screen, not the log: a mistyped command can carry a secret.
        assertFalse(written(quoted("aether --access-secret s3cret --bind 10819")).toString().contains("s3cret"))
    }

    @Test
    fun theFullConfigurationOfAProfileAsksForTheSameCore() {
        val profile = ProfileItem.create(EConfigType.AETHER).apply {
            aetherProtocol = AetherProtocol.MASQUE.type
            aetherTransport = "h2"
            aetherFragment = true
            aetherFragmentSize = "16-32"
            server = "162.159.198.1"
            serverPort = "443"
        }
        val core = AetherCore.of(profile)

        val reimported = coreOf(AetherDependency.ofCustom(custom(socksTo("proxy", port = core.port), freedom, aetherCommand = quoted(core.command))))

        assertEquals(core, reimported)
        assertEquals("127.0.0.1:10819", AetherCoreManager.bindAddressOf(reimported.arguments))
    }

    @Test
    fun aCustomConfigurationWithoutACommandAsksForNoCore() {
        // SOCKS outbounds on the loopback address are not enough: only aetherCommand names a core.
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(custom(socksTo("proxy", port = 20808), freedom)))
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(custom("""{"tag": "local", "protocol": "socks", "settings": {"address": "127.0.0.1", "port": 1080}}""")))
        // A JSON null at the key is no command either.
        assertEquals(AetherDependency.None, AetherDependency.ofCustom(custom(socksTo("proxy"), aetherCommand = "null")))
    }

    private fun withInbounds(vararg inbounds: String) = """{"inbounds": [${inbounds.joinToString(",")}], "outbounds": []}"""

    @Test
    fun anInboundOnTheAetherPortIsFoundBeforeTheCoreIsStarted() {
        // What the app builds: the local proxy with a port, and a tun inbound without one.
        val built = withInbounds(
            """{"tag": "socks", "protocol": "socks", "listen": "127.0.0.1", "port": 10808}""",
            """{"tag": "tun", "protocol": "tun", "settings": {"mtu": 1500}}""",
        )
        assertTrue(AetherDependency.inboundListensOn(built, 10808))
        assertFalse(AetherDependency.inboundListensOn(built, 10819))

        // The local proxy moved onto the default Aether port, or picked there at random.
        assertTrue(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "socks", "port": 10819}"""), 10819))
    }

    @Test
    fun theInboundPortsOfACustomConfigurationAreReadInEveryFormXrayReads() {
        assertTrue(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "socks", "port": "20808"}"""), 20808))
        assertTrue(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "dokodemo-door", "port": "20000-21000"}"""), 20808))
        assertTrue(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "http", "port": "53, 443 ,20800-20810"}"""), 20808))
        assertFalse(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "http", "port": "53,443,20800-20807"}"""), 20808))
        assertFalse(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "socks", "port": "20000-21000"}"""), 21001))
        // A port taken from the environment is not known here, and it is not a reason to refuse the start.
        assertFalse(AetherDependency.inboundListensOn(withInbounds("""{"protocol": "socks", "port": "env:PORT"}"""), 20808))
    }

    @Test
    fun aConfigurationWithoutReadableInboundsCollidesWithNothing() {
        assertFalse(AetherDependency.inboundListensOn("""{"outbounds": []}""", 10819))
        assertFalse(AetherDependency.inboundListensOn("""{"inbounds": {"port": 10819}}""", 10819))
        assertFalse(AetherDependency.inboundListensOn(withInbounds("10819", """{"port": null}""", """{"port": [10819]}""", """{"port": true}"""), 10819))
        assertFalse(AetherDependency.inboundListensOn("", 10819))
        assertFalse(AetherDependency.inboundListensOn("not json {", 10819))
        assertFalse(AetherDependency.inboundListensOn("[]", 10819))
    }

    /** A custom configuration on the core [command] names, with SOCKS inbounds on [inboundPorts]. */
    private fun customOnCore(command: String, vararg inboundPorts: Int): JsonObject = JsonParser.parseString(
        """
        {
          "aetherCommand": "$command",
          "inbounds": [${inboundPorts.joinToString(",") { """{"tag": "in-$it", "port": $it, "protocol": "socks"}""" }}],
          "outbounds": [
            {"tag": "proxy", "protocol": "socks", "settings": {"address": "127.0.0.1", "port": 10819}},
            {"tag": "direct", "protocol": "freedom"}
          ],
          "routing": {"rules": [{"domain": ["geosite:private"], "outboundTag": "direct"}]}
        }
        """
    ).asJsonObject

    private fun routing(config: JsonObject, core: AetherCore = coreOf(AetherDependency.ofCustom(config))) =
        AetherDependency.routeThroughXray(config, core, 10822)

    private fun routed(config: JsonObject): AetherCore = (routing(config) as AetherDependency.Routing.Routed).core

    @Test
    fun whatTheCoreOfACustomConfigurationSendsOutLeavesThroughXray() {
        val config = customOnCore("aether --bind 127.0.0.1:10819 --protocol wg", 10808)
        val core = routed(config)

        val inbound = config.getAsJsonArray("inbounds").last().asJsonObject
        assertEquals("secondary-socks", inbound.get("tag").asString)
        assertEquals(10822, inbound.get("port").asInt)
        assertEquals("127.0.0.1", inbound.get("listen").asString)
        assertEquals("mixed", inbound.get("protocol").asString)
        assertTrue(inbound.getAsJsonObject("settings").get("udp").asBoolean)
        assertFalse(inbound.has("sniffing"))

        val outbound = config.getAsJsonArray("outbounds").last().asJsonObject
        assertEquals("exit-node", outbound.get("tag").asString)
        assertEquals("freedom", outbound.get("protocol").asString)

        // What comes in on that inbound goes out by that outbound, before any rule of the configuration is asked.
        val rules = config.getAsJsonObject("routing").getAsJsonArray("rules")
        assertEquals(2, rules.size())
        assertEquals("secondary-socks", rules[0].asJsonObject.getAsJsonArray("inboundTag").single().asString)
        assertEquals("exit-node", rules[0].asJsonObject.get("outboundTag").asString)
        assertEquals("direct", rules[1].asJsonObject.get("outboundTag").asString)

        assertEquals("aether --bind 127.0.0.1:10819 --protocol wg --upstream socks5://127.0.0.1:10822", core.command)
        assertEquals(core.command, config.get("aetherCommand").asString)
    }

    @Test
    fun aCustomConfigurationThatListensOnTheSecondarySocksPortItselfIsLeftAsItIs() {
        for (inbounds in listOf(intArrayOf(10808, 10822), intArrayOf(10808))) {
            val config = customOnCore("aether --bind 127.0.0.1:10819", *inbounds)
            if (10822 !in inbounds) {
                // A range of ports counts as well.
                config.getAsJsonArray("inbounds").add(JsonParser.parseString("""{"port": "10820-10830", "protocol": "dokodemo-door"}"""))
            }
            val asWritten = config.deepCopy()
            assertEquals(AetherDependency.Routing.PortTaken, routing(config))
            assertEquals(asWritten, config)
        }
    }

    @Test
    fun aCustomConfigurationWhoseBalancersWouldPickTheExitNodeIsLeftAsItIs() {
        // Balancers and observatories pick the outbounds whose tags start with a selector.
        val balanced = customOnCore("aether --bind 127.0.0.1:10819").apply {
            getAsJsonObject("routing").add("balancers", JsonParser.parseString("""[{"tag": "b", "selector": ["proxy", "exit"]}]"""))
        }
        val observed = customOnCore("aether --bind 127.0.0.1:10819").apply {
            add("observatory", JsonParser.parseString("""{"subjectSelector": ["e"]}"""))
        }
        val burst = customOnCore("aether --bind 127.0.0.1:10819").apply {
            add("burstObservatory", JsonParser.parseString("""{"subjectSelector": [""]}"""))
        }
        for ((config, selector) in listOf(balanced to "exit", observed to "e", burst to "")) {
            val asWritten = config.deepCopy()
            assertEquals(AetherDependency.Routing.ExitNodeSelected(selector), routing(config))
            assertEquals(asWritten, config)
        }

        // A selector the exit-node's tag does not start with is no matter.
        val elsewhere = customOnCore("aether --bind 127.0.0.1:10819").apply {
            getAsJsonObject("routing").add("balancers", JsonParser.parseString("""[{"tag": "b", "selector": ["exit-node-", "proxy"]}]"""))
            add("observatory", JsonParser.parseString("""{"subjectSelector": ["proxy"]}"""))
        }
        assertTrue(routed(elsewhere).hasUpstream)
    }

    @Test
    fun aCustomConfigurationWithoutRoutingGetsTheRuleAlone() {
        val config = customOnCore("aether --bind 127.0.0.1:10819").apply { remove("routing") }
        routed(config)
        assertEquals(1, config.getAsJsonObject("routing").getAsJsonArray("rules").size())
    }

    @Test
    fun aCustomConfigurationRoutedAlreadyIsLeftAsWritten() {
        // An exported configuration carries the entries and its core the upstream already.
        val exported = customOnCore("aether --bind 127.0.0.1:10819 --upstream socks5://127.0.0.1:10821")
        val exportedAsWritten = exported.deepCopy()
        val exportedCore = coreOf(AetherDependency.ofCustom(exported))
        assertEquals(AetherDependency.Routing.Routed(exportedCore), routing(exported, exportedCore))
        assertEquals(exportedAsWritten, exported)

        // So is one with an inbound or an outbound of its own under those tags, and one whose inbounds are no list.
        val tagged = customOnCore("aether --bind 127.0.0.1:10819").apply {
            getAsJsonArray("outbounds").add(JsonParser.parseString("""{"tag": "exit-node", "protocol": "blackhole"}"""))
        }
        val notAList = customOnCore("aether --bind 127.0.0.1:10819").apply { add("inbounds", JsonObject()) }
        for (config in listOf(tagged, notAList)) {
            val asWritten = config.deepCopy()
            val core = coreOf(AetherDependency.ofCustom(config))
            assertFalse((routing(config, core) as AetherDependency.Routing.Routed).core.hasUpstream)
            assertEquals(asWritten, config)
        }
    }
}
