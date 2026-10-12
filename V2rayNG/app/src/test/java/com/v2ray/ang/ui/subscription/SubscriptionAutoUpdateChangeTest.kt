package com.v2ray.ang.ui.subscription

import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionAutoUpdateChangeTest {
    @Test
    fun changesOnlyPeriodicUpdateAndDoesNotMutateTheOriginal() {
        val original = SubscriptionItem(
            remarks = "Example", url = "https://example.invalid/subscription",
            enabled = false, autoUpdate = false, updateInterval = 120,
            lastUpdated = 1234, filter = "vless", userAgent = "Example",
        )
        val enabled = subscriptionAutoUpdateChange(original, true)
        assertEquals(original.copy(autoUpdate = true), enabled)
        assertFalse(original.autoUpdate)
        assertEquals(original, subscriptionAutoUpdateChange(enabled, false))
    }

    @Test
    fun ignoresMissingAndLocalGroups() {
        assertNull(subscriptionAutoUpdateChange(null, true))
        assertNull(subscriptionAutoUpdateChange(SubscriptionItem(url = ""), true))
    }

    @Test
    fun ignoresAlreadyAppliedActions() {
        val original = SubscriptionItem(url = "https://example.invalid/subscription", autoUpdate = true)
        assertNull(subscriptionAutoUpdateChange(original, true))
        assertNull(subscriptionAutoUpdateChange(original.copy(autoUpdate = false), false))
    }
}
