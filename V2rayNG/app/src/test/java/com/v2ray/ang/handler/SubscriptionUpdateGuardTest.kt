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
        assertFalse(
            SubscriptionUpdateGuard.canCommit(
                isIndexed = true,
                current = null,
                expected = expected,
            )
        )
    }

    @Test
    fun `rejects an update after the subscription was unpublished`() {
        assertFalse(
            SubscriptionUpdateGuard.canCommit(
                isIndexed = false,
                current = expected,
                expected = expected,
            )
        )
    }

    @Test
    fun `rejects an update after subscription settings changed`() {
        val edited = expected.copy(url = "https://example.com/edited")

        assertFalse(
            SubscriptionUpdateGuard.canCommit(
                isIndexed = true,
                current = edited,
                expected = expected,
            )
        )
    }

    @Test
    fun `allows an update when only last updated metadata changed`() {
        val current = expected.copy(lastUpdated = 200)

        assertTrue(
            SubscriptionUpdateGuard.canCommit(
                isIndexed = true,
                current = current,
                expected = expected,
            )
        )
    }
}
