package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileItem
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
        val primaryResolvedOutbound = resolveOutbound(AppConfig.TAG_PROXY, config) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }

        // Step 2: Resolve all non-builtin routing outbound tags.
        val routingResolvedOutbounds = resolveRoutingOutbounds()

        return CoreConfigContext(
            context = context,
            guid = guid,
            resolvedOutbounds = listOf(primaryResolvedOutbound) + routingResolvedOutbounds,
        )
    }

    /**
     * Resolve one outbound target into a normalized outbound entry.
     *
     * Custom profiles are ignored at this stage and produce no entry.
     */
    private fun resolveOutbound(tag: String, profile: ProfileItem): CoreConfigContext.ResolvedOutbound? {
        if (profile.configType == EConfigType.CUSTOM) {
            return null
        }

        val (resolvedProfiles, resolvedType) = when (profile.configType) {
            EConfigType.POLICYGROUP -> Pair(
                resolvePolicyGroupProfiles(profile),
                CoreResolvedType.POLICYGROUP,
            )

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

    private fun resolvePolicyGroupProfiles(config: ProfileItem): List<ProfileItem> {
        try {
            val serverList = MmkvManager.decodeAllServerList()
            return serverList
                .asSequence()
                .mapNotNull { id -> MmkvManager.decodeServerConfig(id) }
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
}
