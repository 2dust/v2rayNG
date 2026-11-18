package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionUpdateGuardTest {

    private val expected = SubscriptionItem(
        remarks = "Example",
        url = "https://example.com/subscription",
        lastUpdated = 100,
        userAgent = "v2rayNG",
    )

    @Test
    fun `rejects an update after its subscription was deleted`() {
        assertNull(prepare(current = null))
    }

    @Test
    fun `rejects an update after the subscription was unpublished`() {
        assertNull(prepare(isIndexed = false))
    }

    @Test
    fun `rejects an update after subscription settings changed`() {
        val edited = expected.copy(url = "https://example.com/edited")

        assertNull(prepare(current = edited))
    }

    @Test
    fun `settings edit preserves current timestamp instead of the replacement snapshot`() {
        val current = expected.copy(lastUpdated = 200)
        val replacement = expected.copy(enabled = false, lastUpdated = 900)

        assertEquals(replacement.copy(lastUpdated = 200), prepare(current = current, replacement = replacement))
        assertEquals(200L, current.lastUpdated)
        assertEquals(900L, replacement.lastUpdated)
    }

    @Test
    fun `settings edit preserves usage info from a newer refresh`() {
        val usageInfo = SubscriptionUsageInfo(download = 42, total = 100)
        val current = expected.copy(lastUpdated = 200, usageInfo = usageInfo)
        val replacement = expected.copy(enabled = false, usageInfo = SubscriptionUsageInfo(download = 1))

        assertEquals(
            replacement.copy(lastUpdated = 200, usageInfo = usageInfo),
            prepare(current = current, replacement = replacement),
        )
        assertNull(expected.usageInfo)
        assertEquals(1L, replacement.usageInfo!!.download)
    }

    @Test
    fun `settings edit cannot restore usage info cleared by a refresh`() {
        val old = expected.copy(usageInfo = SubscriptionUsageInfo(download = 42))
        val current = expected.copy(lastUpdated = 200)

        assertEquals(
            current.copy(enabled = false),
            SubscriptionUpdateGuard.prepare(true, current, old, old.copy(enabled = false)),
        )
    }

    @Test
    fun `refresh replaces or clears usage info with the current response`() {
        val usageInfo = SubscriptionUsageInfo(download = 42, total = 100)
        val replacement = expected.copy(usageInfo = usageInfo)

        assertEquals(usageInfo, prepare(replacement = replacement, updatedAt = 200)!!.usageInfo)
        assertNull(prepare(updatedAt = 200)!!.usageInfo)
    }

    @Test
    fun `refresh rejects changed usage info even when its timestamp is unchanged`() {
        assertNull(prepare(current = expected.copy(usageInfo = SubscriptionUsageInfo(download = 42)), updatedAt = 200))
    }

    @Test
    fun `refresh uses its explicit timestamp rather than replacement metadata`() {
        val replacement = expected.copy(remarks = "Refreshed", lastUpdated = 999)

        assertEquals(replacement.copy(lastUpdated = 200), prepare(replacement = replacement, updatedAt = 200))
    }

    @Test
    fun `fresh refresh accepts backward clock corrections`() {
        assertEquals(expected.copy(lastUpdated = 50), prepare(updatedAt = 50))
    }

    @Test
    fun `refresh rejects a changed timestamp even if its proposed timestamp is later`() {
        assertNull(prepare(current = expected.copy(lastUpdated = 200), updatedAt = 300))
    }

    @Test
    fun `refresh rejects missing unpublished or edited subscriptions`() {
        assertNull(prepare(current = null, updatedAt = 200))
        assertNull(prepare(isIndexed = false, updatedAt = 200))
        assertNull(prepare(current = expected.copy(enabled = false), updatedAt = 200))
    }

    private fun prepare(
        isIndexed: Boolean = true,
        current: SubscriptionItem? = expected,
        replacement: SubscriptionItem = expected,
        updatedAt: Long? = null,
    ): SubscriptionItem? {
        return SubscriptionUpdateGuard.prepare(
            isIndexed = isIndexed,
            current = current,
            expected = expected,
            replacement = replacement,
            updatedAt = updatedAt,
        )
    }
}
