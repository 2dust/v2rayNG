package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreOutboundBuilderXdriveTest {

    private fun profile(
        service: String?,
        secrets: String? = null,
        extra: String? = null,
        folder: String? = "folder-id"
    ) = ProfileItem.create(EConfigType.VLESS).apply {
        network = NetworkType.XDRIVE.type
        path = folder
        xdriveService = service
        xdriveSecrets = secrets
        xdriveExtra = extra
    }

    @Test
    fun googleDriveSettingsCarryServiceFolderAndSecrets() {
        val settings = CoreOutboundBuilder.buildXdriveSettings(
            profile(AppConfig.XDRIVE_SERVICE_GOOGLE_DRIVE, " id , secret ,token ", folder = " folder-id ")
        )

        assertEquals("google-drive", settings["service"].asString)
        assertEquals("folder-id", settings["remoteFolder"].asString)
        assertEquals(listOf("id", "secret", "token"), settings["secrets"].asJsonArray.map { it.asString })
        assertFalse(settings.has("template"))
    }

    @Test
    fun blankServiceDefaultsToGoogleDrive() {
        val settings = CoreOutboundBuilder.buildXdriveSettings(profile(null))

        assertEquals("google-drive", settings["service"].asString)
        assertEquals(0, settings["secrets"].asJsonArray.size())
    }

    @Test
    fun yandexDiskBecomesTemplateWithWebdavPreset() {
        val settings = CoreOutboundBuilder.buildXdriveSettings(
            profile(AppConfig.XDRIVE_SERVICE_YANDEX_DISK, "login,app-password", folder = "xdrive")
        )

        assertEquals("template", settings["service"].asString)
        val template = settings["template"].asJsonObject
        assertEquals("basic", template["auth"].asJsonObject["type"].asString)
        assertEquals("{secret0}", template["auth"].asJsonObject["username"].asString)
        assertEquals("PROPFIND", template["list"].asJsonObject["method"].asString)
        assertTrue(template["put"].asJsonObject["url"].asString.startsWith("https://webdav.yandex.ru/{folder}/"))
        assertEquals(listOf("login", "app-password"), settings["secrets"].asJsonArray.map { it.asString })
    }

    @Test
    fun yandexDiskKeepsTemplateFromExtra() {
        val settings = CoreOutboundBuilder.buildXdriveSettings(
            profile(AppConfig.XDRIVE_SERVICE_YANDEX_DISK, "a,b", """{"template":{"flatten":false}}""")
        )

        assertFalse(settings["template"].asJsonObject["flatten"].asBoolean)
    }

    @Test
    fun extraIsMergedAndTypedFieldsWin() {
        val settings = CoreOutboundBuilder.buildXdriveSettings(
            profile(
                AppConfig.XDRIVE_SERVICE_TEMPLATE,
                "s0",
                """{"service":"local","remoteFolder":"other","secrets":["x"],"segmentBytes":262144,"template":{"flatten":true}}"""
            )
        )

        assertEquals("template", settings["service"].asString)
        assertEquals("folder-id", settings["remoteFolder"].asString)
        assertEquals(listOf("s0"), settings["secrets"].asJsonArray.map { it.asString })
        assertEquals(262144, settings["segmentBytes"].asInt)
        assertTrue(settings["template"].asJsonObject["flatten"].asBoolean)
    }

    @Test
    fun parseSecretsDropsBlankEntries() {
        assertEquals(listOf("a", "b"), CoreOutboundBuilder.parseXdriveSecrets(" a,, ,b "))
        assertEquals(emptyList<String>(), CoreOutboundBuilder.parseXdriveSecrets(null))
        assertEquals(emptyList<String>(), CoreOutboundBuilder.parseXdriveSecrets(""))
    }
}
