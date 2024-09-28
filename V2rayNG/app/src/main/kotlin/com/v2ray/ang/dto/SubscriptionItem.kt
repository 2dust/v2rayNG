package com.v2ray.ang.dto

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = false,
    val updateInterval: Int? = null,
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
)

