package com.v2ray.ang.dto

data class Hysteria2Bean(
    val server: String?,
    val auth: String?,
    val lazy: Boolean? = true,
    val obfs: ObfsBean? = null,
    val socks5: Socks5Bean? = null,
    val http: Socks5Bean? = null,
    val tls: TlsBean? = null,
    val transport: TransportBean? = null,
) {
    data class ObfsBean(
        val type: String?,
        val salamander: SalamanderBean?
    ) {
        data class SalamanderBean(
            val password: String?,
        )
    }

    data class Socks5Bean(
        val listen: String?,
    )

    data class TlsBean(
        val sni: String?,
        val insecure: Boolean?,
        val pinSHA256: String?,
    )

    data class TransportBean(
        val type: String?,
        val udp: TransportUdpBean?
    ) {
        data class TransportUdpBean(
            val hopInterval: String?,
        )
    }
}
