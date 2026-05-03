package com.v2ray.ang.core

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonArray
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ConfigResult
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.CoreResolvedType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.PackageUidResolver
import com.v2ray.ang.util.Utils

object CoreConfigManager {
    private var initConfigCache: String? = null
    private var initConfigCacheWithTun: String? = null

    //region get config function

    /**
     * Builds normal runtime config JSON for the selected profile.
     *
     * @param context The context of the caller.
     * @param guid The unique identifier for the V2ray configuration.
     * @return A ConfigResult object containing the configuration details or indicating failure.
     */
    fun getV2rayConfig(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid) ?: return ConfigResult(false)
            if (configContext.resolvedType == CoreResolvedType.CUSTOM) {
                return getV2rayCustomConfig(configContext)
            }
            val v2rayConfig = when (configContext.resolvedType) {
                CoreResolvedType.POLICYGROUP -> buildGroupConfig(configContext)
                CoreResolvedType.PROXYCHAIN -> buildProxyChainConfig(configContext)
                CoreResolvedType.NORMAL -> buildNormalConfig(configContext)
                CoreResolvedType.CUSTOM -> null
            } ?: return ConfigResult(false)

            return toConfigResult(configContext, v2rayConfig)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config", e)
            return ConfigResult(false)
        }
    }

    /**
     * Builds speedtest config for the selected profile.
     *
     * It reuses the same build flow as normal config, then removes
     * unnecessary sections for delay testing.
     *
     * @param context The context of the caller.
     * @param guid The unique identifier for the V2ray configuration.
     * @return A ConfigResult object containing the configuration details or indicating failure.
     */
    fun getV2rayConfig4Speedtest(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid) ?: return ConfigResult(false)
            if (configContext.resolvedType == CoreResolvedType.CUSTOM) {
                return getV2rayCustomConfig(configContext)
            }
            val v2rayConfig = when (configContext.resolvedType) {
                CoreResolvedType.POLICYGROUP -> buildGroupConfig(configContext)
                CoreResolvedType.PROXYCHAIN -> buildProxyChainConfig(configContext)
                CoreResolvedType.NORMAL -> buildNormalConfig(configContext)
                CoreResolvedType.CUSTOM -> null
            } ?: return ConfigResult(false)

            postProcessForSpeedtest(v2rayConfig)

            return toConfigResult(configContext, v2rayConfig)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config for speedtest", e)
            return ConfigResult(false)
        }
    }

    /**
     * Builds config result for CUSTOM profiles.
     */
    private fun getV2rayCustomConfig(configContext: CoreConfigContext): ConfigResult {
        val context = configContext.context
        val raw = MmkvManager.decodeServerRaw(configContext.guid) ?: return ConfigResult(false)
        val result = ConfigResult(true, configContext.guid, raw)
        if (!needTun()) {
            return result
        }

        val json = JsonUtil.parseString(raw)?.takeIf { it.isJsonObject }?.asJsonObject ?: return result

        // Check whether package names need to be replaced with UIDs
        if (SettingsManager.canUseProcessRouting()) {
            val rulesJson = json.get("routing")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("rules")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: JsonArray()

            for (elem in rulesJson) {
                val rule = elem.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val process = rule.get("process")?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                val packages = process.mapNotNull {
                    it.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }.takeIf { it.isNotEmpty() } ?: continue
                val uids = PackageUidResolver.packageNamesToUids(context, packages).takeIf { it.isNotEmpty() } ?: continue

                rule.add("process", JsonArray().apply { uids.forEach { add(it) } })
            }
        }

        // check if tun inbound exists
        val inboundsJson = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray().also { json.add("inbounds", it) }
        val tunNotExists = inboundsJson.none { elem ->
            elem.isJsonObject && elem.asJsonObject.get("protocol")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString == "tun"
        }

        if (tunNotExists) {
            // add tun inbound from template
            initV2rayConfig(configContext)?.let { templateConfig ->
                templateConfig.inbounds.firstOrNull { it.tag == "tun" }?.let { inboundTun ->
                    inboundTun.settings?.mtu = SettingsManager.getVpnMtu()
                    inboundsJson.add(JsonUtil.parseString(JsonUtil.toJson(inboundTun)))
                }
            }
        }

        return JsonUtil.toJsonPretty(json)?.let { ConfigResult(true, configContext.guid, it) } ?: result
    }

    /** Builds full config for policy-group mode. */
    private fun buildGroupConfig(configContext: CoreConfigContext): V2rayConfig? {
        val config = configContext.selectedProfile
        val validConfigs = configContext.resolvedProfiles

        if (validConfigs.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "All configs are invalid")
            return null
        }

        val v2rayConfig = initV2rayConfig(configContext) ?: return null
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = config.remarks

        getInbounds(v2rayConfig)

        v2rayConfig.outbounds.removeAt(0)
        val outboundsList = mutableListOf<V2rayConfig.OutboundBean>()
        var index = 0
        for (item in validConfigs) {
            index++
            val outbound = convertProfile2Outbound(item) ?: continue
            outbound.tag = "proxy-$index-${item.remarks.trim()}"
            outboundsList.add(outbound)
        }
        outboundsList.addAll(v2rayConfig.outbounds)
        v2rayConfig.outbounds = ArrayList(outboundsList)

        getRouting(configContext, v2rayConfig)
        getFakeDns(v2rayConfig)
        getDns(v2rayConfig)
        getBalance(configContext, v2rayConfig)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED)) {
            getCustomLocalDns(v2rayConfig)
        }
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED)) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

        // Resolve and add to DNS Hosts
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") == "1") {
            resolveOutboundDomainsToHosts(v2rayConfig)
        }

        return v2rayConfig
    }

    /**
     * Builds full V2Ray config for proxy-chain mode.
     *
     * Uses resolvedProfiles as an ordered chain and links each hop
     * with dialerProxy.
     */
    private fun buildProxyChainConfig(configContext: CoreConfigContext): V2rayConfig? {
        val config = configContext.selectedProfile
        val resolvedProfiles = configContext.resolvedProfiles

        val v2rayConfig = initV2rayConfig(configContext) ?: return null
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = config.remarks

        getInbounds(v2rayConfig)

        // Build and link the whole chain directly from resolvedProfiles.
        val chainOutbounds = resolvedProfiles.mapNotNull { profile ->
            convertProfile2Outbound(profile)
        }.toMutableList()
        if (chainOutbounds.size < 2) {
            LogUtil.w(AppConfig.TAG, "Proxy chain requires at least 2 valid profiles, but only ${chainOutbounds.size} found")
            return null
        }

        chainOutbounds.forEachIndexed { index, outbound ->
            outbound.tag = if (index == 0) AppConfig.TAG_PROXY else AppConfig.TAG_PROXY + index
        }
        for (index in 0 until chainOutbounds.size - 1) {
            chainOutbounds[index].ensureSockopt().dialerProxy = chainOutbounds[index + 1].tag
        }

        // Keep built-in outbounds and place the chain before them.
        val builtinOutbounds = if (v2rayConfig.outbounds.size > 1) {
            v2rayConfig.outbounds.drop(1)
        } else {
            emptyList()
        }
        v2rayConfig.outbounds = ArrayList(chainOutbounds + builtinOutbounds)

        getRouting(configContext, v2rayConfig)
        getFakeDns(v2rayConfig)
        getDns(v2rayConfig)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            getCustomLocalDns(v2rayConfig)
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

        // Resolve and add to DNS Hosts
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") == "1") {
            resolveOutboundDomainsToHosts(v2rayConfig)
        }

        return v2rayConfig
    }

    /** Builds full config for normal single-node mode. */
    private fun buildNormalConfig(configContext: CoreConfigContext): V2rayConfig? {
        val config = configContext.selectedProfile

        val address = config.server ?: return null
        if (!Utils.isPureIpAddress(address) && !Utils.isValidUrl(address)) {
            LogUtil.w(AppConfig.TAG, "$address is an invalid ip or domain")
            return null
        }

        val v2rayConfig = initV2rayConfig(configContext) ?: return null
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = config.remarks

        getInbounds(v2rayConfig)
        getOutbounds(configContext, v2rayConfig) ?: return null
        getRouting(configContext, v2rayConfig)
        getFakeDns(v2rayConfig)
        getDns(v2rayConfig)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            getCustomLocalDns(v2rayConfig)
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

        // Resolve and add to DNS Hosts
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") == "1") {
            resolveOutboundDomainsToHosts(v2rayConfig)
        }

        return v2rayConfig
    }

    /**
     * Removes non-essential sections for speedtest use.
     */
    private fun postProcessForSpeedtest(v2rayConfig: V2rayConfig) {
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.inbounds.clear()
        v2rayConfig.routing.rules.clear()
        v2rayConfig.dns = null
        v2rayConfig.fakedns = null
        v2rayConfig.stats = null
        v2rayConfig.policy = null
        v2rayConfig.outbounds.forEach { key -> key.mux = null }
    }

    /** Converts a built config object into a unified result payload. */
    private fun toConfigResult(configContext: CoreConfigContext, v2rayConfig: V2rayConfig): ConfigResult {
        return ConfigResult(
            status = true,
            guid = configContext.guid,
            content = JsonUtil.toJsonPretty(v2rayConfig) ?: ""
        )
    }

    /**
     * Initializes V2ray configuration.
     *
     * This function loads the V2ray configuration from assets or from a cached value.
     * It first attempts to use the cached configuration if available, otherwise reads
     * the configuration from the "v2ray_config.json" asset file.
     *
     * @param configContext Runtime context used to access app assets and profile data
     * @return V2rayConfig object parsed from the JSON configuration, or null if the configuration is empty
     */
    private fun initV2rayConfig(configContext: CoreConfigContext): V2rayConfig? {
        val context = configContext.context
        var assets = ""
        if (needTun()) {
            assets = initConfigCacheWithTun ?: Utils.readTextFromAssets(context, "v2ray_config_with_tun.json")
            if (TextUtils.isEmpty(assets)) {
                return null
            }
            initConfigCacheWithTun = assets
        } else {
            assets = initConfigCache ?: Utils.readTextFromAssets(context, "v2ray_config.json")
            if (TextUtils.isEmpty(assets)) {
                return null
            }
            initConfigCache = assets
        }
        val config = JsonUtil.fromJson(assets, V2rayConfig::class.java)
        return config
    }


    //endregion


    //region some sub function

    private fun needTun(): Boolean {
        return SettingsManager.isVpnMode() && !SettingsManager.isUsingHevTun()
    }

    /**
     * Configures the inbound settings for V2ray.
     *
     * This function sets up the listening ports, sniffing options, and other inbound-related configurations.
     *
     * @param v2rayConfig The V2ray configuration object to be modified
     * @return true if inbound configuration was successful, false otherwise
     */
    private fun getInbounds(v2rayConfig: V2rayConfig): Boolean {
        try {
            val vpn = SettingsManager.isVpnMode()
            val useHev = SettingsManager.isUsingHevTun()
            val forcedByHev = vpn && useHev

            val enableLocalProxy = forcedByHev || MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)

            val socksPort = SettingsManager.getSocksPort()
            val socksUsername = SettingsManager.getSocksUsername()
            val socksPassword = SettingsManager.getSocksPassword()
            val inbound1 = v2rayConfig.inbounds[0]
            if (inbound1.settings == null) {
                inbound1.settings = V2rayConfig.InboundBean.InSettingsBean()
            }

            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) != true) {
                inbound1.listen = AppConfig.LOOPBACK
            }
            inbound1.port = socksPort
            inbound1.settings?.udp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SOCKS_ENABLE_UDP, true)
            if (socksUsername != null && socksPassword != null) {
                inbound1.settings?.auth = "password"
                inbound1.settings?.accounts = listOf(
                    V2rayConfig.InboundBean.InSettingsBean.SocksAccountBean(
                        user = socksUsername,
                        pass = socksPassword
                    )
                )
            } else {
                inbound1.settings?.auth = "noauth"
                inbound1.settings?.accounts = null
            }
            val fakedns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
            val sniffAllTlsAndHttp =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true) != false
            inbound1.sniffing?.enabled = fakedns || sniffAllTlsAndHttp
            inbound1.sniffing?.routeOnly =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
            if (!sniffAllTlsAndHttp) {
                inbound1.sniffing?.destOverride?.clear()
            }
            if (fakedns) {
                inbound1.sniffing?.destOverride?.add("fakedns")
            }

            if (!Utils.isXray()) {
                val inbound2 = JsonUtil.fromJson(JsonUtil.toJson(inbound1), V2rayConfig.InboundBean::class.java) ?: return false
                inbound2.tag = EConfigType.HTTP.name.lowercase()
                inbound2.port = SettingsManager.getHttpPort()
                inbound2.protocol = EConfigType.HTTP.name.lowercase()
                inbound2.settings?.auth = null
                inbound2.settings?.udp = null
                v2rayConfig.inbounds.add(inbound2)
            }

            if (!enableLocalProxy) {
                v2rayConfig.inbounds.removeIf { it.protocol == "socks" || it.protocol == "http" }
            }

            if (needTun()) {
                val inboundTun = v2rayConfig.inbounds.firstOrNull { e -> e.tag == "tun" }
                inboundTun?.settings?.mtu = SettingsManager.getVpnMtu()
                inboundTun?.sniffing = inbound1.sniffing
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to configure inbounds", e)
            return false
        }
        return true
    }

    /**
     * Configures the fake DNS settings if enabled.
     *
     * Adds FakeDNS configuration to v2rayConfig if both local DNS and fake DNS are enabled.
     *
     * @param v2rayConfig The V2ray configuration object to be modified
     */
    private fun getFakeDns(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true
            && MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
        ) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
        }
    }

    /**
     * Injects custom outbound profiles into the V2ray configuration.
     * Uses pre-resolved profiles from the context instead of re-parsing.
     *
     * @param v2rayConfig The V2ray configuration to modify
     * @param customOutbounds Pre-resolved custom outbound profiles (tag -> ProfileItem mapping)
     */
    private fun injectCustomOutbounds(v2rayConfig: V2rayConfig, customOutbounds: Map<String, ProfileItem>) {
        val existingTags = v2rayConfig.outbounds.mapTo(mutableSetOf()) { it.tag }

        customOutbounds.forEach { (tag, profile) ->
            if (tag in existingTags) return@forEach
            try {
                val outbound = convertProfile2Outbound(profile) ?: run {
                    LogUtil.w(AppConfig.TAG, "Could not convert profile '$tag' to outbound, skipping")
                    return@forEach
                }
                outbound.tag = tag
                v2rayConfig.outbounds.add(outbound)
                existingTags.add(tag)
                LogUtil.d(AppConfig.TAG, "Injected custom outbound: tag='$tag'")
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to inject custom outbound for tag '$tag', skipping", e)
            }
        }
    }

    /**
     * Configures routing settings for V2ray.
     *
     * Sets up the domain strategy, injects custom outbounds from rulesets, and adds routing rules.
     *
     * @param configContext Configuration context with custom outbound profiles
     * @param v2rayConfig The V2ray configuration object to be modified
     * @return true if routing configuration was successful, false otherwise
     */
    private fun getRouting(configContext: CoreConfigContext, v2rayConfig: V2rayConfig): Boolean {
        try {

            v2rayConfig.routing.domainStrategy =
                MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
                    ?: "AsIs"

            // Inject custom outbound profiles from routing rulesets
            injectCustomOutbounds(v2rayConfig, configContext.customOutboundProfiles)

            val rulesetItems = MmkvManager.decodeRoutingRulesets()
            rulesetItems?.forEach { key ->
                getRoutingUserRule(configContext, key, v2rayConfig)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to configure routing", e)
            return false
        }
        return true
    }

    /**
     * Adds a specific ruleset item to the routing configuration.
     *
     * @param configContext Runtime context used by routing helpers
     * @param item The ruleset item to add
     * @param v2rayConfig The V2ray configuration object to be modified
     */
    private fun getRoutingUserRule(configContext: CoreConfigContext, item: RulesetItem?, v2rayConfig: V2rayConfig) {
        val context = configContext.context
        try {
            if (item == null || !item.enabled) {
                return
            }

            val rule = JsonUtil.fromJson(JsonUtil.toJson(item), V2rayConfig.RoutingBean.RulesBean::class.java) ?: return

            // Replace specific geoip rules with ext versions
            rule.ip?.let { ipList ->
                val updatedIpList = ArrayList<String>()
                ipList.forEach { ip ->
                    when (ip) {
                        AppConfig.GEOIP_CN -> updatedIpList.add("ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn")
                        AppConfig.GEOIP_PRIVATE -> updatedIpList.add("ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private")
                        else -> updatedIpList.add(ip)
                    }
                }
                rule.ip = updatedIpList
            }

            if (SettingsManager.canUseProcessRouting()) {
                // Convert process package names to UIDs
                rule.process?.let { processList ->
                    if (processList.isNotEmpty()) {
                        val uids = PackageUidResolver.packageNamesToUids(context, processList)
                        rule.process = uids.ifEmpty { null }
                    }
                }
            } else {
                rule.process = null
            }

            // If the outbound tag is a custom one that failed to inject, fall back to proxy
            val outboundTag = rule.outboundTag
            if (!outboundTag.isNullOrBlank()
                && outboundTag !in AppConfig.BUILTIN_OUTBOUND_TAGS
                && v2rayConfig.outbounds.none { it.tag == outboundTag }
            ) {
                LogUtil.w(AppConfig.TAG, "Outbound tag '$outboundTag' not found, falling back to '${AppConfig.TAG_PROXY}'")
                rule.outboundTag = AppConfig.TAG_PROXY
            }

            v2rayConfig.routing.rules.add(rule)

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to apply routing user rule", e)
        }
    }

    /**
     * Retrieves domain rules for a specific outbound tag.
     *
     * Searches through all rulesets to find domains targeting the specified tag.
     *
     * @param tag The outbound tag to search for
     * @return ArrayList of domain rules matching the tag
     */
    private fun getUserRule2Domain(tag: String): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && key.outboundTag == tag && !key.domain.isNullOrEmpty()) {
                key.domain?.forEach {
                    domain.add(it)
                }
            }
        }

        return domain
    }

    /**
     * Retrieves domain rules for custom outbound tags.
     *
     * Searches through all rulesets to find domains targeting any custom outbound tags.
     *
     * @return ArrayList of domain rules matching custom outbound tags
     */
    private fun getCustomOutboundUserRule2Domain(): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && !AppConfig.BUILTIN_OUTBOUND_TAGS.contains(key.outboundTag)
                && !key.domain.isNullOrEmpty()
            ) {
                key.domain?.forEach {
                    domain.add(it)
                }
            }
        }

        return domain
    }

    /**
     * Configures custom local DNS settings.
     *
     * Sets up DNS inbound, outbound, and routing rules for local DNS resolution.
     *
     * @param v2rayConfig The V2ray configuration object to be modified
     * @return true if custom local DNS configuration was successful, false otherwise
     */
    private fun getCustomLocalDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
                val geositeCn = arrayListOf(AppConfig.GEOSITE_CN)
                val proxyDomain = getUserRule2Domain(AppConfig.TAG_PROXY)
                val directDomain = getUserRule2Domain(AppConfig.TAG_DIRECT)
                val finalDomain = geositeCn.plus(proxyDomain).plus(directDomain).distinct()
                // fakedns with all domains to make it always top priority
                v2rayConfig.dns?.servers?.add(
                    0,
                    V2rayConfig.DnsBean.ServersBean(
                        address = "fakedns",
                        domains = finalDomain
                    )
                )
            }

            if (SettingsManager.isVpnMode()) {
                if (SettingsManager.isUsingHevTun()) {
                    //hev-socks5-tunnel dns routing
                    v2rayConfig.routing.rules.add(
                        0, V2rayConfig.RoutingBean.RulesBean(
                            inboundTag = arrayListOf("socks"),
                            outboundTag = "dns-out",
                            port = "53",
                        )
                    )
                } else {
                    v2rayConfig.routing.rules.add(
                        0, V2rayConfig.RoutingBean.RulesBean(
                            inboundTag = arrayListOf("tun"),
                            outboundTag = "dns-out",
                            port = "53",
                        )
                    )
                }
            }

            // DNS outbound
            if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
                v2rayConfig.outbounds.add(
                    V2rayConfig.OutboundBean(
                        protocol = "dns",
                        tag = "dns-out",
                        settings = null,
                        streamSettings = null,
                        mux = null
                    )
                )
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to configure custom local DNS", e)
            return false
        }
        return true
    }

    /**
     * Configures the DNS settings for V2ray.
     *
     * Sets up DNS servers, hosts, and routing rules for DNS resolution.
     *
     * @param v2rayConfig The V2ray configuration object to be modified
     * @return true if DNS configuration was successful, false otherwise
     */
    private fun getDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val hosts = mutableMapOf<String, Any>()
            val servers = ArrayList<Any>()

            //remote Dns
            val remoteDns = SettingsManager.getRemoteDnsServers()
            val proxyDomain = (getUserRule2Domain(AppConfig.TAG_PROXY) + getCustomOutboundUserRule2Domain()).distinct()
            remoteDns.forEach {
                servers.add(it)
            }
            if (proxyDomain.isNotEmpty()) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = remoteDns.first(),
                        domains = proxyDomain,
                    )
                )
            }

            // domestic DNS
            val domesticDns = SettingsManager.getDomesticDnsServers()
            val directDomain = getUserRule2Domain(AppConfig.TAG_DIRECT)
            val isCnRoutingMode = directDomain.contains(AppConfig.GEOSITE_CN)
            val cnRegionFilter = { domain: String ->
                domain.startsWith("geosite:") && (domain.endsWith("-cn") || domain.endsWith("@cn"))
                        || domain == AppConfig.GEOSITE_CN
            }
            val finalDirectDomain = if (isCnRoutingMode) directDomain.filterNot {
                cnRegionFilter(it)
            } else directDomain
            val domesticDnsTags = mutableListOf<String>()
            domesticDns.forEachIndexed { index, element ->
                val tag = AppConfig.TAG_DOMESTIC_DNS + index
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = element,
                        domains = finalDirectDomain,
                        skipFallback = true,
                        tag = tag
                    )
                )
                domesticDnsTags.add(tag)
            }
            if (isCnRoutingMode) {
                val geoipCn = arrayListOf(AppConfig.GEOIP_CN)
                val cnRegionDomain = directDomain.filter { cnRegionFilter(it) }
                domesticDns.forEachIndexed { index, element ->
                    val geositeCnDnsTag = AppConfig.TAG_DOMESTIC_DNS + index + "_cn_expect"
                    servers.add(
                        V2rayConfig.DnsBean.ServersBean(
                            address = element,
                            domains = cnRegionDomain,
                            expectIPs = geoipCn,
                            skipFallback = true,
                            tag = geositeCnDnsTag
                        )
                    )
                    domesticDnsTags.add(geositeCnDnsTag)
                }
            }

            //block dns
            val blkDomain = getUserRule2Domain(AppConfig.TAG_BLOCKED)
            if (blkDomain.isNotEmpty()) {
                hosts.putAll(blkDomain.map { it to AppConfig.LOOPBACK })
            }

            // hardcode googleapi rule to fix play store problems
            hosts[AppConfig.GOOGLEAPIS_CN_DOMAIN] = AppConfig.GOOGLEAPIS_COM_DOMAIN

            // hardcode popular Android Private DNS rule to fix localhost DNS problem
            hosts[AppConfig.DNS_ALIDNS_DOMAIN] = AppConfig.DNS_ALIDNS_ADDRESSES
            hosts[AppConfig.DNS_CLOUDFLARE_ONE_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONE_ADDRESSES
            hosts[AppConfig.DNS_CLOUDFLARE_DNS_COM_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_COM_ADDRESSES
            hosts[AppConfig.DNS_CLOUDFLARE_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_ADDRESSES
            hosts[AppConfig.DNS_DNSPOD_DOMAIN] = AppConfig.DNS_DNSPOD_ADDRESSES
            hosts[AppConfig.DNS_GOOGLE_DOMAIN] = AppConfig.DNS_GOOGLE_ADDRESSES
            hosts[AppConfig.DNS_QUAD9_DOMAIN] = AppConfig.DNS_QUAD9_ADDRESSES
            hosts[AppConfig.DNS_YANDEX_DOMAIN] = AppConfig.DNS_YANDEX_ADDRESSES

            //User DNS hosts
            try {
                val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
                if (userHosts.isNotNullEmpty()) {
                    var userHostsMap = userHosts?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.filter { it.contains(":") }
                        ?.associate { it.split(":").let { (k, v) -> k to v } }
                    if (userHostsMap != null) hosts.putAll(userHostsMap)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to configure user DNS hosts", e)
            }

            // DNS dns
            v2rayConfig.dns = V2rayConfig.DnsBean(
                servers = servers,
                hosts = hosts,
                tag = AppConfig.TAG_DNS,
                enableParallelQuery = if ((domesticDns.size + remoteDns.size) > 2) true else null
            )

            // DNS routing
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_DIRECT,
                    inboundTag = domesticDnsTags,
                    domain = null
                )
            )
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_PROXY,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to configure DNS", e)
            return false
        }
        return true
    }


    //endregion


    //region outbound related functions

    /**
     * Configures the primary outbound connection.
     *
     * Converts the profile to an outbound configuration and applies global settings.
     *
     * @param configContext Runtime context containing the selected profile
     * @param v2rayConfig The V2ray configuration object to be modified
     * @return true if outbound configuration was successful, null if there was an error
     */
    private fun getOutbounds(configContext: CoreConfigContext, v2rayConfig: V2rayConfig): Boolean? {
        val outbound = convertProfile2Outbound(configContext.selectedProfile) ?: return null

        if (v2rayConfig.outbounds.isNotEmpty()) {
            v2rayConfig.outbounds[0] = outbound
        } else {
            v2rayConfig.outbounds.add(outbound)
        }
        return true
    }

    /**
     * Configures load balancing settings for the V2ray configuration.
     *
     * @param configContext Runtime context containing policy group settings
     * @param v2rayConfig The V2ray configuration object to be modified with balancing settings
     */
    private fun getBalance(configContext: CoreConfigContext, v2rayConfig: V2rayConfig) {
        val config = configContext.selectedProfile
        try {
            v2rayConfig.routing.rules.forEach { rule ->
                if (rule.outboundTag == AppConfig.TAG_PROXY) {
                    rule.outboundTag = null
                    rule.balancerTag = AppConfig.TAG_BALANCER
                }
            }

            val lstSelector = listOf("proxy-")
            when (config.policyGroupType) {
                // Least Ping goto else
                "1" -> {
                    // Least Load
                    val balancer = V2rayConfig.RoutingBean.BalancerBean(
                        tag = AppConfig.TAG_BALANCER,
                        selector = lstSelector,
                        strategy = V2rayConfig.RoutingBean.StrategyObject(
                            type = "leastLoad"
                        )
                    )
                    v2rayConfig.routing.balancers = listOf(balancer)
                    v2rayConfig.burstObservatory = V2rayConfig.BurstObservatoryObject(
                        subjectSelector = lstSelector,
                        pingConfig = V2rayConfig.BurstObservatoryObject.PingConfigObject(
                            destination = MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL) ?: AppConfig.DELAY_TEST_URL,
                            interval = "5m",
                            sampling = 2,
                            timeout = "30s"
                        )
                    )
                }

                "2" -> {
                    // Random
                    val balancer = V2rayConfig.RoutingBean.BalancerBean(
                        tag = AppConfig.TAG_BALANCER,
                        selector = lstSelector,
                        strategy = V2rayConfig.RoutingBean.StrategyObject(
                            type = "random"
                        )
                    )
                    v2rayConfig.routing.balancers = listOf(balancer)
                }

                "3" -> {
                    // Round Robin
                    val balancer = V2rayConfig.RoutingBean.BalancerBean(
                        tag = AppConfig.TAG_BALANCER,
                        selector = lstSelector,
                        strategy = V2rayConfig.RoutingBean.StrategyObject(
                            type = "roundRobin"
                        )
                    )
                    v2rayConfig.routing.balancers = listOf(balancer)
                }

                else -> {
                    // Default: Least Ping
                    val balancer = V2rayConfig.RoutingBean.BalancerBean(
                        tag = AppConfig.TAG_BALANCER,
                        selector = lstSelector,
                        strategy = V2rayConfig.RoutingBean.StrategyObject(
                            type = "leastPing"
                        )
                    )
                    v2rayConfig.routing.balancers = listOf(balancer)
                    v2rayConfig.observatory = V2rayConfig.ObservatoryObject(
                        subjectSelector = lstSelector,
                        probeUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL) ?: AppConfig.DELAY_TEST_URL,
                        probeInterval = "3m",
                        enableConcurrency = true
                    )
                }
            }

            if (v2rayConfig.routing.domainStrategy == "IPIfNonMatch") {
                v2rayConfig.routing.rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        ip = arrayListOf("0.0.0.0/0", "::/0"),
                        balancerTag = AppConfig.TAG_BALANCER,
                    )
                )
            } else {
                v2rayConfig.routing.rules.add(
                    V2rayConfig.RoutingBean.RulesBean(
                        network = "tcp,udp",
                        balancerTag = AppConfig.TAG_BALANCER,
                    )
                )
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to configure balance", e)
        }
    }

    /**
     * Resolves domain names to IP addresses in outbound connections.
     *
     * Pre-resolves domains to improve connection speed and reliability.
     *
     * @param v2rayConfig The V2ray configuration object to be modified
     */
    private fun resolveOutboundDomainsToHosts(v2rayConfig: V2rayConfig) {
        val proxyOutboundList = v2rayConfig.getAllProxyOutbound()
        val dns = v2rayConfig.dns ?: return
        val newHosts = dns.hosts?.toMutableMap() ?: mutableMapOf()
        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true

        for (item in proxyOutboundList) {
            val domain = item.getServerAddress()
            if (domain.isNullOrEmpty()) continue

            if (newHosts.containsKey(domain)) {
                item.ensureSockopt().domainStrategy = "UseIP"
                item.ensureSockopt().happyEyeballs = V2rayConfig.OutboundBean.StreamSettingsBean.HappyEyeballsBean(
                    prioritizeIPv6 = preferIpv6,
                    interleave = 2
                )
                continue
            }

            val resolvedIps = HttpUtil.resolveHostToIP(domain, preferIpv6)
            if (resolvedIps.isNullOrEmpty()) continue

            item.ensureSockopt().domainStrategy = "UseIP"
            item.ensureSockopt().happyEyeballs = V2rayConfig.OutboundBean.StreamSettingsBean.HappyEyeballsBean(
                prioritizeIPv6 = preferIpv6,
                interleave = 2
            )
            newHosts[domain] = if (resolvedIps.size == 1) {
                resolvedIps[0]
            } else {
                resolvedIps
            }
        }

        dns.hosts = newHosts
    }

    /**
     * Converts a profile item to an outbound configuration.
     *
     * Delegates to [CoreOutboundBuilder] which owns all per-protocol
     * conversion logic, keeping this manager focused on config orchestration.
     *
     * @param profileItem The profile item to convert
     * @return OutboundBean configuration for the profile, or null if not supported
     */
    private fun convertProfile2Outbound(profileItem: ProfileItem): V2rayConfig.OutboundBean? {
        return CoreOutboundBuilder.convert(profileItem)
    }

    //endregion
}