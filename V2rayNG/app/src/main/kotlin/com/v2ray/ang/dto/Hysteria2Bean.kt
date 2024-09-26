package com.v2ray.ang.dto

data class Hysteria2Bean(
    val server: String?,
    val auth: String?,
    val lazy: Boolean? = true,
    val socks5: Socks5Bean?,
    val tls: TlsBean?
) {
    data class Socks5Bean(
        val listen: String?,
    )

    data class TlsBean(
        val sni: String?
    )
}