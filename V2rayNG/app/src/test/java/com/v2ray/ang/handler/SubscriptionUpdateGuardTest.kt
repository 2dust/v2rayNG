package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse(canCommit(current = null))
    }

    @Test
    fun `rejects an update after the subscription was unpublished`() {
        assertFalse(canCommit(isIndexed = false))
    }

    @Test
    fun `rejects an update after subscription settings changed`() {
        val edited = expected.copy(url = "https://example.com/edited")

        assertFalse(canCommit(current = edited))
    }

    @Test
    fun `allows an update when only last updated metadata changed`() {
        val current = expected.copy(lastUpdated = 200)

        assertTrue(canCommit(current = current))
    }

    private fun canCommit(
        isIndexed: Boolean = true,
        current: SubscriptionItem? = expected,
    ): Boolean {
        return SubscriptionUpdateGuard.canCommit(
            isIndexed = isIndexed,
            current = current,
            expected = expected,
        )
    }
}
