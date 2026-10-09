package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoreConfigContextBuilderTest {

    private fun aether(name: String, protocol: AetherProtocol) =
        ProfileItem.create(EConfigType.AETHER).apply { remarks = name; aetherProtocol = protocol.type }

    private val vless = ProfileItem.create(EConfigType.VLESS).apply { remarks = "vless"; server = "1.2.3.4"; serverPort = "443" }

    @Test
    fun aGroupKeepsItsFirstAetherProfileAndAnyWithTheSameSettings() {
        val first = aether("warp", AetherProtocol.MASQUE)
        val same = aether("warp again", AetherProtocol.MASQUE)
        val other = aether("wg", AetherProtocol.WIREGUARD)

        val (kept, leftOut) = CoreConfigContextBuilder.withOneAetherProfile(listOf(vless, first, other, same))

        assertEquals(listOf(vless, first, same), kept)
        assertEquals(listOf(other), leftOut)
    }

    @Test
    fun aGroupKeepsTheSameTunnelStoredWithAListenPortOfItsOwn() {
        // Profiles stored while each profile had a listen port of its own may carry one still; every core listens on the one port now.
        val first = aether("warp", AetherProtocol.MASQUE)
        val elsewhere = aether("warp on 20808", AetherProtocol.MASQUE).apply { aetherListenPort = "20808" }

        val (kept, leftOut) = CoreConfigContextBuilder.withOneAetherProfile(listOf(first, elsewhere))

        assertEquals(listOf(first, elsewhere), kept)
        assertEquals(emptyList<ProfileItem>(), leftOut)
    }

    @Test
    fun aGroupWithoutAetherIsUnchanged() {
        assertEquals(listOf(vless) to emptyList<ProfileItem>(), CoreConfigContextBuilder.withOneAetherProfile(listOf(vless)))
        assertEquals(emptyList<ProfileItem>() to emptyList<ProfileItem>(), CoreConfigContextBuilder.withOneAetherProfile(emptyList()))
    }
}
