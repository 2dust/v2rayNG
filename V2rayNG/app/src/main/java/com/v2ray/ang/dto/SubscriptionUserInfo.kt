package com.v2ray.ang.dto

data class SubscriptionUserInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = -1
) {
    val hasTraffic: Boolean
        get() = total > 0

    val hasExpiration: Boolean
        get() = expire > 0
}
