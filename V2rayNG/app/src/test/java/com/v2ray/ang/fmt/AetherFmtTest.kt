package com.v2ray.ang.fmt

import com.google.gson.Gson
import com.v2ray.ang.dto.entities.AetherSettings
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64

class AetherFmtTest {
    @Test
    fun profileUriRoundTripPreservesSettingsAndUnicodeRemarks() {
        val settings = AetherSettings(endpoint = "1.1.1.1:443", scanMode = "verified", fragment = false)
        val profile = ProfileItem.create(EConfigType.AETHER).copy(remarks = "کانفیگ Aether", aetherSettings = settings)
        val uri = EConfigType.AETHER.protocolScheme + AetherFmt.toUri(profile)
        val restored = AetherFmt.parse(uri)
        assertEquals(EConfigType.AETHER, restored?.configType)
        assertEquals(profile.remarks, restored?.remarks)
        assertEquals(settings, restored?.aetherSettings)
        assertFalse(uri.contains("identity.toml"))
    }

    @Test
    fun invalidOrUnrelatedInputIsNotImported() {
        listOf("", "aether://", "aether://not valid base64", "vmess://abc").forEach { assertNull(AetherFmt.parse(it)) }
    }

    @Test
    fun precedingReleasedProfileFormatStillLoadsWithoutAetherFields() {
        val profile = Gson().fromJson(
            """{"configVersion":4,"configType":"VLESS","subscriptionId":"old-group","remarks":"old","server":"192.0.2.1","serverPort":"443","password":"test-id"}""",
            ProfileItem::class.java,
        )
        assertEquals(EConfigType.VLESS, profile.configType)
        assertEquals("old-group", profile.subscriptionId)
        assertEquals("test-id", profile.password)
        assertNull(profile.aetherSettings)
        assertEquals(5, EConfigType.VLESS.value)
        assertEquals(11, EConfigType.AETHER.value)
    }

    @Test
    fun precedingAetherLinksRetainTheLegacyPortWithoutUsingItForStartup() {
        val precedingJson = """{"version":1,"remarks":"old Aether","settings":{"protocol":"masque","carrier":"h2","scanMode":"balanced","ipVersion":"v4","noise":"firewall","endpoint":"","dns":"1.1.1.1,1.0.0.1","fragment":true,"fragmentSize":"16-32","fragmentDelay":"2-10","socksPort":"10808"}}"""
        val restored = AetherFmt.parse(EConfigType.AETHER.protocolScheme + Base64.UrlSafe.encode(precedingJson.toByteArray()))
        assertEquals("10808", restored?.aetherSettings?.socksPort)
        val arguments = restored!!.aetherSettings!!.arguments("identity.toml", 45123)
        assertEquals("127.0.0.1:45123", arguments[arguments.indexOf("--bind") + 1])
    }
}
