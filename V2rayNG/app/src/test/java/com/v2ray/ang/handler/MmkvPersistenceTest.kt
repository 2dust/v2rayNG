package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MmkvPersistenceTest {
    private lateinit var stores: MmkvTestStore

    @Before fun setUp() { stores = MmkvTestStore() }
    @After fun tearDown() { stores.close() }

    @Test fun `stale profile reorder preserves additions and excludes deleted IDs`() {
        stores.main.values["SUB_SERVERS_s"] = "[\"new\",\"a\",\"c\"]"
        assertTrue(MmkvManager.reorderServerList(listOf("c", "deleted", "a", "a"), "s"))
        assertEquals(listOf("c", "a", "new"), MmkvManager.decodeServerList("s"))
    }

    @Test fun `stale subscription reorder preserves current membership`() {
        stores.main.values["SUB_IDS"] = "[\"new\",\"a\",\"c\"]"
        assertTrue(MmkvManager.reorderSubscriptions(listOf("c", "deleted", "a")))
        assertEquals(listOf("c", "a", "new"), MmkvManager.decodeSubsList())
    }

    @Test fun `empty reorder cannot remove entries or create deleted entries`() {
        stores.main.values["SUB_IDS"] = "[\"a\"]"
        assertTrue(MmkvManager.reorderSubscriptions(emptyList()))
        assertEquals(listOf("a"), MmkvManager.decodeSubsList())
        stores.main.values["SUB_IDS"] = "[]"
        assertTrue(MmkvManager.reorderSubscriptions(listOf("deleted")))
        assertTrue(MmkvManager.decodeSubsList().isEmpty())
    }

    @Test fun `failed or unreadable reorder leaves the index untouched`() {
        stores.main.values["SUB_IDS"] = "[\"a\",\"b\"]"
        stores.main.failWrite = { true }
        assertFalse(MmkvManager.reorderSubscriptions(listOf("b", "a")))
        assertEquals(listOf("a", "b"), MmkvManager.decodeSubsList())
        stores.main.failWrite = { false }
        stores.main.values["SUB_IDS"] = "invalid"
        assertFalse(MmkvManager.reorderSubscriptions(listOf("b", "a")))
        assertEquals("invalid", stores.main.values["SUB_IDS"])
    }

    @Test fun `last subscription stays deleted when metadata cleanup fails`() {
        val item = SubscriptionItem(remarks = "s")
        assertEquals("s", MmkvManager.encodeSubscription("s", item))
        stores.subscriptions.failRemove = true
        assertTrue(MmkvManager.removeSubscription("s"))
        assertTrue(stores.subscriptions.values.containsKey("s"))
        assertTrue(MmkvManager.decodeSubscriptions().isEmpty())
        assertTrue(MmkvManager.migrateSubscriptionIndex())
        assertTrue(MmkvManager.decodeSubscriptions().isEmpty())
        assertFalse(MmkvManager.updateSubscription("s", item, item.copy(enabled = false)))
    }

    @Test fun `legacy subscriptions are recovered only by explicit migration`() {
        stores.subscriptions.values["s"] = JsonUtil.toJson(SubscriptionItem(remarks = "legacy"))
        assertTrue(MmkvManager.decodeSubscriptions().isEmpty())
        assertFalse(stores.main.values.containsKey("SUB_IDS"))
        stores.main.failWrite = { true }
        assertFalse(MmkvManager.migrateSubscriptionIndex())
        stores.main.failWrite = { false }
        assertTrue(MmkvManager.migrateSubscriptionIndex())
        assertEquals(listOf("s"), MmkvManager.decodeSubsList())
    }

    @Test fun `settings edits and older refreshes preserve the latest timestamp`() {
        val expected = SubscriptionItem(lastUpdated = 100)
        MmkvManager.encodeSubscription("s", expected.copy(lastUpdated = 200))
        assertTrue(MmkvManager.updateSubscription("s", expected, expected.copy(enabled = false)))
        val edited = MmkvManager.decodeSubscription("s")!!
        assertFalse(edited.enabled)
        assertEquals(200L, edited.lastUpdated)
        assertTrue(MmkvManager.updateSubscription("s", edited, edited, updatedAt = 300))
        assertEquals(300L, MmkvManager.decodeSubscription("s")!!.lastUpdated)
        assertFalse(MmkvManager.updateSubscription("s", edited, edited, updatedAt = 250))
        assertEquals(300L, MmkvManager.decodeSubscription("s")!!.lastUpdated)
    }

    @Test fun `stale profile batch does not overwrite a completed refresh`() {
        val expected = SubscriptionItem(lastUpdated = 100)
        MmkvManager.encodeSubscription("s", expected.copy(lastUpdated = 300))
        val profile = ProfileItem.create(EConfigType.VLESS).apply { subscriptionId = "s" }
        assertThrows(SubscriptionUpdateAbortedException::class.java) {
            MmkvManager.saveServerProfiles(mapOf("p" to profile), emptyMap(), "s", true,
                SubscriptionUpdateCommit(expected, expected.copy(lastUpdated = 200)))
        }
        assertEquals(300L, MmkvManager.decodeSubscription("s")!!.lastUpdated)
        assertTrue(MmkvManager.decodeServerList("s").isEmpty())
        assertNull(MmkvManager.decodeServerConfig("p"))
    }

    @Test fun `fresh timestamp and batch updates accept a backward clock correction`() {
        val future = SubscriptionItem(lastUpdated = 20000)
        MmkvManager.encodeSubscription("s", future)
        assertTrue(MmkvManager.updateSubscription("s", future, future, updatedAt = 10000))
        val expected = MmkvManager.decodeSubscription("s")!!
        assertEquals(10000L, expected.lastUpdated)
        val profile = ProfileItem.create(EConfigType.VLESS).apply { subscriptionId = "s" }
        MmkvManager.saveServerProfiles(mapOf("p" to profile), emptyMap(), "s", true,
            SubscriptionUpdateCommit(expected, expected.copy(lastUpdated = 5000)))
        assertEquals(5000L, MmkvManager.decodeSubscription("s")!!.lastUpdated)
        assertEquals(listOf("p"), MmkvManager.decodeServerList("s"))
        assertFalse(MmkvManager.updateSubscription("s", expected, expected, updatedAt = 15000))
    }

    @Test fun `settings edits never replace refresh metadata from their snapshot`() {
        val expected = SubscriptionItem(lastUpdated = 100)
        MmkvManager.encodeSubscription("s", expected)
        assertTrue(MmkvManager.updateSubscription("s", expected, expected.copy(remarks = "edited", lastUpdated = 900)))
        assertEquals(100L, MmkvManager.decodeSubscription("s")!!.lastUpdated)
    }

    @Test fun `unreadable migration payload remains pending until repaired`() {
        MmkvManager.encodeSubscription("s", SubscriptionItem())
        stores.main.values["SUB_SERVERS_s"] = "[\"p\",\"missing\"]"
        stores.main.values["ANG_CONFIGS"] = "[\"p\",\"missing\"]"
        for (unreadable in listOf("invalid-json", 123)) {
            stores.profiles.values["p"] = unreadable
            migratePins()
            assertFalse(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
            assertFalse(migrateServerIndex())
            assertFalse(MmkvManager.decodeSettingsBool("server_list_to_subscriptions_migrated", false))
        }
        stores.profiles.values["p"] = JsonUtil.toJson(ProfileItem.create(EConfigType.HYSTERIA2).apply {
            subscriptionId = "s"
            pinSHA256 = "old-pin"
        })
        assertTrue(migrateServerIndex())
        migratePins()
        assertTrue(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
        assertEquals("old-pin", MmkvManager.decodeServerConfig("p")!!.pinnedCA256)
    }

    @Test fun `unreadable migration indexes are not treated as empty`() {
        for (key in listOf("SUB_IDS", "SUB_SERVERS_s")) {
            stores.main.values["SUB_IDS"] = "[\"s\"]"
            stores.main.values["SUB_SERVERS_s"] = "[]"
            stores.main.values[key] = "invalid-json"
            migratePins()
            assertFalse(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
        }
        stores.main.values["SUB_IDS"] = "[]"
        stores.main.values["ANG_CONFIGS"] = 123
        assertFalse(migrateServerIndex())
        assertFalse(MmkvManager.decodeSettingsBool("server_list_to_subscriptions_migrated", false))
    }

    @Test fun `absent migration records do not prevent completion`() {
        stores.main.values["SUB_IDS"] = "[\"s\"]"
        stores.main.values["SUB_SERVERS_s"] = "[\"missing\"]"
        stores.main.values["ANG_CONFIGS"] = "[\"missing\"]"
        assertTrue(migrateServerIndex())
        migratePins()
        assertTrue(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
        assertTrue(MmkvManager.readProfilesForMigration().isEmpty())
    }

    @Test fun `failed pin migration is retried before its completion flag is set`() {
        val profile = ProfileItem.create(EConfigType.HYSTERIA2).apply {
            subscriptionId = "s"
            pinSHA256 = "old-pin"
        }
        MmkvManager.encodeSubscription("s", SubscriptionItem())
        MmkvManager.encodeServerConfig("p", profile)
        stores.profiles.failWrite = { true }
        migratePins()
        assertFalse(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
        assertEquals("old-pin", MmkvManager.decodeServerConfig("p")!!.pinSHA256)
        stores.profiles.failWrite = { false }
        migratePins()
        assertTrue(MmkvManager.decodeSettingsBool("hysteria2_pin_sha256_migrated", false))
        assertEquals("old-pin", MmkvManager.decodeServerConfig("p")!!.pinnedCA256)
        assertNull(MmkvManager.decodeServerConfig("p")!!.pinSHA256)
    }

    private fun migratePins() {
        SettingsManager::class.java.getDeclaredMethod("migrateHysteria2PinSHA256")
            .apply { isAccessible = true }.invoke(SettingsManager)
    }

    @Test fun `legacy profile index migration retries without hiding newer imports`() {
        stores.main.values["SUB_IDS"] = "[\"s\"]"
        stores.main.values["ANG_CONFIGS"] = "[\"legacy\"]"
        stores.subscriptions.values["s"] = JsonUtil.toJson(SubscriptionItem())
        stores.profiles.values["legacy"] = JsonUtil.toJson(
            ProfileItem.create(EConfigType.VLESS).apply { subscriptionId = "s" })
        stores.main.failWrite = { it == "SUB_SERVERS_s" }
        assertFalse(migrateServerIndex())
        assertFalse(MmkvManager.decodeSettingsBool("server_list_to_subscriptions_migrated", false))
        stores.main.values["SUB_SERVERS_s"] = "[\"new\"]"
        stores.main.failWrite = { false }
        assertTrue(migrateServerIndex())
        assertEquals(listOf("new", "legacy"), MmkvManager.decodeServerList("s"))
        assertTrue(MmkvManager.decodeSettingsBool("server_list_to_subscriptions_migrated", false))
    }

    private fun migrateServerIndex(): Boolean =
        SettingsManager::class.java.getDeclaredMethod("migrateServerListToSubscriptions")
            .apply { isAccessible = true }.invoke(SettingsManager) as Boolean
}
