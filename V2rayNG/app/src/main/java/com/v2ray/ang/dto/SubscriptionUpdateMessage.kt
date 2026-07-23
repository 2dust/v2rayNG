package com.v2ray.ang.dto

import java.io.Serializable

data class SubscriptionUpdateMessage(
    val key: Int,
    val subIds: List<String> = listOf()
) : Serializable
