package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var icmpDelayMillis: Long = 0L
) {
    fun getTestDelayString(): String {
        val http = if (testDelayMillis == 0L) "" else "${testDelayMillis}ms"
        val icmp = if (icmpDelayMillis == 0L) "" else "${icmpDelayMillis}ms"
        if (http.isNotEmpty() && icmp.isNotEmpty()) {
            return "HTTP: $http | ICMP: $icmp"
        }
        if (icmp.isNotEmpty()) return "ICMP: $icmp"
        return http
    }
}