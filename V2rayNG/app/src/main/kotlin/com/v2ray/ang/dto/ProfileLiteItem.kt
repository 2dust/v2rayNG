package com.v2ray.ang.dto

data class ProfileLiteItem(
    val configType: EConfigType,
    var subscriptionId: String = "",
    var remarks: String = "",
    var server: String?,
    var serverPort: Int?,
)