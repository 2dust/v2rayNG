package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(var testDelayMillis: Long = 0L) {
    fun getTestDelayString(): String {
        return if (testDelayMillis == 0L) {
            "..."
        } else if (testDelayMillis < 0L) {
            "timeout"
        } else {
            testDelayMillis.toString() + "ms"
        }
    }
}