package com.v2ray.ang.dto

sealed class RealPingEvent {

    /** Periodic progress update while the batch is still running. */
    data class Progress(val completed: Int, val total: Int) : RealPingEvent()

    /** A single server result is available. */
    data class Result(val guid: String, val delayMillis: Long) : RealPingEvent()

    /** Terminal counts for the batch. */
    data class Finish(
        val live: Int,
        val completed: Int,
        val total: Int,
    ) : RealPingEvent()
}

