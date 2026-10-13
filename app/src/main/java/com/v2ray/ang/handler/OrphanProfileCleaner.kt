package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID

internal data class StoredProfileReference(
    val guid: String,
    val subscriptionId: String?,
)

internal object OrphanProfileCleaner {

    /**
     * Finds profile payloads that are provably unreachable from the raw group indexes.
     *
     * A null subscription ID means that the profile payload could not be decoded and is
     * preserved. A null server set means that a group index could not be decoded, in which
     * case the entire classification returns null and no cleanup should run.
     * Subscription metadata is deliberately not an input because SUB and SUB_IDS can be
     * missing while the raw SUB_SERVERS_* indexes remain intact.
     */
    fun findOrphans(
        profiles: Collection<StoredProfileReference>,
        indexedServersBySubscription: Map<String, Set<String>?>,
        selectedServer: String?,
    ): Set<String>? {
        if (profiles.isEmpty()) return emptySet()

        if (indexedServersBySubscription.isEmpty() ||
            indexedServersBySubscription.values.any { it == null }
        ) {
            return null
        }

        val indexedServers = indexedServersBySubscription.values
            .filterNotNull()
            .flatten()
            .toSet()

        return profiles.mapNotNullTo(linkedSetOf()) { profile ->
            if (profile.guid == selectedServer || profile.guid in indexedServers) {
                return@mapNotNullTo null
            }

            val subscriptionId = profile.subscriptionId ?: return@mapNotNullTo null
            val groupId = subscriptionId.ifEmpty { DEFAULT_SUBSCRIPTION_ID }
            if (!indexedServersBySubscription.containsKey(groupId)) {
                return@mapNotNullTo null
            }

            profile.guid
        }
    }
}
