package com.v2ray.ang.dto

import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.CoreResolvedType

data class CoreConfigContext(
    val context: Context,
    val guid: String,
    val isCustom: Boolean = false,
    val resolvedOutbounds: List<ResolvedOutbound> = emptyList(),
    val routingDomainRules: List<RoutingDomainRule> = emptyList(),
) {
    data class ResolvedOutbound(
        val tag: String,
        val profile: ProfileItem,
        val resolvedProfiles: List<ProfileItem>,
        val resolvedType: CoreResolvedType,
        val policyGroup: ResolvedPolicyGroup? = null,
    )

    /** Runtime identities and chains; never persisted as part of a profile. */
    data class ResolvedProfile(val guid: String, val profile: ProfileItem)

    data class PolicyGroupMember(
        val guid: String,
        // Destination-facing hop first, followed by its dialers.
        val chain: List<ResolvedProfile>,
    )

    data class ResolvedPolicyGroup(
        val guid: String,
        val members: List<PolicyGroupMember>,
        val hasSubscriptionChain: Boolean,
        val fallback: PolicyGroupMember? = null,
    )

    data class RoutingDomainRule(
        val domain: List<String>,
        val outboundTag: String,
    )
}
