package com.v2ray.ang.service

/**
 * Tracks Android's selected underlay without treating the initial callback or repeated callbacks
 * for the same network as handovers.
 */
internal class UnderlyingNetworkStateTracker<T : Any> {
    private var current: T? = null
    private var hasObservedNetwork = false

    @Synchronized
    fun onAvailable(network: T): Boolean {
        val changed = hasObservedNetwork && current != network
        current = network
        hasObservedNetwork = true
        return changed
    }

    @Synchronized
    fun onLost(network: T): Boolean {
        if (current != network) return false
        current = null
        return true
    }

    @Synchronized
    fun isCurrent(network: T): Boolean = current == network

    @Synchronized
    fun reset() {
        current = null
        hasObservedNetwork = false
    }
}
