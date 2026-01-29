package com.v2ray.ang.enums

enum class RoutingType(val fileName: String) {
    WHITE("custom_routing_white"),
    BLACK("custom_routing_black"),
    GLOBAL("custom_routing_global"),
    WHITE_IRAN("custom_routing_white_iran"),
    WHITE_RUSSIA("custom_routing_white_russia");

    companion object {
        fun fromIndex(index: Int): RoutingType {
            return when (index) {
                0 -> WHITE
                1 -> BLACK
                2 -> GLOBAL
                3 -> WHITE_IRAN
                4 -> WHITE_RUSSIA
                else -> WHITE
            }
        }
    }
}
