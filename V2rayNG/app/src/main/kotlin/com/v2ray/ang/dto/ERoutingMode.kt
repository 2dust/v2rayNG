package com.v2ray.ang.dto

enum class ERoutingMode(val value: String  ) {
    GLOBAL_PROXY("0"),
    FOREIGN_SITES("1"),
    BLOCKED_SITES("2"),
    BYPASS_LAN("10"),
    BYPASS_MAINLAND("11"),
    BYPASS_LAN_MAINLAND("12"),
    GLOBAL_DIRECT("13");
}
