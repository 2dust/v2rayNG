package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreConfigManagerTest {

    private fun socks(address: String, port: Int) = V2rayConfig.OutboundBean(
        protocol = "socks",
        settings = V2rayConfig.OutboundBean.OutSettingsBean(address = address, port = port),
    )

    @Test
    fun aProfileNamedExitNodeCannotRouteBesideAnAetherCore() {
        val warp = ProfileItem.create(EConfigType.AETHER).apply { remarks = "warp"; aetherProtocol = "wg" }
        val named = ProfileItem.create(EConfigType.VLESS).apply { remarks = AppConfig.TAG_EXIT_NODE; server = "1.2.3.4"; serverPort = "443" }
        val outbounds = listOf(
            CoreConfigContext.ResolvedOutbound(AppConfig.TAG_PROXY, warp, listOf(warp), CoreResolvedType.NORMAL),
            CoreConfigContext.ResolvedOutbound(AppConfig.TAG_EXIT_NODE, named, listOf(named), CoreResolvedType.NORMAL),
        )
        assertTrue(CoreConfigManager.takesExitNodeName(AetherDependency.of(outbounds), outbounds))
        // Without an Aether core the name is nobody's.
        assertFalse(CoreConfigManager.takesExitNodeName(AetherDependency.None, outbounds))
        assertFalse(CoreConfigManager.takesExitNodeName(AetherDependency.of(outbounds.take(1)), outbounds.take(1)))
    }

    @Test
    fun aCoreThatDialsOutThroughAChainHopNeedsThatHop() {
        val warp = ProfileItem.create(EConfigType.AETHER).apply { remarks = "warp"; aetherProtocol = "wg" }
        val hop = ProfileItem.create(EConfigType.VLESS).apply { remarks = "hop"; server = "1.2.3.4"; serverPort = "443" }
        val chained = AetherCore.of(warp).copy(exit = AetherExit.through(listOf(hop)))
        val exitNode = V2rayConfig.OutboundBean(tag = AppConfig.TAG_EXIT_NODE, protocol = "vless")
        val toCore = socks(AppConfig.LOOPBACK, 10819).apply { tag = AppConfig.TAG_PROXY }

        assertTrue(CoreConfigManager.lacksChainHop(chained, listOf(toCore)))
        assertFalse(CoreConfigManager.lacksChainHop(chained, listOf(toCore, exitNode)))
        // A core that dials out plainly gets its own exit-node.
        assertFalse(CoreConfigManager.lacksChainHop(AetherCore.of(warp), listOf(toCore)))
    }

    @Test
    fun whatTheAetherCoreSendsOutLeavesThroughXray() {
        val config = V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(V2rayConfig.InboundBean(tag = "socks", port = 10808, protocol = "socks")),
            outbounds = arrayListOf(socks(AppConfig.LOOPBACK, 10819), V2rayConfig.OutboundBean(tag = "direct", protocol = "freedom")),
            routing = V2rayConfig.RoutingBean(
                domainStrategy = "AsIs",
                rules = arrayListOf(V2rayConfig.RoutingBean.RulesBean(domain = listOf("geosite:private"), outboundTag = "direct")),
            ),
        )
        val core = CoreConfigManager.routeAetherThroughXray(config, AetherCore.ofCommand("aether --bind 127.0.0.1:10819 --protocol masque")!!, 10822)!!

        val inbound = config.inbounds.last()
        assertEquals(AppConfig.TAG_SECONDARY_SOCKS, inbound.tag)
        assertEquals(10822, inbound.port)
        assertEquals("mixed", inbound.protocol)
        assertEquals(AppConfig.LOOPBACK, inbound.listen)
        assertEquals(true, inbound.settings?.udp)
        assertNull(inbound.sniffing)

        val outbound = config.outbounds.last()
        assertEquals(AppConfig.TAG_EXIT_NODE, outbound.tag)
        assertEquals("freedom", outbound.protocol)
        assertNull(outbound.mux)

        // What comes in on that inbound goes out by that outbound, before any other rule is asked.
        assertEquals(2, config.routing.rules.size)
        assertEquals(listOf(AppConfig.TAG_SECONDARY_SOCKS), config.routing.rules.first().inboundTag)
        assertEquals(AppConfig.TAG_EXIT_NODE, config.routing.rules.first().outboundTag)

        assertEquals("socks5://127.0.0.1:10822", core.arguments.last())
    }

    @Test
    fun aConfigurationThatListensOnTheSecondarySocksPortItselfIsLeftAsItIs() {
        val config = V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(V2rayConfig.InboundBean(tag = "socks", port = 10822, protocol = "socks")),
            outbounds = arrayListOf(socks(AppConfig.LOOPBACK, 10819)),
            routing = V2rayConfig.RoutingBean(domainStrategy = "AsIs", rules = arrayListOf()),
        )

        assertNull(CoreConfigManager.routeAetherThroughXray(config, AetherCore.ofCommand("aether --bind 127.0.0.1:10819")!!, 10822))
        assertEquals(1, config.inbounds.size)
        assertEquals(1, config.outbounds.size)
        assertTrue(config.routing.rules.isEmpty())
    }

    @Test
    fun theExitNodeCarriesTheFinalMaskAndDialModeOfTheAetherProfile() {
        val config = V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(),
            outbounds = arrayListOf(socks(AppConfig.LOOPBACK, 10819)),
            routing = V2rayConfig.RoutingBean(domainStrategy = "AsIs", rules = arrayListOf()),
        )
        val mask = """{"tcp": [{"type": "fragment"}]}"""
        val core = AetherCore.ofCommand("aether --bind 127.0.0.1:10819 --protocol masque")!!.copy(exit = AetherExit(mask, "code-1"))

        val routed = CoreConfigManager.routeAetherThroughXray(config, core, 10822)!!

        val exitNode = config.outbounds.last()
        assertEquals(AppConfig.TAG_EXIT_NODE, exitNode.tag)
        assertEquals("freedom", exitNode.protocol)
        assertEquals(JsonUtil.parseString(mask), exitNode.streamSettings?.finalmask)
        assertEquals("code-1", exitNode.streamSettings?.sockopt?.dialMode)
        // The outbound to the core stays as it was: what it reaches is on the loopback address.
        assertNull(config.outbounds.first().streamSettings?.sockopt?.dialMode)
        assertEquals(core.exit, routed.exit)
    }

    @Test
    fun aCoreWithAnUpstreamOfItsOwnLeavesTheConfigurationAlone() {
        val config = V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(),
            outbounds = arrayListOf(socks(AppConfig.LOOPBACK, 10819)),
            routing = V2rayConfig.RoutingBean(domainStrategy = "AsIs", rules = arrayListOf()),
        )
        val own = AetherCore.ofCommand("aether --bind 127.0.0.1:10819 --upstream socks5://127.0.0.1:1080")!!

        assertEquals(own, CoreConfigManager.routeAetherThroughXray(config, own, 10822))
        assertTrue(config.inbounds.isEmpty())
        assertEquals(1, config.outbounds.size)
        assertTrue(config.routing.rules.isEmpty())
    }
}
