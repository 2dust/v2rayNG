package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem

internal data class SubscriptionUpdateCommit(
    val expected: SubscriptionItem,
    val replacement: SubscriptionItem,
)

internal object SubscriptionUpdateGuard {
    fun canCommit(
        isIndexed: Boolean,
        current: SubscriptionItem?,
        expected: SubscriptionItem,
    ): Boolean {
        return isIndexed && current != null && settingsOf(current) == settingsOf(expected)
    }

    private fun settingsOf(subscription: SubscriptionItem): SubscriptionItem {
        return subscription.copy(lastUpdated = -1)
    }
}
