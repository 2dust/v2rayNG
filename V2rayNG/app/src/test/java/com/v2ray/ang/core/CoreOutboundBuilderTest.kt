package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for CoreOutboundBuilder.applyDialMode: dialMode must land in
 * streamSettings.sockopt without discarding the other sockopt options.
 */
class CoreOutboundBuilderTest {

    private fun createProfile(mode: String?): ProfileItem =
        ProfileItem.create(EConfigType.VLESS).apply { dialMode = mode }

    @Test
    fun test_applyDialMode_setsSockoptDialMode() {
        val outbound = OutboundBean(protocol = "vless", streamSettings = OutboundBean.StreamSettingsBean())

        CoreOutboundBuilder.applyDialMode(outbound, createProfile("code-1"))

        assertEquals("code-1", outbound.streamSettings?.sockopt?.dialMode)
        assertEquals(AppConfig.DEFAULT_NETWORK, outbound.streamSettings?.network)
    }

    @Test
    fun test_applyDialMode_keepsExistingSockoptOptions() {
        val outbound = OutboundBean(
            protocol = "vless",
            streamSettings = OutboundBean.StreamSettingsBean(
                sockopt = OutboundBean.StreamSettingsBean.SockoptBean(
                    dialerProxy = "hop-1",
                    domainStrategy = "UseIP"
                )
            )
        )

        CoreOutboundBuilder.applyDialMode(outbound, createProfile("code-1"))

        val sockopt = outbound.streamSettings?.sockopt
        assertEquals("code-1", sockopt?.dialMode)
        assertEquals("hop-1", sockopt?.dialerProxy)
        assertEquals("UseIP", sockopt?.domainStrategy)
    }

    @Test
    fun test_applyDialMode_ignoresBlankDialMode() {
        val outbound = OutboundBean(protocol = "vless", streamSettings = OutboundBean.StreamSettingsBean())

        CoreOutboundBuilder.applyDialMode(outbound, createProfile(" "))
        CoreOutboundBuilder.applyDialMode(outbound, createProfile(null))

        assertNull(outbound.streamSettings?.sockopt)
    }

    @Test
    fun test_applyDialMode_wireguardWithoutStreamSettings_getsSockoptWithoutNetwork() {
        // wireguard outbounds are created without streamSettings; Xray still dials their
        // endpoint through the system dialer, so a sockopt-only streamSettings is added
        val outbound = OutboundBean(protocol = "wireguard")

        CoreOutboundBuilder.applyDialMode(outbound, createProfile("code-1"))

        assertNotNull(outbound.streamSettings)
        assertNull(outbound.streamSettings?.network)
        assertEquals("code-1", outbound.streamSettings?.sockopt?.dialMode)
    }

    /** A profile whose sni and finalMask keep populateTlsSettings away from Utils and MMKV. */
    private fun echProfile(security: String, echOutbound: String): ProfileItem =
        ProfileItem.create(EConfigType.VLESS).apply {
            this.security = security
            sni = "example.com"
            finalMask = "{}"
            echConfigList = "cloudflare-ech.com+https://1.1.1.1/dns-query"
            this.echOutbound = echOutbound
        }

    @Test
    fun test_populateTlsSettings_attachesTheEchOutboundForTlsAsWritten() {
        // EchOutbound.serialize checks it, so an invalid one reaches it too and fails the configuration there.
        for (echOutbound in listOf("""{"tag": "ech-out", "protocol": "freedom"}""", """{"tag": "proxy"}""")) {
            val streamSettings = OutboundBean.StreamSettingsBean()

            CoreOutboundBuilder.populateTlsSettings(streamSettings, echProfile(AppConfig.TLS, echOutbound), null)

            assertEquals(echOutbound, streamSettings.tlsSettings?.echOutbound)
        }
    }

    @Test
    fun test_populateTlsSettings_attachesNoEchOutboundForRealityOrABlankOne() {
        val reality = OutboundBean.StreamSettingsBean()
        CoreOutboundBuilder.populateTlsSettings(reality, echProfile(AppConfig.REALITY, """{"tag": "ech-out"}"""), null)
        assertNotNull(reality.realitySettings)
        assertNull(reality.realitySettings?.echOutbound)

        val blank = OutboundBean.StreamSettingsBean()
        CoreOutboundBuilder.populateTlsSettings(blank, echProfile(AppConfig.TLS, " "), null)
        assertNotNull(blank.tlsSettings)
        assertNull(blank.tlsSettings?.echOutbound)
    }

    @Test
    fun test_populateTlsSettings_offersTheAlpnAsWrittenCommaSeparated() {
        // TlsSettingsCheck reads alpn with alpnProtocols as well, to refuse what WebSocket and HTTPUpgrade cannot use.
        val offered = mapOf(
            " h2 , http/1.1," to listOf("h2", "http/1.1"),
            "http/1.1" to listOf("http/1.1"),
            "h3,,h2" to listOf("h3", "h2"),
            // Only a comma parts two; Xray is offered what is between as one name.
            "h2 http/1.1" to listOf("h2 http/1.1"),
            " , " to null,
            "" to null,
            null to null,
        )
        for ((alpn, protocols) in offered) {
            val streamSettings = OutboundBean.StreamSettingsBean()

            CoreOutboundBuilder.populateTlsSettings(streamSettings, echProfile(AppConfig.TLS, " ").apply { this.alpn = alpn }, null)

            assertEquals(protocols, streamSettings.tlsSettings?.alpn, "$alpn")
            assertEquals(protocols.orEmpty(), CoreOutboundBuilder.alpnProtocols(alpn), "$alpn")
        }
    }

    @Test
    fun test_applyTargetStrategy_setsOutboundTargetStrategy() {
        val outbound = OutboundBean(protocol = "vless")

        CoreOutboundBuilder.applyTargetStrategy(outbound, ProfileItem.create(EConfigType.VLESS).apply { targetStrategy = "ForceIPv6v4" })

        assertEquals("ForceIPv6v4", outbound.targetStrategy)
    }

    @Test
    fun test_applyTargetStrategy_leavesTheDefaultOut() {
        val outbound = OutboundBean(protocol = "vless", targetStrategy = "UseIP")

        CoreOutboundBuilder.applyTargetStrategy(outbound, ProfileItem.create(EConfigType.VLESS).apply { targetStrategy = AppConfig.TARGET_STRATEGY_AS_IS })
        assertNull(outbound.targetStrategy)

        CoreOutboundBuilder.applyTargetStrategy(outbound, ProfileItem.create(EConfigType.VLESS).apply { targetStrategy = " " })
        assertNull(outbound.targetStrategy)

        CoreOutboundBuilder.applyTargetStrategy(outbound, ProfileItem.create(EConfigType.VLESS))
        assertNull(outbound.targetStrategy)
    }

    @Test
    fun test_toOutboundAetherExit_carriesTheFinalMaskAndDialModeOfTheAetherProfile() {
        val plain = CoreOutboundBuilder.toOutboundAetherExit(AetherExit.PLAIN)
        assertEquals(AppConfig.TAG_EXIT_NODE, plain.tag)
        assertEquals("freedom", plain.protocol)
        assertNull(plain.mux)
        assertNull(plain.streamSettings)

        val mask = """{"tcp": [{"type": "fragment", "settings": {"packets": "tlshello"}}]}"""
        val exit = CoreOutboundBuilder.toOutboundAetherExit(AetherExit(finalMask = mask, dialMode = "code-1"))
        assertEquals(JsonUtil.parseString(mask), exit.streamSettings?.finalmask)
        assertEquals("code-1", exit.streamSettings?.sockopt?.dialMode)
        // A freedom outbound has no transport to name.
        assertNull(exit.streamSettings?.network)

        val dialOnly = CoreOutboundBuilder.toOutboundAetherExit(AetherExit(dialMode = "code-1"))
        assertEquals("code-1", dialOnly.streamSettings?.sockopt?.dialMode)
        assertNull(dialOnly.streamSettings?.finalmask)
        assertNull(dialOnly.streamSettings?.network)
    }
}
