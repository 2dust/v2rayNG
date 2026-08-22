package com.v2ray.ang.dto

import java.io.Serializable

/** One persisted delay-test result delivered from the probe process to the UI. */
data class RealPingResult(
    val testId: String,
    val guid: String,
    val delayMillis: Long,
) : Serializable

/** Completed profile count for one active delay-test batch. */
data class RealPingProgress(
    val testId: String,
    val completed: Int,
    val total: Int,
) : Serializable

/** Terminal result for one delay-test batch. */
data class RealPingSummary(
    val testId: String,
    val live: Int,
    val total: Int,
    val cancelled: Boolean,
    val listChanged: Boolean = false,
) : Serializable
