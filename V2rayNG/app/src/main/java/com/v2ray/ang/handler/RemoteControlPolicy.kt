package com.v2ray.ang.handler

import java.security.MessageDigest

internal data class RemoteControlIdentity(val certificates: Set<String>, val firstInstallTime: Long)

internal data class RemoteControlGrant(
    val packageName: String,
    val identity: RemoteControlIdentity,
    val token: String
)

/** Pure authorization rules; Android supplies identities, never a caller-controlled package extra. */
internal object RemoteControlPolicy {
    fun select(
        previous: List<RemoteControlGrant>,
        selected: Map<String, RemoteControlIdentity>,
        newToken: () -> String
    ): List<RemoteControlGrant> = selected.map { (packageName, identity) ->
        previous.firstOrNull { it.packageName == packageName && it.identity == identity }
            ?: RemoteControlGrant(packageName, identity, newToken())
    }

    fun isAuthorized(
        grants: List<RemoteControlGrant>,
        installed: Map<String, RemoteControlIdentity>,
        senderPackages: Set<String>?,
        claimedPackage: String?,
        token: String?
    ): Boolean {
        val active = grants.filter { it.identity == installed[it.packageName] }
        // A known platform UID is authoritative, including when it contradicts a supplied token.
        if (senderPackages != null) {
            return active.any { it.packageName in senderPackages }
        }
        if (claimedPackage.isNullOrBlank() || token.isNullOrEmpty()) return false
        return active.any {
            it.packageName == claimedPackage && MessageDigest.isEqual(
                it.token.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8)
            )
        }
    }
}
