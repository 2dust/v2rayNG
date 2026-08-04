package com.v2ray.ang.enums

/**
 * How a server is measured when testing profiles.
 */
enum class PingType(val value: String) {
    /** HTTP GET to the test url through the tested outbound. */
    PROXY_GET("proxy_get"),

    /** HTTP HEAD to the test url through the tested outbound. */
    PROXY_HEAD("proxy_head"),

    /** Plain TCP handshake with the server address. */
    TCP("tcp"),

    /** System ping to the server address. */
    ICMP("icmp");

    companion object {
        fun from(value: String?): PingType = entries.firstOrNull { it.value == value } ?: PROXY_GET
    }
}
