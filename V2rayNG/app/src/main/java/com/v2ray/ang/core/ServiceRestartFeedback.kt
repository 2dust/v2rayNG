package com.v2ray.ang.core

/** Tracks restart feedback after the restart job hands off to the replacement service. */
internal class ServiceRestartFeedback {
    private var restarting = false

    val isRestarting: Boolean
        get() = restarting

    fun begin() {
        restarting = true
    }

    fun complete(): Boolean = restarting.also { restarting = false }

    fun cancel() {
        restarting = false
    }
}
