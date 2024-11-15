package com.v2ray.ang.dto

import com.v2ray.ang.AppConfig


enum class EConfigType(val value: Int, val protocolScheme: String) {
    VMESS(1, AppConfig.VMESS),
    CUSTOM(2, AppConfig.CUSTOM),
    SHADOWSOCKS(3, AppConfig.SHADOWSOCKS),
    SOCKS(4, AppConfig.SOCKS),
    VLESS(5, AppConfig.VLESS),
    TROJAN(6, AppConfig.TROJAN),
    WIREGUARD(7, AppConfig.WIREGUARD),
//    TUIC(8, AppConfig.TUIC),
    HYSTERIA2(9, AppConfig.HYSTERIA2),
    HTTP(10, AppConfig.HTTP);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value }
    }
}
