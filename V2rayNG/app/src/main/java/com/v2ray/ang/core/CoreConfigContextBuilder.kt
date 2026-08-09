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

/**
 * Build runtime context from the selected profile.
 *
 * All outbound type analysis is completed here for both the selected profile
 * and routing targets. Custom profiles are returned immediately without
 * entering the normal analysis flow.
 */
object CoreConfigContextBuilder {

    /** Lazily decoded profile snapshot shared by every config in one probe batch. */
    internal class ProbeProfileLookup(requestedGuids: List<String>) {
        private val profilesByGuid = linkedMapOf<String, ProfileItem>()
        private val subscriptionsByGuid = mutableMapOf<String, SubscriptionItem?>()
        private var profilesByRemarks: Map<String, ProfileItem>? = null
        private var allProfiles: List<ProfileItem>? = null

        init {
            requestedGuids.forEach(::loadProfile)
        }

        fun findByGuid(guid: String): ProfileItem? =
            profilesByGuid[guid] ?: loadProfile(guid)

        fun findByRemarks(remarks: String?): ProfileItem? {
            if (remarks.isNullOrEmpty()) return null
            ensureAllProfilesLoaded()
            return profilesByRemarks?.get(remarks)
        }

        fun profiles(): List<ProfileItem> {
            ensureAllProfilesLoaded()
            return allProfiles.orEmpty()
        }

        fun subscription(guid: String): SubscriptionItem? {
            if (guid in subscriptionsByGuid) return subscriptionsByGuid[guid]
            return MmkvManager.decodeSubscription(guid).also { subscriptionsByGuid[guid] = it }
        }

        private fun loadProfile(guid: String): ProfileItem? {
            if (guid.isBlank()) return null
            return MmkvManager.decodeServerConfig(guid)?.also { profilesByGuid[guid] = it }
        }

        private fun ensureAllProfilesLoaded() {
            if (allProfiles != null) return
            val ordered = mutableListOf<ProfileItem>()
            val seenGuids = mutableSetOf<String>()
            MmkvManager.decodeAllServerList().forEach { guid ->
                val profile = findByGuid(guid) ?: return@forEach
                if (seenGuids.add(guid)) ordered += profile
            }
            profilesByGuid.forEach { (guid, profile) ->
                if (seenGuids.add(guid)) ordered += profile
            }
            allProfiles = ordered
            profilesByRemarks = buildMap {
                ordered.forEach { profile -> putIfAbsent(profile.remarks, profile) }
            }
        }
    }

    private fun findProfileByRemarks(
        lookup: ProbeProfileLookup?,
        remarks: String?,
    ): ProfileItem? = if (lookup != null) {
        lookup.findByRemarks(remarks)
    } else {
        SettingsManager.getServerViaRemarks(remarks)
    }

    /**
     * Load one profile and produce a fully analyzed context.
     *
     * Null is returned only when the selected profile cannot be loaded.
     */
    fun build(context: Context, guid: String): CoreConfigContext? {
        val config = MmkvManager.decodeServerConfig(guid) ?: return null

        return buildResolved(context, guid, config, lookup = null, includeRouting = true)
    }

    /** Build only the outbound dependency graph required by a RealDelay probe. */
    internal fun buildForProbe(
        context: Context,
        guid: String,
        lookup: ProbeProfileLookup,
    ): CoreConfigContext? {
        val config = lookup.findByGuid(guid) ?: return null
        return buildResolved(context, guid, config, lookup, includeRouting = false)
    }

    private fun buildResolved(
        context: Context,
        guid: String,
        config: ProfileItem,
        lookup: ProbeProfileLookup?,
        includeRouting: Boolean,
    ): CoreConfigContext? {
        // CUSTOM: return immediately — CoreConfigManager handles this path on its own.
        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        // Step 1: Resolve the main outbound (always tag = TAG_PROXY).
        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, config, lookup) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        // Step 2: Resolve all non-builtin routing outbound tags.
        val routingResolvedOutbounds = if (includeRouting) resolveRoutingOutbounds() else emptyList()
        val resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds
        val fallbackResolvedOutbounds = resolveFallbackOutbounds(resolvedOutbounds, lookup)
        val routingDomainRules = if (includeRouting) collectRoutingDomainRulesForDns() else emptyList()

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
    private fun resolveOutbound(
        tag: String,
        profile: ProfileItem,
        lookup: ProbeProfileLookup? = null,
    ): CoreConfigContext.ResolvedOutbound? {
        if (profile.configType == EConfigType.CUSTOM) {
            return null
        }

        val (resolvedProfiles, resolvedType) = when (profile.configType) {
            EConfigType.POLICYGROUP -> Pair(
                resolvePolicyGroupProfiles(profile, lookup),
                CoreResolvedType.POLICYGROUP,
            )

            EConfigType.PROXYCHAIN -> {
                val chainProfiles = resolveProxyChainProfiles(profile, lookup)
                val type = if (chainProfiles.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainProfiles, type)
            }

            else -> {
                val chainProfiles = resolveProxyChainProfilesFromGroup(profile, lookup)
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
                        val profile = SettingsManager.getServerViaRemarks(tag) ?: run {
                            LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                            return@forEach
                        }
                        val resolvedOutbound = resolveOutbound(tag, profile) ?: run {
                            LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                            return@forEach
                        }
                        if (resolvedOutbound.resolvedProfiles.isEmpty()) {
                            LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                            return@forEach
                        }
                        resolvedOutbounds.add(resolvedOutbound)
                        LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedProfiles.size}")
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                    }
                }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    private fun resolvePolicyGroupProfiles(
        config: ProfileItem,
        lookup: ProbeProfileLookup?,
    ): List<ProfileItem> {
        try {
            val profiles = lookup?.profiles() ?: MmkvManager.decodeAllServerList()
                .mapNotNull(MmkvManager::decodeServerConfig)
            return profiles.asSequence()
                .filter { profile ->
                    val subscriptionId = config.policyGroupSubscriptionId
                    if (subscriptionId.isNullOrBlank()) {
                        true
                    } else {
                        profile.subscriptionId == subscriptionId
                    }
                }
                .filter { profile ->
                    val filter = config.policyGroupFilter
                    if (filter.isNullOrBlank()) {
                        true
                    } else {
                        try {
                            Regex(filter).containsMatchIn(profile.remarks)
                        } catch (_: Exception) {
                            profile.remarks.contains(filter)
                        }
                    }
                }
                .filter { it.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.server!!) || Utils.isValidUrl(it.server!!) }
                .filter { !it.configType.isComplexType() }
                .toList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve policy group profiles for '${config.remarks}'", e)
            return listOf(config)
        }
    }

    private fun resolveProxyChainProfiles(
        config: ProfileItem,
        lookup: ProbeProfileLookup?,
    ): List<ProfileItem> {
        if (config.proxyChainProfiles.isNullOrBlank()) {
            return listOf(config)
        }

        try {
            return config.proxyChainProfiles.orEmpty().split(",")
                .asSequence()
                .mapNotNull { remark -> findProfileByRemarks(lookup, remark) }
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
    private fun resolveProxyChainProfilesFromGroup(
        config: ProfileItem,
        lookup: ProbeProfileLookup?,
    ): List<ProfileItem> {
        if (config.subscriptionId.isEmpty()) {
            return listOf(config)
        }

        try {
            val subItem = if (lookup != null) {
                lookup.subscription(config.subscriptionId)
            } else {
                MmkvManager.decodeSubscription(config.subscriptionId)
            } ?: return listOf(config)
            val resolved = mutableListOf<ProfileItem>()
            findProfileByRemarks(lookup, subItem.nextProfile)
                ?.let { resolved.add(it) }
            resolved.add(config)
            findProfileByRemarks(lookup, subItem.prevProfile)
                ?.let { resolved.add(it) }
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
    private fun resolveFallbackOutbounds(
        resolvedOutbounds: List<CoreConfigContext.ResolvedOutbound>,
        lookup: ProbeProfileLookup?,
    ): List<CoreConfigContext.ResolvedOutbound> {
        return resolvedOutbounds
            .asSequence()
            .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
            .filter { BalancerStrategyType.from(it.profile.policyGroupType).supportsObservatory && it.profile.policyGroupTestOutbounds != false }
            .mapNotNull { it.profile.policyGroupFallbackTag }
            .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
            .distinct()
            .mapNotNull { tag ->
                findProfileByRemarks(lookup, tag)
                    ?.takeUnless { it.configType == EConfigType.CUSTOM || it.configType == EConfigType.POLICYGROUP }
                    ?.let { resolveOutbound(tag, it, lookup) }
            }
            .toList()
    }
}
