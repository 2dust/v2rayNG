package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionItem

data class SubEditData(
    val item: SubscriptionItem?,
    val confirmRemove: Boolean,
    val profileOptions: List<String>
)
