package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class CoreOutboundBuilderXdriveTest {

    private fun xdrive(
        configType: EConfigType = EConfigType.VLESS,
        encryption: String? = "mlkem768x25519plus.native.0rtt.key",
        flow: String? = null,
        service: String? = "google-drive",
        folder: String? = "/tunnel",
        secrets: String? = "id,secret,refresh",
        extra: String? = null,
    ) = ProfileItem.create(configType).apply {
        network = NetworkType.XDRIVE.type
        method = encryption
        this.flow = flow
        xdriveService = service
        path = folder
        xdriveSecrets = secrets
        xdriveExtra = extra
    }

    @Test
    fun buildsSettingsFromDedicatedFields() {
        val settings = requireNotNull(CoreOutboundBuilder.buildXdriveSettings(xdrive()))

        assertEquals("google-drive", settings.service)
        assertEquals("/tunnel", settings.remoteFolder)
        assertEquals(listOf("id", "secret", "refresh"), settings.secrets)
        assertNull(settings.s3)
        assertNull(settings.template)
    }

    @Test
    fun extraJsonFillsRemainingFieldsAndDedicatedFieldsWin() {
        val extra = """{"service":"local","remoteFolder":"/x","secrets":["e"],"segmentBytes":524288,
            |"concurrency":64,"s3":{"endpoint":"https://s3.example","region":"auto","bucket":"b","pathStyle":true},
            |"template":{"put":{"method":"PUT"}},"xmux":{"maxConnections":"2-4"}}""".trimMargin()
        val settings = requireNotNull(
            CoreOutboundBuilder.buildXdriveSettings(xdrive(service = "s3", secrets = "AK, SK", extra = extra))
        )

        assertEquals("s3", settings.service)
        assertEquals("/tunnel", settings.remoteFolder)
        assertEquals(listOf("AK", "SK"), settings.secrets)
        assertEquals(524288, settings.segmentBytes)
        assertEquals(64, settings.concurrency)
        assertEquals("b", settings.s3?.bucket)
        assertEquals(true, settings.s3?.pathStyle)
        assertEquals("PUT", settings.template?.getAsJsonObject("put")?.get("method")?.asString)
        assertEquals("2-4", settings.xmux?.maxConnections)
    }

    @Test
    fun blankFieldsKeepExtraValuesAndServiceDefaultsToGoogleDrive() {
        val keepExtra = requireNotNull(
            CoreOutboundBuilder.buildXdriveSettings(
                xdrive(service = " ", folder = "", secrets = " , ", extra = """{"service":"local","remoteFolder":"/x","secrets":["e"]}""")
            )
        )
        assertEquals("local", keepExtra.service)
        assertEquals("/x", keepExtra.remoteFolder)
        assertEquals(listOf("e"), keepExtra.secrets)

        val empty = requireNotNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(service = null, folder = null, secrets = null)))
        assertEquals("google-drive", empty.service)
        assertNull(empty.remoteFolder)
        assertNull(empty.secrets)

        val legacy = requireNotNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(service = "Google Drive")))
        assertEquals("google-drive", legacy.service)
    }

    @Test
    fun invalidExtraIsRejected() {
        assertNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(extra = "{")))
        assertNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(extra = "[1]")))
        assertNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(extra = """{"segmentBytes":"big"}""")))
    }

    @Test
    fun populateTransportSettingsWritesXdriveSettingsWithoutSni() {
        val stream = OutboundBean.StreamSettingsBean()
        val sni = CoreOutboundBuilder.populateTransportSettings(stream, xdrive().apply { host = "ignored.example" })

        assertNull(sni)
        assertEquals("xdrive", stream.network)
        assertNull(stream.xhttpSettings)
        val json = JsonParser.parseString(JsonUtil.toJson(stream)).asJsonObject.getAsJsonObject("xdriveSettings")
        assertEquals("google-drive", json["service"].asString)
        assertEquals("/tunnel", json["remoteFolder"].asString)
        assertEquals(3, json.getAsJsonArray("secrets").size())
        assertTrue(!json.has("s3"))
    }

    @Test
    fun populateTransportSettingsLeavesSettingsEmptyForInvalidExtra() {
        val stream = OutboundBean.StreamSettingsBean()
        CoreOutboundBuilder.populateTransportSettings(stream, xdrive(extra = "not json"))

        assertEquals("xdrive", stream.network)
        assertNull(stream.xdriveSettings)
    }

    @Test
    fun xdriveMuxIsAlwaysEnabledWithAcceptedValues() {
        assertEquals(
            OutboundBean.MuxBean(enabled = true, concurrency = 16, xudpConcurrency = 4, xudpProxyUDP443 = "allow"),
            CoreOutboundBuilder.buildXdriveMux("16", "4", "allow")
        )
        assertEquals(
            OutboundBean.MuxBean(enabled = true, concurrency = 8, xudpConcurrency = 8, xudpProxyUDP443 = "reject"),
            CoreOutboundBuilder.buildXdriveMux("-1", "-1", "skip")
        )
        assertEquals(
            OutboundBean.MuxBean(enabled = true, concurrency = 8, xudpConcurrency = 0, xudpProxyUDP443 = "reject"),
            CoreOutboundBuilder.buildXdriveMux(null, "0", null)
        )
        assertEquals(
            OutboundBean.MuxBean(enabled = true, concurrency = 8, xudpConcurrency = 8, xudpProxyUDP443 = "reject"),
            CoreOutboundBuilder.buildXdriveMux("abc", "", "")
        )
    }

    @Test
    fun parseSecretsTrimsAndDropsEmptyEntries() {
        assertEquals(listOf("a", "b"), CoreOutboundBuilder.buildXdriveSettings(xdrive(secrets = " a , ,b,"))?.secrets)
        assertNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(secrets = " , "))?.secrets)
        assertNull(CoreOutboundBuilder.buildXdriveSettings(xdrive(secrets = null))?.secrets)
    }

    companion object {
        /** Invalid-JSON branches log through LogUtil; silence it so no Android or MMKV call runs. */
        @BeforeAll
        @JvmStatic
        fun silenceLogs() {
            LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }.set(null, Int.MAX_VALUE)
        }
    }
}
