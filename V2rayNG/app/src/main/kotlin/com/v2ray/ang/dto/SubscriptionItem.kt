package com.v2ray.ang.dto

data class SubscriptionItem(
        var remarks: String = "",
        var url: String = "",
        var enabled: Boolean = true,
        var addedTime: Long = System.currentTimeMillis(),
        var used: Long=-1,
        var total: Long=-1,
        var expire: Long=-1,
        var home_link: String="",
        ) {
}
