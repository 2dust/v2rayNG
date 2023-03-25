package com.v2ray.ang.dto

enum class EConfigType(val value: Int, val protocolScheme: String) {
    VMESS(1, "vmess://"),
    CUSTOM(2, ""),
    SHADOWSOCKS(3, "ss://"),
    SOCKS(4, "socks://"),
    VLESS(5, "vless://"),
    TROJAN(6, "trojan://"),
    WIREGUARD(7, "wireguard://"),
    LowestPing(101, ""),
    LoadBalance (102, ""),
    Usage (103, "");



    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value }
    }
}
