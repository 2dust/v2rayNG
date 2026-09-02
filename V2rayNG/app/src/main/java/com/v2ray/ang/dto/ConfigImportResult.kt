package com.v2ray.ang.dto

/** Import counts, including subscription URLs skipped because they already exist. */
data class ConfigImportResult(
    val configCount: Int = 0,
    val subscriptionCount: Int = 0,
    val duplicateSubscriptionCount: Int = 0
)
