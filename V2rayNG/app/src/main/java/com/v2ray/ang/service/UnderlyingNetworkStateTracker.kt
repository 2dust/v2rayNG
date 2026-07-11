package com.v2ray.ang.service

/**
 * Tracks Android's selected VPN underlay without treating the initial callback
 * or repeated capability updates as a network transition.
 */
internal class UnderlyingNetworkStateTracker<T> {
    private var current: T? = null
    private var hasObservedNetwork = false
    private var currentWasLost = false

    @Synchronized
    fun onAvailable(network: T): Boolean {
        val changed = hasObservedNetwork && (current != network || currentWasLost)
        current = network
        hasObservedNetwork = true
        currentWasLost = false
        return changed
    }

    @Synchronized
    fun onLost(network: T): Boolean {
        if (current != network) return false
        current = null
        currentWasLost = true
        return true
    }

    @Synchronized
    fun isCurrent(network: T): Boolean = current == network

    @Synchronized
    fun reset() {
        current = null
        hasObservedNetwork = false
        currentWasLost = false
    }
}
