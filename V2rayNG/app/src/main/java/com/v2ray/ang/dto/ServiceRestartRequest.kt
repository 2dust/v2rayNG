package com.v2ray.ang.dto

import java.io.Serializable

data class ServiceRestartRequest(
    val suppressIntermediateAnnouncements: Boolean = false,
) : Serializable
