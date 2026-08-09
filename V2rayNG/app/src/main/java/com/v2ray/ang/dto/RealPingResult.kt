package com.v2ray.ang.dto

import java.io.Serializable

/** One persisted RealDelay result delivered from the probe process to the UI. */
data class RealPingResult(
    val guid: String,
    val delayMillis: Long,
) : Serializable
