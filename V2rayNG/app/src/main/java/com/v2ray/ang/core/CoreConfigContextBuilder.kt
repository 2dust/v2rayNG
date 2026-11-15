package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileEntry
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
        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, ProfileEntry(guid, config)) ?: run {
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
    private fun resolveOutbound(tag: String, entry: ProfileEntry): CoreConfigContext.ResolvedOutbound? {
        if (entry.profile.configType == EConfigType.CUSTOM) {
            return null
        }

        val (resolvedEntries, resolvedType) = when (entry.profile.configType) {
            EConfigType.POLICYGROUP -> Pair(
                resolvePolicyGroupEntries(entry),
                CoreResolvedType.POLICYGROUP,
            )

            EConfigType.PROXYCHAIN -> {
                val chainEntries = resolveProxyChainEntries(entry)
                val type = if (chainEntries.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainEntries, type)
            }

            else -> {
                val chainEntries = resolveProxyChainEntriesFromGroup(entry)
                val type = if (chainEntries.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
                Pair(chainEntries, type)
            }
        }

        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            entry = entry,
            resolvedEntries = resolvedEntries,
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
                        val entry = SettingsManager.getProfileEntryViaRemarks(tag) ?: run {
                            LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                            return@forEach
                        }
                        val resolvedOutbound = resolveOutbound(tag, entry) ?: run {
                            LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                            return@forEach
                        }
                        if (resolvedOutbound.resolvedEntries.isEmpty()) {
                            LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                            return@forEach
                        }
                        resolvedOutbounds.add(resolvedOutbound)
                        LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${resolvedOutbound.resolvedType}', profiles=${resolvedOutbound.resolvedEntries.size}")
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbound for tag '$tag', skipping", e)
                    }
                }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve routing outbounds from rulesets", e)
        }

        return resolvedOutbounds
    }

    private fun resolvePolicyGroupEntries(entry: ProfileEntry): List<ProfileEntry> {
        try {
            val serverList = MmkvManager.decodeAllServerList()
            return serverList
                .asSequence()
                .mapNotNull { id -> MmkvManager.decodeServerConfig(id)?.let { ProfileEntry(id, it) } }
                .filter {
                    val subscriptionId = entry.profile.policyGroupSubscriptionId
                    if (subscriptionId.isNullOrBlank()) {
                        true
                    } else {
                        it.profile.subscriptionId == subscriptionId
                    }
                }
                .filter {
                    val filter = entry.profile.policyGroupFilter
                    if (filter.isNullOrBlank()) {
                        true
                    } else {
                        try {
                            Regex(filter).containsMatchIn(it.profile.remarks)
                        } catch (_: Exception) {
                            it.profile.remarks.contains(filter)
                        }
                    }
                }
                .filter { it.profile.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.profile.server!!) || Utils.isValidUrl(it.profile.server!!) }
                .filter { !it.profile.configType.isComplexType() }
                .toList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve policy group profiles for '${entry.profile.remarks}'", e)
            return listOf(entry)
        }
    }

    private fun resolveProxyChainEntries(entry: ProfileEntry): List<ProfileEntry> {
        if (entry.profile.proxyChainProfiles.isNullOrBlank()) {
            return listOf(entry)
        }

        try {
            return entry.profile.proxyChainProfiles.orEmpty().split(",")
                .asSequence()
                .mapNotNull { remark -> SettingsManager.getProfileEntryViaRemarks(remark) }
                .filter { it.profile.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.profile.server!!) || Utils.isValidUrl(it.profile.server!!) }
                .filter { !it.profile.configType.isComplexType() }
                .toList()
                .reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${entry.profile.remarks}'", e)
            return listOf(entry)
        }
    }

    /**
     * Resolve chain nodes from subscription neighbors in order: next, current, prev.
     *
     * When no chain is available, return a single-node result.
     */
    private fun resolveProxyChainEntriesFromGroup(entry: ProfileEntry): List<ProfileEntry> {
        if (entry.profile.subscriptionId.isEmpty()) {
            return listOf(entry)
        }

        try {
            val subItem = MmkvManager.decodeSubscription(entry.profile.subscriptionId) ?: return listOf(entry)
            val resolved = mutableListOf<ProfileEntry>()
            SettingsManager.getProfileEntryViaRemarks(subItem.nextProfile)?.let { resolved.add(it) }
            resolved.add(entry)
            SettingsManager.getProfileEntryViaRemarks(subItem.prevProfile)?.let { resolved.add(it) }
            return resolved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${entry.profile.remarks}'", e)
            return listOf(entry)
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
            .filter { BalancerStrategyType.from(it.entry.profile.policyGroupType).supportsObservatory && it.entry.profile.policyGroupTestOutbounds != false }
            .mapNotNull { it.entry.profile.policyGroupFallbackTag }
            .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && resolvedOutbounds.none { outbound -> outbound.tag == it } }
            .distinct()
            .mapNotNull { tag ->
                SettingsManager.getProfileEntryViaRemarks(tag)
                    ?.takeUnless { it.profile.configType == EConfigType.CUSTOM || it.profile.configType == EConfigType.POLICYGROUP }
                    ?.let { resolveOutbound(tag, it) }
            }
            .toList()
    }
}
