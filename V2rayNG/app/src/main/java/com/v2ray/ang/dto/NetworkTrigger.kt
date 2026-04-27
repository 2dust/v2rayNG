package com.v2ray.ang.dto

data class NetworkTrigger(
    val enabled: Boolean = true,
    val triggerType: String = "",
    val targetSsid: String = "",
    val action: String = "start"
)
