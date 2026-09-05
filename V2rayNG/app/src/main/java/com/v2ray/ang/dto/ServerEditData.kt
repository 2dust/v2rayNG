package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.ProfileItem

data class ServerEditData(
    val profile: ProfileItem?,
    val isSelected: Boolean = false,
    val rawContent: String = "",
    val subscriptions: List<SubscriptionOption> = emptyList(),
    val profileRemarks: List<String> = emptyList(),
    val fallbackTags: List<String> = emptyList()
)
