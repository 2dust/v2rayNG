package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

data class GroupMapItem(
    val id: String,
    val remarks: String,
    val serverCount: Int = 0,
    val usageInfo: SubscriptionUsageInfo? = null,
)
