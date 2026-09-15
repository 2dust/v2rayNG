package com.v2ray.ang.dto

data class BatchImportResult(
    val profileCount: Int = 0,
    val subscriptionIds: List<String> = emptyList(),
    /** Outcomes of saving new subscriptions and downloading their initial profiles. */
    val subscriptionUpdates: SubscriptionUpdateResult = SubscriptionUpdateResult(),
)
