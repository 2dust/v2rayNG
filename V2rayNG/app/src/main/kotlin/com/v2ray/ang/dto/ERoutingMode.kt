package com.v2ray.ang.dto

enum class ERoutingMode(val value: String  ) {
    GLOBAL_PROXY("0"),
    BYPASS_LAN("1"),
    BYPASS_MAINLAND("2"),
    BYPASS_LAN_MAINLAND("3"),
    GLOBAL_DIRECT("4");
}
