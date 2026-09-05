package com.v2ray.ang.dto

data class SubUpdateOptions(
    val updateSubscription: Boolean = false,
    val autoTestAfterUpdate: Boolean = false,
    val autoRemoveInvalid: Boolean = false,
    val autoSortAfterTest: Boolean = false,
)
