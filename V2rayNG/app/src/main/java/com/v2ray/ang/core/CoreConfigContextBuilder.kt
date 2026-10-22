package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

internal class PolicyGroupConfigException(val groupId: String, reason: String) : IllegalArgumentException(reason)

/**
 * Build runtime context from the selected profile.
 *
 * All outbound type analysis is completed here for both the selected profile
 * and routing targets. Custom profiles are returned immediately without
 * entering the normal analysis flow.
 */
object CoreConfigContextBuilder {

    /**
     * Load one profile and produce a fully analyzed context.
     *
     * Null is returned only when the selected profile cannot be loaded.
     */
    fun build(context: Context, guid: String): CoreConfigContext? {
        val config = MmkvManager.decodeServerConfig(guid) ?: return null

        // CUSTOM: return immediately — CoreConfigManager handles this path on its own.
        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        // Step 1: Resolve the main outbound (always tag = TAG_PROXY).
        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, config, guid) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        // Step 2: Resolve all non-builtin routing outbound tags.
        val routingResolvedOutbounds = resolveRoutingOutbounds()
        val resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds
        val fallbackResolvedOutbounds = resolveFallbackOutbounds(resolvedOutbounds)
        val routingDomainRules = collectRoutingDomainRulesForDns()

        return CoreConfigContext(
            context = context,
            guid = guid,
            resolvedOutbounds = resolvedOutbounds + fallbackResolvedOutbounds,
            routingDomainRules = routingDomainRules,
        )
    }

    /**
     * Resolve one outbound target into a normalized outbound entry.
     *
     * Custom profiles are ignored at this stage and produce no entry.
     */
    private fun resolveOutbound(tag: String, profile: ProfileItem, guid: String): CoreConfigContext.ResolvedOutbound? {
        if (profile.configType == EConfigType.CUSTOM) {
            return null
        }

        if (profile.configType == EConfigType.POLICYGROUP) {
            val servers = MmkvManager.decodeAllServerList().mapNotNull { id ->
                MmkvManager.decodeServerConfig(id)?.let { CoreConfigContext.ResolvedProfile(id, it) }
            }
            return resolvePolicyGroup(
                tag, guid, profile, servers,
                profile.subscriptionId.takeIf { it.isNotEmpty() }?.let { MmkvManager.decodeSubscription(it) },
            )
        }

        val (resolvedProfiles, resolvedType) = when (profile.configType) {
            EConfigType.PROXYCHAIN -> {
                val chainProfiles = resolveProxyChainProfiles(profile)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }

            else -> {
                val chainProfiles = resolveProxyChainProfilesFromGroup(profile)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }
        }

        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            profile = profile,
            resolvedProfiles = resolvedProfiles,
            resolvedType = resolvedType,
        )
    }

    /**
     * Collect and resolve non-builtin routing targets from enabled rules.
     *
     * Invalid or empty targets are skipped and handled by fallback logic later.
     */
    private fun resolveRoutingOutbounds(): List<CoreConfigContext.ResolvedOutbound> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val resolvedOutbounds = mutableListOf<CoreConfigContext.ResolvedOutbound>()
        val processedTags = mutableSetOf<String>()

        try {
            rulesetItems
                .filter { it.enabled }
                .mapNotNull { it.outboundTag.takeIf { tag -> tag.isNotBlank() } }
                .filter { tag -> tag !in AppConfig.BUILTIN_OUTBOUND_TAGS }
                .distinct()
                .forEach { tag ->
                    if (tag in processedTags) {
                        return@forEach
                    }
                    processedTags.add(tag)

                    try {
                        val (guid, profile) = SettingsManager.getServerViaRemarksWithGuid(tag) ?: run {
                            LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                            return@forEach
                        }
                        val resolvedOutbound = resolveOutbound(tag, profile, guid) ?: run {
                            LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                            return@forEach
                        }
                        if (resolvedOutbound.resolvedProfiles.isEmpty()) {
                            LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                            return@forEach
                        }
                        resolvedOutbounds.add(resolvedOutbound)
                        LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedProfiles.size}")
                    } catch (e: PolicyGroupConfigException) {
                        throw e
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                    }
                }
        } catch (e: PolicyGroupConfigException) {
            throw e
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    /** Resolve one snapshot so subscription aliases and candidate GUIDs stay consistent. */
    internal fun resolvePolicyGroup(
        tag: String,
        guid: String,
        config: ProfileItem,
        servers: List<CoreConfigContext.ResolvedProfile>,
        subscription: SubscriptionItem?,
    ): CoreConfigContext.ResolvedOutbound {
        if (config.subscriptionId.isNotEmpty() && subscription == null) {
            throw PolicyGroupConfigException(guid, "Missing subscription")
        }
        fun valid(profile: ProfileItem): Boolean = !profile.configType.isComplexType() &&
            profile.server.isNotNullEmpty() &&
            (Utils.isPureIpAddress(profile.server!!) || Utils.isValidUrl(profile.server!!))

        fun endpoint(remark: String?): CoreConfigContext.ResolvedProfile? {
            if (remark.isNullOrEmpty()) return null
            // Preserve first-match semantics of existing persisted remark references.
            return servers.firstOrNull { it.profile.remarks == remark }
                ?.takeIf { valid(it.profile) }
                ?: throw PolicyGroupConfigException(guid, "Invalid subscription chain endpoint")
        }

        val landing = endpoint(subscription?.nextProfile)
        val preproxy = endpoint(subscription?.prevProfile)
        if (landing != null && landing.guid == preproxy?.guid) {
            throw PolicyGroupConfigException(guid, "Repeated subscription chain endpoint")
        }
        val hasChain = landing != null || preproxy != null
        fun member(server: CoreConfigContext.ResolvedProfile): CoreConfigContext.PolicyGroupMember {
            val chain = listOfNotNull(landing, server, preproxy)
            if (chain.map { it.guid }.distinct().size != chain.size) {
                throw PolicyGroupConfigException(guid, "Repeated policy group chain hop")
            }
            return CoreConfigContext.PolicyGroupMember(server.guid, chain)
        }

        val filter = config.policyGroupFilter
        val regex = if (filter.isNullOrBlank()) null else try {
            Regex(filter)
        } catch (_: IllegalArgumentException) {
            null // Existing invalid-regex behavior is a literal substring filter.
        }
        val candidates = servers.distinctBy { it.guid }.filter { server ->
            val profile = server.profile
            (config.policyGroupSubscriptionId.isNullOrBlank() || profile.subscriptionId == config.policyGroupSubscriptionId) &&
                (filter.isNullOrBlank() || (regex?.containsMatchIn(profile.remarks) ?: profile.remarks.contains(filter))) &&
                valid(profile) && server.guid != landing?.guid && server.guid != preproxy?.guid
        }
        if (candidates.isEmpty()) throw PolicyGroupConfigException(guid, "Empty policy group")

        val fallbackTag = config.policyGroupFallbackTag
        val fallback = if (hasChain && BalancerStrategyType.from(config.policyGroupType).supportsObservatory &&
            config.policyGroupTestOutbounds != false && !fallbackTag.isNullOrEmpty() &&
            fallbackTag !in AppConfig.BUILTIN_OUTBOUND_TAGS
        ) {
            member(endpoint(fallbackTag) ?: throw PolicyGroupConfigException(guid, "Missing fallback"))
        } else null
        return CoreConfigContext.ResolvedOutbound(
            tag, config, candidates.map { it.profile }, CoreResolvedType.POLICYGROUP,
            CoreConfigContext.ResolvedPolicyGroup(guid, candidates.map(::member), hasChain, fallback),
        )
    }

    private fun resolveProxyChainProfiles(config: ProfileItem): List<ProfileItem> {
        if (config.proxyChainProfiles.isNullOrBlank()) {
            return listOf(config)
        }

        try {
            return config.proxyChainProfiles.orEmpty().split(",")
                .asSequence()
                .mapNotNull { remark -> SettingsManager.getServerViaRemarks(remark) }
                .filter { it.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.server!!) || Utils.isValidUrl(it.server!!) }
                .filter { !it.configType.isComplexType() }
                .toList()
                .reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    /**
     * Resolve chain nodes from subscription neighbors in order: next, current, prev.
     *
     * When no chain is available, return a single-node result.
     */
    private fun resolveProxyChainProfilesFromGroup(config: ProfileItem): List<ProfileItem> {
        if (config.subscriptionId.isEmpty()) {
            return listOf(config)
        }

        try {
            val subItem = MmkvManager.decodeSubscription(config.subscriptionId) ?: return listOf(config)
            val resolved = mutableListOf<ProfileItem>()
            SettingsManager.getServerViaRemarks(subItem.nextProfile)?.let { resolved.add(it) }
            resolved.add(config)
            SettingsManager.getServerViaRemarks(subItem.prevProfile)?.let { resolved.add(it) }
            return resolved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    /**
     * Collect enabled routing domain rules in original order for DNS segmentation.
     *
     * outbounds are normalized into three tags only: proxy / direct / block.
     */
    private fun collectRoutingDomainRulesForDns(): List<CoreConfigContext.RoutingDomainRule> {
        val rulesetItems = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val result = mutableListOf<CoreConfigContext.RoutingDomainRule>()

        rulesetItems
            .asSequence()
            .filter { it.enabled }
            .filter { !it.domain.isNullOrEmpty() }
            .forEach { rule ->
                val normalizedOutboundTag = when (rule.outboundTag) {
                    AppConfig.TAG_DIRECT -> AppConfig.TAG_DIRECT
                    AppConfig.TAG_BLOCKED -> AppConfig.TAG_BLOCKED
                    else -> AppConfig.TAG_PROXY
                }
                result.add(
                    CoreConfigContext.RoutingDomainRule(
                        domain = rule.domain.orEmpty(),
                        outboundTag = normalizedOutboundTag
                    )
                )
            }

        return result
    }

    /**
     * Resolve and collect fallback outbounds from all POLICYGROUP nodes.
     *
     * Fallback targets must not overlap with already resolved tags or builtin tags.
     */
    private fun resolveFallbackOutbounds(resolvedOutbounds: List<CoreConfigContext.ResolvedOutbound>): List<CoreConfigContext.ResolvedOutbound> {
        return resolvedOutbounds
            .asSequence()
            .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
            .filter { it.policyGroup?.hasSubscriptionChain != true }
            .filter { BalancerStrategyType.from(it.profile.policyGroupType).supportsObservatory && it.profile.policyGroupTestOutbounds != false }
            .mapNotNull { it.profile.policyGroupFallbackTag }
            .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
            .distinct()
            .mapNotNull { tag ->
                SettingsManager.getServerViaRemarksWithGuid(tag)
                    ?.takeUnless { it.second.configType == EConfigType.CUSTOM || it.second.configType == EConfigType.POLICYGROUP }
                    ?.let { (guid, profile) -> resolveOutbound(tag, profile, guid) }
            }
            .toList()
    }
}
