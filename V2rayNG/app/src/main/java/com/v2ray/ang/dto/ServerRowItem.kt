package com.v2ray.ang.dto

import androidx.compose.runtime.Immutable
import com.v2ray.ang.enums.EConfigType

@Immutable
data class ServerRowItem(
    val guid: String,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val subscriptionBadge: String,
    val configType: EConfigType,
    val testDelayMillis: Long = 0L
)
