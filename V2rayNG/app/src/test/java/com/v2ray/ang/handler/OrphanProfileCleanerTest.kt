package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrphanProfileCleanerTest {

    @Test
    fun `reports no work when profile storage is empty`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = emptyList(),
            indexedServersBySubscription = emptyMap(),
            selectedServer = null,
        )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `uses raw group indexes when subscription metadata is missing`() {
        val profiles = listOf(
            StoredProfileReference("live", "group-a"),
            StoredProfileReference("orphan", "group-a"),
        )

        val result = OrphanProfileCleaner.findOrphans(
            profiles = profiles,
            indexedServersBySubscription = mapOf("group-a" to setOf("live")),
            selectedServer = null,
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps profiles whose group index is missing`() {
        val profiles = listOf(
            StoredProfileReference("known-orphan", "group-a"),
            StoredProfileReference("unknown", "missing-group"),
        )

        val result = OrphanProfileCleaner.findOrphans(
            profiles = profiles,
            indexedServersBySubscription = mapOf("group-a" to emptySet()),
            selectedServer = null,
        )

        assertEquals(setOf("known-orphan"), result)
    }

    @Test
    fun `aborts cleanup when any raw group index is unreadable`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = listOf(StoredProfileReference("candidate", "group-a")),
            indexedServersBySubscription = mapOf(
                "group-a" to emptySet(),
                "corrupt-group" to null,
            ),
            selectedServer = null,
        )

        assertNull(result)
    }

    @Test
    fun `aborts cleanup when no raw group indexes survive`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = listOf(StoredProfileReference("candidate", "group-a")),
            indexedServersBySubscription = emptyMap(),
            selectedServer = null,
        )

        assertNull(result)
    }

    @Test
    fun `keeps selected and undecodable profiles`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = listOf(
                StoredProfileReference("selected", "group-a"),
                StoredProfileReference("undecodable", null),
                StoredProfileReference("orphan", "group-a"),
            ),
            indexedServersBySubscription = mapOf("group-a" to emptySet()),
            selectedServer = "selected",
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `normalizes ungrouped profiles to the default group`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = listOf(
                StoredProfileReference("live", ""),
                StoredProfileReference("orphan", ""),
            ),
            indexedServersBySubscription = mapOf(
                DEFAULT_SUBSCRIPTION_ID to setOf("live"),
            ),
            selectedServer = null,
        )

        assertEquals(setOf("orphan"), result)
    }

    @Test
    fun `keeps a profile indexed by a different group`() {
        val result = OrphanProfileCleaner.findOrphans(
            profiles = listOf(StoredProfileReference("mismatched", "group-a")),
            indexedServersBySubscription = mapOf(
                "group-a" to emptySet(),
                "group-b" to setOf("mismatched"),
            ),
            selectedServer = null,
        )

        assertEquals(emptySet<String>(), result)
    }
}
