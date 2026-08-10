package com.v2ray.ang.dto.entities

data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    /** Derived once for this profile snapshot and reused by list recompositions. */
    val isDeprecated: Boolean,
    val testDelayMillis: Long = 0L,
    val testDelayString: String = "",
)
