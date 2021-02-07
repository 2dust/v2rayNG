package com.v2ray.ang.dto

data class ServerAffiliationInfo(var testDelayMillis: Long) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }
}
