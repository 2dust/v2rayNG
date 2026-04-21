package com.v2ray.ang.dto

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var testSpeedMbps: Double = 0.0
) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }

    fun getTestSpeedString(): String {
        if (testSpeedMbps < 0.05) {
            return ""
        }
        return if (testSpeedMbps >= 10.0) {
            String.format("%.0f Mbps", testSpeedMbps)
        } else {
            String.format("%.1f Mbps", testSpeedMbps)
        }
    }
}
