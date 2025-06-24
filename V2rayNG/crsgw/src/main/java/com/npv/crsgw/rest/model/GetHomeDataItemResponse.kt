package com.npv.crsgw.rest.model

data class GetHomeDataItemResponse(
    val email: String,
    // In GB
    val totalTraffic: Long,
    // In GB
    val usedTraffic: Double,
    val remainingDays: Int,
    val expireAt: String,
    val items: List<HomeDataItem>,
    val subscriptionLinks: List<String>,
    val signature: String
)
