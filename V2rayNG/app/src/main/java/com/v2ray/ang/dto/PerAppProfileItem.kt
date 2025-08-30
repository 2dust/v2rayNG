package com.v2ray.ang.dto

data class PerAppProfileItem(
    var remarks: String = "default",
    var apps: MutableSet<String> = mutableSetOf(),
    var addedTime: Long = System.currentTimeMillis()
)
