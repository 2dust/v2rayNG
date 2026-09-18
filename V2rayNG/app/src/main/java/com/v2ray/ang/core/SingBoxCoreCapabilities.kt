package com.v2ray.ang.core

/** Capabilities compiled into the experimental sing-box Android core. */
object SingBoxCoreCapabilities {
    const val AVAILABLE = true
    const val HYSTERIA2 = true
    const val NAIVE_PROXY = true
    const val OPENCONNECT = true
    const val AMNEZIA_WG = true

    fun summary(): String = buildList {
        if (HYSTERIA2) add("Hysteria2")
        if (NAIVE_PROXY) add("NaiveProxy")
        if (OPENCONNECT) add("OpenConnect")
        if (AMNEZIA_WG) add("AmneziaWG/WireGuard")
    }.joinToString()
}
