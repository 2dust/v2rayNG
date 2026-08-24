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
        return isIndexed && current?.copy(lastUpdated = expected.lastUpdated) == expected
    }
}
