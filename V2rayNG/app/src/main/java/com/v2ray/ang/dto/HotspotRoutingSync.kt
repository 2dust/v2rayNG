package com.v2ray.ang.dto

import java.io.Serializable

/** Authenticated native-core lifecycle update for the shell-owned tethering daemon. */
data class HotspotRoutingSync(
    val token: String,
    val event: Int,
    val snapshot: HotspotRoutingSnapshot? = null,
    val detail: String = "",
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        const val EVENT_CORE_STOPPING = 1
        const val EVENT_CORE_STARTED = 2
        const val EVENT_CORE_START_FAILED = 3
    }
}
