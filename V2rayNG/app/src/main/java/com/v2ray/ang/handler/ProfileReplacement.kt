package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

internal object ProfileReplacement {

    /**
     * Returns whether a policy group belongs to the local profile set rather than subscription
     * content. Legacy profiles without the source marker are retained only when their stored
     * subscription ownership matches the group being replaced.
     */
    fun isManualPolicyGroup(profile: ProfileItem?, subscriptionId: String): Boolean {
        if (profile?.configType != EConfigType.POLICYGROUP) return false

        return when (profile.isSubscription) {
            true -> false
            false -> true
            null -> profile.subscriptionId == subscriptionId
        }
    }

    fun findManualPolicyGroups(
        serverIds: List<String>,
        profilesByGuid: Map<String, ProfileItem?>,
        subscriptionId: String,
    ): List<String> = serverIds.filter { guid ->
        isManualPolicyGroup(profilesByGuid[guid], subscriptionId)
    }

    /**
     * Finds the profile that should become selected after publishing a replacement batch.
     * The first profile becomes selected when the store has no current selection.
     */
    fun findSelectedReplacement(
        profiles: Map<String, ProfileItem>,
        currentSelection: String?,
        selectedProfile: ProfileItem?,
    ): String? {
        if (profiles.isEmpty()) return null
        if (currentSelection.isNullOrBlank()) return profiles.keys.first()
        if (selectedProfile == null) return null

        if (selectedProfile.remarks.isNotBlank()) {
            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks) &&
                        isSameText(candidate.server, selectedProfile.server) &&
                        isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                        isSameText(candidate.password, selectedProfile.password)
            }?.key?.let { return it }

            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks)
            }?.key?.let { return it }
        }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                    isSameText(candidate.password, selectedProfile.password)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort)
        }?.key?.let { return it }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server)
        }?.key?.let { return it }

        return profiles.keys.firstOrNull()
    }

    /**
     * Finds replaced payloads that are safe to remove.
     *
     * A null cross-group reference set means that at least one raw group index could not
     * be read. In that case deletion fails closed.
     */
    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServer: String?,
        serversReferencedByOtherGroups: Set<String>?,
    ): Set<String> {
        if (serversReferencedByOtherGroups == null) return emptySet()

        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid != protectedServer &&
                    guid !in replacementServers &&
                    guid !in serversReferencedByOtherGroups
        }
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
