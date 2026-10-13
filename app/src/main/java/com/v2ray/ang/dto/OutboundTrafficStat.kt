package com.v2ray.ang.dto

data class OutboundTrafficStat(
    val tag: String,
    val direction: String,
    val value: Long,
)