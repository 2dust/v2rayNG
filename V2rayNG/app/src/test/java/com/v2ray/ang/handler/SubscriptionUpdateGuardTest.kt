package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
