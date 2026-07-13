package com.v2ray.ang.dto

import java.io.Serializable

/** A viable policy-group route measured by the dedicated probe process. */
data class PolicyRouteUpdate(
    val profileGuid: String,
    val outboundTag: String,
) : Serializable
