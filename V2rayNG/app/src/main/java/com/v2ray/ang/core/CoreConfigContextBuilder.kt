package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.entities.ProfileEntry
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

/** Builds runtime outbound entries while preserving stable stored profile identities. */
object CoreConfigContextBuilder {
    fun build(context: Context, guid: String): CoreConfigContext? {
        val config = MmkvManager.decodeServerConfig(guid) ?: return null
        if (config.configType == EConfigType.CUSTOM) {
            return CoreConfigContext(context = context, guid = guid, isCustom = true)
        }

        val primary = resolveOutbound(AppConfig.TAG_PROXY, ProfileEntry(guid, config)) ?: run {
            LogUtil.e(AppConfig.TAG, "Failed to resolve main outbound for '${config.remarks}'")
            return null
        }
        val routing = resolveRoutingOutbounds()
        val resolved = listOf(primary) + routing
        val fallbacks = resolveFallbackOutbounds(resolved)
        return CoreConfigContext(
            context = context,
            guid = guid,
            resolvedOutbounds = resolved + fallbacks,
            routingDomainRules = collectRoutingDomainRulesForDns(),
        )
    }

    private fun resolveOutbound(tag: String, entry: ProfileEntry): CoreConfigContext.ResolvedOutbound? {
        if (entry.profile.configType == EConfigType.CUSTOM) return null
        val profile = entry.profile
        if (profile.configType == EConfigType.POLICYGROUP) {
            val servers = MmkvManager.decodeAllServerList().mapNotNull { id ->
                MmkvManager.decodeServerConfig(id)?.let { CoreConfigContext.ResolvedProfile(id, it) }
            }
            return resolvePolicyGroup(
                tag, entry.guid, profile, servers,
                profile.subscriptionId.takeIf { it.isNotEmpty() }?.let { MmkvManager.decodeSubscription(it) },
            )
        }
        val (entries, type) = when (profile.configType) {
            EConfigType.PROXYCHAIN -> {
                val chain = resolveProxyChainEntries(entry)
                chain to if (chain.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
            }
            else -> {
                val chain = resolveProxyChainEntriesFromGroup(entry)
                chain to if (chain.size <= 1) CoreResolvedType.NORMAL else CoreResolvedType.PROXYCHAIN
            }
        }
        return CoreConfigContext.ResolvedOutbound(tag, entry, entries, type)
    }

    private fun resolveRoutingOutbounds(): List<CoreConfigContext.ResolvedOutbound> {
        val items = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        val result = mutableListOf<CoreConfigContext.ResolvedOutbound>()
        val processed = mutableSetOf<String>()
        try {
            items.asSequence().filter { it.enabled }
                .mapNotNull { it.outboundTag.takeIf(String::isNotBlank) }
                .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS }
                .distinct().forEach { tag ->
                    if (!processed.add(tag)) return@forEach
                    try {
                        val entry = SettingsManager.getProfileEntryViaRemarks(tag) ?: run {
                            LogUtil.w(AppConfig.TAG, "Routing tag '$tag' has no matching profile — will fall back to proxy at routing time")
                            return@forEach
                        }
                        val outbound = resolveOutbound(tag, entry) ?: run {
                            LogUtil.w(AppConfig.TAG, "Cannot use CUSTOM profile as routing outbound for tag '$tag', skipping")
                            return@forEach
                        }
                        if (outbound.resolvedEntries.isEmpty()) {
                            LogUtil.w(AppConfig.TAG, "Routing outbound '$tag' resolved to empty list, skipping")
                            return@forEach
                        }
                        result.add(outbound)
                        LogUtil.d(AppConfig.TAG, "Resolved routing outbound: tag='$tag', type='${outbound.resolvedType}', profiles=${outbound.resolvedEntries.size}")
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
        return result
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
        fun valid(profile: ProfileItem) = !profile.configType.isComplexType() &&
            profile.server.isNotNullEmpty() && (Utils.isPureIpAddress(profile.server!!) || Utils.isValidUrl(profile.server!!))
        fun endpoint(remark: String?): CoreConfigContext.ResolvedProfile? {
            if (remark.isNullOrEmpty()) return null
            return servers.firstOrNull { it.profile.remarks == remark }?.takeIf { valid(it.profile) }
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
        val regex = if (filter.isNullOrBlank()) null else try { Regex(filter) } catch (_: IllegalArgumentException) { null }
        val candidates = servers.distinctBy { it.guid }.filter { server ->
            val candidate = server.profile
            (config.policyGroupSubscriptionId.isNullOrBlank() || candidate.subscriptionId == config.policyGroupSubscriptionId) &&
                (filter.isNullOrBlank() || (regex?.containsMatchIn(candidate.remarks) ?: candidate.remarks.contains(filter))) &&
                valid(candidate) && server.guid != landing?.guid && server.guid != preproxy?.guid
        }
        if (candidates.isEmpty()) throw PolicyGroupConfigException(guid, "Empty policy group")
        val fallbackRemark = config.policyGroupFallbackTag
        val fallback = if (hasChain && BalancerStrategyType.from(config.policyGroupType).supportsObservatory &&
            config.policyGroupTestOutbounds != false && !fallbackRemark.isNullOrEmpty() &&
            fallbackRemark !in AppConfig.BUILTIN_OUTBOUND_TAGS
        ) member(endpoint(fallbackRemark) ?: throw PolicyGroupConfigException(guid, "Missing fallback")) else null
        return CoreConfigContext.ResolvedOutbound(
            tag = tag,
            entry = ProfileEntry(guid, config),
            resolvedEntries = candidates.map { ProfileEntry(it.guid, it.profile) },
            resolvedType = CoreResolvedType.POLICYGROUP,
            policyGroup = CoreConfigContext.ResolvedPolicyGroup(guid, candidates.map(::member), hasChain, fallback),
        )
    }

    private fun resolveProxyChainEntries(entry: ProfileEntry): List<ProfileEntry> {
        val remarks = entry.profile.proxyChainProfileList
        if (remarks.isNullOrEmpty()) return listOf(entry)
        return try {
            remarks.asSequence().mapNotNull(SettingsManager::getProfileEntryViaRemarks)
                .filter { it.profile.server.isNotNullEmpty() }
                .filter { Utils.isPureIpAddress(it.profile.server!!) || Utils.isValidUrl(it.profile.server!!) }
                .filter { !it.profile.configType.isComplexType() }.toList().reversed()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain profiles for '${entry.profile.remarks}'", e)
            listOf(entry)
        }
    }

    private fun resolveProxyChainEntriesFromGroup(entry: ProfileEntry): List<ProfileEntry> {
        if (entry.profile.subscriptionId.isEmpty()) return listOf(entry)
        return try {
            val sub = MmkvManager.decodeSubscription(entry.profile.subscriptionId) ?: return listOf(entry)
            buildList {
                SettingsManager.getProfileEntryViaRemarks(sub.nextProfile)?.let(::add)
                add(entry)
                SettingsManager.getProfileEntryViaRemarks(sub.prevProfile)?.let(::add)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to resolve proxy chain from group for '${entry.profile.remarks}'", e)
            listOf(entry)
        }
    }

    private fun collectRoutingDomainRulesForDns(): List<CoreConfigContext.RoutingDomainRule> {
        val rules = MmkvManager.decodeRoutingRulesets() ?: return emptyList()
        return rules.asSequence().filter { it.enabled && !it.domain.isNullOrEmpty() }.map { rule ->
            val target = when (rule.outboundTag) {
                AppConfig.TAG_DIRECT -> AppConfig.TAG_DIRECT
                AppConfig.TAG_BLOCKED -> AppConfig.TAG_BLOCKED
                else -> AppConfig.TAG_PROXY
            }
            CoreConfigContext.RoutingDomainRule(rule.domain.orEmpty(), target)
        }.toList()
    }

    private fun resolveFallbackOutbounds(outbounds: List<CoreConfigContext.ResolvedOutbound>): List<CoreConfigContext.ResolvedOutbound> =
        outbounds.asSequence()
            .filter { it.resolvedType == CoreResolvedType.POLICYGROUP }
            .filter { it.policyGroup?.hasSubscriptionChain != true }
            .filter { BalancerStrategyType.from(it.entry.profile.policyGroupType).supportsObservatory && it.entry.profile.policyGroupTestOutbounds != false }
            .mapNotNull { it.entry.profile.policyGroupFallbackTag }
            .filter { it !in AppConfig.BUILTIN_OUTBOUND_TAGS && outbounds.none { item -> item.tag == it } }
            .distinct()
            .mapNotNull { remark ->
                SettingsManager.getProfileEntryViaRemarks(remark)
                    ?.takeUnless { it.profile.configType == EConfigType.CUSTOM || it.profile.configType == EConfigType.POLICYGROUP }
                    ?.let { resolveOutbound(remark, it) }
            }.toList()
}
