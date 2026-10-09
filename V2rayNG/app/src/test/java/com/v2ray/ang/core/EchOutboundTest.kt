package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for EchOutbound: the checks made when a profile is saved, and how a configuration is
 * serialized with the ECH outbounds that its profiles carry.
 */
class EchOutboundTest {

    private fun profile(
        echOutbound: String?,
        echConfigList: String? = ECH_CONFIG_LIST,
        configType: EConfigType = EConfigType.VLESS,
        security: String? = AppConfig.TLS,
    ): ProfileItem =
        ProfileItem.create(configType).apply {
            this.security = security
            this.echConfigList = echConfigList
            this.echOutbound = echOutbound
        }

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    /** A TLS proxy outbound as CoreOutboundBuilder builds it, carrying [echOutbound]. */
    private fun tlsOutbound(tag: String, echOutbound: String?, echConfigList: String? = ECH_CONFIG_LIST): OutboundBean =
        OutboundBean(
            tag = tag,
            protocol = "vless",
            streamSettings = OutboundBean.StreamSettingsBean(
                security = "tls",
                tlsSettings = OutboundBean.StreamSettingsBean.TlsSettingsBean(
                    echConfigList = echConfigList,
                    echOutbound = echOutbound,
                ),
            ),
        )

    /** A configuration with [outbounds], then the direct outbound. */
    private fun config(vararg outbounds: OutboundBean): V2rayConfig =
        V2rayConfig(
            log = V2rayConfig.LogBean(),
            inbounds = arrayListOf(),
            outbounds = arrayListOf(*outbounds, OutboundBean(tag = AppConfig.TAG_DIRECT, protocol = "freedom")),
            routing = V2rayConfig.RoutingBean(domainStrategy = "AsIs", rules = arrayListOf()),
        )

    private fun dialerProxyOf(outbound: OutboundBean): String? =
        outbound.streamSettings?.tlsSettings?.echSockopt?.dialerProxy

    private fun contentOf(result: EchOutbound.Result): String = (result as EchOutbound.Result.Done).content

    private fun outboundsOf(result: EchOutbound.Result): JsonArray = json(contentOf(result)).getAsJsonArray("outbounds")

    private fun tagsOf(outbounds: JsonArray): List<String> = outbounds.map { it.asJsonObject.get("tag").asString }

    @Test
    fun validate_acceptsAnEmptyOrValidEchOutbound() {
        assertNull(EchOutbound.validate(profile(null)))
        assertNull(EchOutbound.validate(profile("  ")))
        assertNull(EchOutbound.validate(profile("""{"tag": "ech-out", "protocol": "freedom"}""")))
        assertNull(EchOutbound.validate(profile("""{"tag": "ech-proxy"}""")))
        assertNull(EchOutbound.validate(profile("""{"tag": "Proxy"}""")))
    }

    @Test
    fun validate_rejectsWhatIsNotAJsonObject() {
        for (json in listOf("freedom", "[{\"tag\": \"ech-out\"}]", "{\"tag\": \"ech-out\"", "{\"tag\": \"ech-out\"} x")) {
            assertEquals(EchOutbound.Error.INVALID_JSON, EchOutbound.validate(profile(json)), json)
        }
    }

    @Test
    fun validate_needsAnEchConfigList() {
        assertEquals(
            EchOutbound.Error.NEEDS_ECH_CONFIG_LIST,
            EchOutbound.validate(profile("""{"tag": "ech-out"}""", echConfigList = "")),
        )
    }

    @Test
    fun validate_rejectsAMissingOrTakenTag() {
        val jsons = listOf(
            """{"protocol": "freedom"}""",
            """{"tag": ""}""",
            """{"tag": " "}""",
            """{"tag": 1}""",
            """{"tag": "direct"}""",
            """{"tag": "block"}""",
            """{"tag": "proxy"}""",
            """{"tag": "proxy-ech"}""",
        )
        for (json in jsons) {
            assertEquals(EchOutbound.Error.INVALID_TAG, EchOutbound.validate(profile(json)), json)
        }
    }

    @Test
    fun validate_ignoresAnEchOutboundWhereItDoesNotApply() {
        // The editor hides it there and no configuration uses it, so an imported value must not block saving.
        val invalid = """{"tag": "proxy"}"""
        assertNull(EchOutbound.validate(profile(invalid, security = AppConfig.REALITY)))
        assertNull(EchOutbound.validate(profile(invalid, security = null)))
        for (configType in listOf(EConfigType.SOCKS, EConfigType.HTTP, EConfigType.WIREGUARD, EConfigType.AETHER)) {
            assertNull(EchOutbound.validate(profile(invalid, configType = configType)), configType.name)
        }
        // Trojan uses it under TLS, and Hysteria2 always runs over TLS: a blank security is saved as TLS.
        assertEquals(EchOutbound.Error.INVALID_TAG, EchOutbound.validate(profile(invalid, configType = EConfigType.TROJAN)))
        assertEquals(
            EchOutbound.Error.INVALID_TAG,
            EchOutbound.validate(profile(invalid, configType = EConfigType.HYSTERIA2, security = null)),
        )
    }

    @Test
    fun serialize_sharesAnEchOutboundAndNumbersADifferentOneUnderTheSameTag() {
        val shared = """{"tag": "ech", "protocol": "freedom"}"""
        val outbounds = arrayOf(
            tlsOutbound("proxy-1", shared),
            tlsOutbound("proxy-2", """{"tag": "ech", "protocol": "blackhole"}"""),
            tlsOutbound("proxy-3", shared),
            tlsOutbound("proxy-4", null),
        )

        val appended = outboundsOf(EchOutbound.serialize(config(*outbounds)))

        assertEquals(listOf("ech", "ech-2", "ech", null), outbounds.map { dialerProxyOf(it) })
        assertEquals(listOf("proxy-1", "proxy-2", "proxy-3", "proxy-4", "direct", "ech", "ech-2"), tagsOf(appended))
        assertEquals("blackhole", appended.last().asJsonObject.get("protocol").asString)
    }

    @Test
    fun serialize_numbersClearOfTheTagsOfOtherOutbounds() {
        // "ech-2" is another outbound of the configuration, so the second ECH outbound takes "ech-3".
        val outbounds = arrayOf(
            tlsOutbound("proxy-1", """{"tag": "ech", "protocol": "freedom"}"""),
            tlsOutbound("proxy-2", """{"tag": "ech", "protocol": "blackhole"}"""),
            OutboundBean(tag = "ech-2", protocol = "freedom"),
        )

        val appended = outboundsOf(EchOutbound.serialize(config(*outbounds)))

        assertEquals(listOf("ech", "ech-3", null), outbounds.map { dialerProxyOf(it) })
        assertEquals(listOf("proxy-1", "proxy-2", "ech-2", "direct", "ech", "ech-3"), tagsOf(appended))
    }

    @Test
    fun serialize_appendsTheEchOutboundLastAsWritten() {
        // tcpCongestion is in no bean, so it only gets through when the outbound is appended as written.
        val echOutbound = """{"tag": "ech-out", "protocol": "freedom", "streamSettings": {"sockopt": {"tcpCongestion": "bbr"}}}"""

        val content = contentOf(EchOutbound.serialize(config(tlsOutbound("proxy", echOutbound))))

        assertEquals(json(echOutbound), json(content).getAsJsonArray("outbounds").last())
        assertTrue(content.contains("\"tcpCongestion\": \"bbr\""))
    }

    @Test
    fun serialize_leavesTheAttachedTextOutOfTheConfiguration() {
        val content = contentOf(EchOutbound.serialize(config(tlsOutbound("proxy", """{"tag": "ech", "protocol": "freedom"}"""))))
        val tlsSettings = json(content).getAsJsonArray("outbounds")[0].asJsonObject
            .getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")

        assertNull(tlsSettings.get("echOutbound"))
        assertEquals("ech", tlsSettings.getAsJsonObject("echSockopt").get("dialerProxy").asString)
    }

    @Test
    fun serialize_leavesAConfigurationWithoutEchOutboundsAsBefore() {
        val config = config(tlsOutbound("proxy", null))
        val before = JsonUtil.toJsonPretty(config)!!

        assertEquals(EchOutbound.Result.Done(before), EchOutbound.serialize(config))
    }

    @Test
    fun serialize_reportsATagThatAnotherOutboundHas() {
        val result = EchOutbound.serialize(
            config(
                tlsOutbound("proxy", """{"tag": "dns-out", "protocol": "freedom"}"""),
                OutboundBean(tag = "dns-out", protocol = "dns"),
            )
        )

        assertEquals(EchOutbound.Result.TagConflict("dns-out"), result)
    }

    @Test
    fun serialize_failsOnAnEchOutboundTheEditorNeverChecked() {
        // An imported ECH outbound is checked here, rather than letting the ECH config query go direct.
        assertEquals(
            EchOutbound.Result.Invalid(EchOutbound.Error.INVALID_TAG),
            EchOutbound.serialize(config(tlsOutbound("proxy", """{"tag": "proxy-ech", "protocol": "freedom"}"""))),
        )
        assertEquals(
            EchOutbound.Result.Invalid(EchOutbound.Error.INVALID_JSON),
            EchOutbound.serialize(config(tlsOutbound("proxy", "freedom"))),
        )
        assertEquals(
            EchOutbound.Result.Invalid(EchOutbound.Error.NEEDS_ECH_CONFIG_LIST),
            EchOutbound.serialize(config(tlsOutbound("proxy", """{"tag": "ech", "protocol": "freedom"}""", echConfigList = null))),
        )
    }

    private companion object {
        const val ECH_CONFIG_LIST = "cloudflare-ech.com+https://1.1.1.1/dns-query"
    }
}
