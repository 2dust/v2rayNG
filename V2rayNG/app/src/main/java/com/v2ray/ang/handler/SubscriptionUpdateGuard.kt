package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionItem

internal class SubscriptionUpdateAbortedException :
    ProfileStorageException("Subscription changed while its update was running")

internal data class SubscriptionUpdateCommit(
    val expected: SubscriptionItem,
    val replacement: SubscriptionItem,
)

internal object SubscriptionUpdateGuard {
    /**
     * Returns the value to persist, or null if the subscription no longer matches the request.
     * Settings edits preserve current refresh metadata. A refresh supplies [updatedAt] and must
     * also match the initiating timestamp; a fresh timestamp may move backwards with the clock.
     */
    fun prepare(
        isIndexed: Boolean,
        current: SubscriptionItem?,
        expected: SubscriptionItem,
        replacement: SubscriptionItem,
        updatedAt: Long? = null,
    ): SubscriptionItem? {
        if (!isIndexed || current == null) return null
        if (current.copy(lastUpdated = expected.lastUpdated) != expected) return null
        if (updatedAt != null && current.lastUpdated != expected.lastUpdated) return null
        return replacement.copy(lastUpdated = updatedAt ?: current.lastUpdated)
    }
}
