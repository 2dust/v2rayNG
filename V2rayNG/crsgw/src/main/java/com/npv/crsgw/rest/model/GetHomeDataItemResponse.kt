package com.npv.crsgw.rest.model

data class GetHomeDataItemResponse(
    val email: String,
    val expireAt: String,
    val items: List<HomeDataItem>,
    val subscriptionLinks: List<String>
)
