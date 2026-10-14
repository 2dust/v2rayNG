package com.v2ray.ang.core

/** Serializes commands for the Aether-owned child and tunnel, including pending restarts. */
internal class AetherLifecycle {
    private var stopping = false
    private var restartPending = false

    @Synchronized
    fun isStopping(): Boolean = stopping

    @Synchronized
    fun canStart(setupActive: Boolean, ownsCore: Boolean): Boolean =
        !stopping && !setupActive && !ownsCore

    @Synchronized
    fun readyPort(requestedGuid: String, ownedGuid: String, childAlive: Boolean, port: Int?): Int? =
        if (!stopping && ownedGuid.isNotBlank() && requestedGuid == ownedGuid && childAlive &&
            port != null && port in 1024..65535) port else null

    @Synchronized
    fun beginStop(): Boolean {
        restartPending = false
        if (stopping) return false
        stopping = true
        return true
    }

    @Synchronized
    fun beginRestart(): Boolean {
        if (stopping) return false
        stopping = true
        restartPending = true
        return true
    }

    @Synchronized
    fun completeRestart(start: () -> Unit): Boolean {
        if (!restartPending) return false
        restartPending = false
        stopping = false
        try {
            start()
        } catch (failure: Exception) {
            stopping = true
            throw failure
        }
        return true
    }
}
