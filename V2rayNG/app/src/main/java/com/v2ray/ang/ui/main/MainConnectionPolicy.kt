package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.perappproxy.PerAppRoutingMode

/** Keeps an empty allowlist from being presented as a ready-to-connect state. */
internal object MainConnectionPolicy {
    fun canConnect(
        isRunning: Boolean,
        selectedGuid: String?,
        routingMode: PerAppRoutingMode,
        routedAppCount: Int,
    ): Boolean = isRunning || (!selectedGuid.isNullOrEmpty() &&
        (routingMode != PerAppRoutingMode.ONLY_SELECTED || routedAppCount > 0))
}
