package com.v2ray.ang.dto

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var lastTestTime: Long = 0L,
    var testSource: String = ""
) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }
}
