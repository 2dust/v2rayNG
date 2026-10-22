package com.v2ray.ang.ui.subscription

import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
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
            requestHeaders = "X-Subscription: example",
            usageInfo = SubscriptionUsageInfo(upload = 12, download = 34, total = 100, expire = 5678),
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

    @Test
    fun enabledChangePreservesLatestUsageMetadataAndHeaders() {
        val rowSnapshot = SubscriptionItem(url = "https://example.invalid/subscription", enabled = true)
        val current = rowSnapshot.copy(
            lastUpdated = 1234,
            requestHeaders = "X-Subscription: latest",
            usageInfo = SubscriptionUsageInfo(
                upload = 12, download = 34, total = 100, expire = 5678,
                title = "Current plan", supportUrl = "https://example.invalid/support",
                webPageUrl = "https://example.invalid/account",
            ),
        )

        assertEquals(current.copy(enabled = false), subscriptionEnabledChange(current, false))
        assertEquals(current, subscriptionEnabledChange(subscriptionEnabledChange(current, false), true))
        assertEquals(true, current.enabled)
        assertNull(rowSnapshot.usageInfo)
    }

    @Test
    fun enabledChangeIgnoresMissingOrAlreadyAppliedActions() {
        assertNull(subscriptionEnabledChange(null, true))
        assertNull(subscriptionEnabledChange(SubscriptionItem(enabled = true), true))
        assertNull(subscriptionEnabledChange(SubscriptionItem(enabled = false), false))
    }

    @Test
    fun enabledChangeStillSupportsLocalGroups() {
        val current = SubscriptionItem(url = "", enabled = false)
        assertEquals(current.copy(enabled = true), subscriptionEnabledChange(current, true))
    }
}
