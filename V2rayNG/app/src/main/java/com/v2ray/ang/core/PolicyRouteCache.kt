package com.v2ray.ang.core

/**
 * Process-local memory of the last policy-group outbound that worked on each
 * underlay. Nothing is persisted to disk: explicit service stop/restart clears
 * it, while an in-place core reset can carry it across a network transition.
 */
object PolicyRouteCache {
    data class Snapshot(
        val networkKey: String?,
        val networkHandle: Long?,
        val generation: Long,
    )

    private val routes = mutableMapOf<String, MutableMap<String, String>>()
    private var currentNetworkKey: String? = null
    private var currentNetworkHandle: Long? = null
    private var generation = 0L

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(currentNetworkKey, currentNetworkHandle, generation)

    @Synchronized
    fun setCurrentNetwork(networkKey: String, networkHandle: Long) {
        currentNetworkKey = networkKey
        currentNetworkHandle = networkHandle
    }

    @Synchronized
    fun lookup(networkKey: String?, profileGuid: String): String? {
        if (networkKey.isNullOrBlank() || profileGuid.isBlank()) return null
        return routes[networkKey]?.get(profileGuid)
    }

    @Synchronized
    fun remember(
        networkKey: String?,
        profileGuid: String,
        outboundTag: String,
        expectedGeneration: Long? = null,
    ): Boolean {
        if (networkKey.isNullOrBlank() || profileGuid.isBlank() || outboundTag.isBlank()) return false
        if (expectedGeneration != null && expectedGeneration != generation) return false
        routes.getOrPut(networkKey) { mutableMapOf() }[profileGuid] = outboundTag
        return true
    }

    @Synchronized
    fun rememberCurrent(snapshot: Snapshot, profileGuid: String, outboundTag: String): Boolean {
        if (snapshot.networkKey.isNullOrBlank() || profileGuid.isBlank() || outboundTag.isBlank()) return false
        if (snapshot != Snapshot(currentNetworkKey, currentNetworkHandle, generation)) return false
        val networkRoutes = routes.getOrPut(snapshot.networkKey) { mutableMapOf() }
        if (networkRoutes[profileGuid] == outboundTag) return false
        networkRoutes[profileGuid] = outboundTag
        return true
    }

    /** Accepts a result from another process only for the exact underlay that measured it. */
    @Synchronized
    fun rememberObserved(
        networkKey: String,
        networkHandle: Long,
        profileGuid: String,
        outboundTag: String,
    ): Boolean {
        if (networkKey != currentNetworkKey || networkHandle != currentNetworkHandle) return false
        if (profileGuid.isBlank() || outboundTag.isBlank()) return false
        val networkRoutes = routes.getOrPut(networkKey) { mutableMapOf() }
        if (networkRoutes[profileGuid] == outboundTag) return false
        networkRoutes[profileGuid] = outboundTag
        return true
    }

    @Synchronized
    fun clear() {
        routes.clear()
        currentNetworkKey = null
        currentNetworkHandle = null
        generation++
    }
}
