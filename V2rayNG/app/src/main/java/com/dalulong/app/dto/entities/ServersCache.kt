package com.dalulong.app.dto.entities

data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    val testDelayMillis: Long = 0L,
    val testDelayString: String = "",
)
