package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MainServerRowModelsTest {
    private val endpoint = """{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443}]}}"""
    private val single = """{"outbounds":[$endpoint]}"""
    private val multiple = """{"outbounds":[$endpoint,$endpoint]}"""
    private val stored = ServersCache(
        guid = "stable-guid",
        profile = ProfileItem.create(EConfigType.CUSTOM).copy(
            remarks = "User name", subscriptionId = "subscription", description = "stale metadata",
            server = "old.example", serverPort = "1234",
        ),
        testDelayMillis = 27,
    )

    private fun row(server: ServersCache, multipleText: String = "Multiple Servers") =
        buildServerRowUiModel(server, "Group", multipleText)

    @Test
    fun oldStoredProfilesRefreshMetadataAndPreserveTheirIdentity() {
        val oldJson = """{"configVersion":4,"configType":"CUSTOM","remarks":"User name","subscriptionId":"subscription"}"""
        val profile = JsonUtil.fromJson(oldJson, ProfileItem::class.java)!!
        val refreshed = stored.copy(profile = profile).withCustomMetadata(single)
        assertEquals("example.com", refreshed.profile.server)
        assertEquals("443", refreshed.profile.serverPort)
        assertEquals("example.*** : 443", row(refreshed).statistics)
        assertEquals("stable-guid", refreshed.guid)
        assertEquals("User name", refreshed.profile.remarks)
        assertEquals("subscription", refreshed.profile.subscriptionId)
        assertEquals(27L, refreshed.testDelayMillis)
        assertNull(profile.server)
    }

    @Test
    fun multipleServersUseTheCurrentLocalizedLabelAndClearTheOldEndpoint() {
        val refreshed = stored.withCustomMetadata(multiple)
        assertEquals(2, refreshed.customServerCount)
        assertNull(refreshed.profile.server)
        assertNull(refreshed.profile.serverPort)
        assertEquals("Multiple Servers", row(refreshed).statistics)
        assertEquals("Несколько серверов", row(refreshed, "Несколько серверов").statistics)
        assertEquals("stale metadata", stored.profile.description)
    }

    @Test
    fun editsFromMultipleToSingleAndEmptyRecomputePresentation() {
        val refreshed = stored.withCustomMetadata(multiple).withCustomMetadata(single)
        assertEquals(1, refreshed.customServerCount)
        assertEquals("example.*** : 443", row(refreshed).statistics)
        assertEquals("", row(refreshed.withCustomMetadata("{}")).statistics)
    }

    @Test
    fun absentOrInvalidEndpointsDoNotReuseStaleDescriptions() {
        for (raw in listOf(null, "{}", """{"outbounds":[{"protocol":"vless","settings":{"address":"bad host"}}]}""")) {
            assertEquals("", row(stored.withCustomMetadata(raw)).statistics)
        }
    }

    @Test
    fun malformedStoredJsonLeavesTheRowUsable() {
        val level = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
        val previous = level.getInt(null)
        level.setInt(null, Int.MAX_VALUE)
        try {
            val refreshed = stored.withCustomMetadata("{")
            assertEquals("User name", row(refreshed).remarks)
            assertEquals("", row(refreshed).statistics)
        } finally {
            level.setInt(null, previous)
        }
    }

    @Test
    fun normalProfilesKeepTheirExistingDescription() {
        val normal = stored.copy(profile = stored.profile.copy(configType = EConfigType.VLESS))
        assertSame(normal, normal.withCustomMetadata(multiple))
        assertEquals("stale metadata", row(normal).statistics)
    }
}
