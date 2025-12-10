package com.v2ray.ang.dto

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var totalDownloadBytes: Long = 0L
) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        return testDelayMillis.toString() + "ms"
    }

    fun getTotalDownloadString(): String {
        if (totalDownloadBytes == 0L) {
            return ""
        }
        val kb = totalDownloadBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.2f KB", kb)
            else -> "$totalDownloadBytes B"
        }
    }
}
