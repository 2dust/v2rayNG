package com.v2ray.ang.dto

data class SubscriptionItem(
        var remarks: String = "",
        var url: String = "",
        var enabled: Boolean = true,
        var addedTime: Long = System.currentTimeMillis(),
        var lastUpdateTime: Long = 0,
        var used: Long=-1,
        var total: Long=-1,
        var expire: Long=-1,//in ms
        var home_link: String="",
        var support_link: String="",
        var update_interval: Int=1,
        var dns: String?=null
        ) {



    fun needUpdate(): Boolean {
                if (update_interval<0)return false
                var hours = (System.currentTimeMillis() - lastUpdateTime) / (1000 * 60 * 60)
                return (hours>=update_interval)


        }
}
