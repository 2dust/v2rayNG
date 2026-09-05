package com.v2ray.ang.ui.main

/** Tracks only the UI state that must remain stable while the daemon replaces its service. */
internal class MainRestartTracker {
    private var pending: PendingRestart? = null

    val isIdle: Boolean
        get() = pending == null

    val hasPendingServerChange: Boolean
        get() = pending is PendingRestart.ServerChange

    fun beginServerChange(currentGuid: String?, requestedGuid: String): Boolean {
        if (requestedGuid == currentGuid || pending != null) return false
        pending = PendingRestart.ServerChange(requestedGuid)
        return true
    }

    fun onRestarting() {
        if (pending == null) pending = PendingRestart.Background
    }

    fun complete(): String? {
        val requestedGuid = (pending as? PendingRestart.ServerChange)?.guid
        pending = null
        return requestedGuid
    }

    fun completeUnhandledServerChange(guid: String, handled: Boolean): Boolean {
        if (handled || pending != PendingRestart.ServerChange(guid)) return false
        pending = null
        return true
    }

    private sealed interface PendingRestart {
        data object Background : PendingRestart
        data class ServerChange(val guid: String) : PendingRestart
    }
}
