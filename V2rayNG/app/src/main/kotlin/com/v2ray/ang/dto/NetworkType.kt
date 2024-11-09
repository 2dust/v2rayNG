package com.v2ray.ang.dto

enum class NetworkType(val type: String) {
    TCP("tcp"),
    KCP("kcp"),
    WS("ws"),
    HTTP_UPGRADE("httpupgrade"),
    SPLIT_HTTP("splithttp"),
    HTTP("http"),
    H2("h2"),
    QUIC("quic"),
    GRPC("grpc");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: TCP
    }
}
