package com.v2ray.ang.core

import android.content.Context
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ConfigResult
import com.v2ray.ang.dto.CoreConfigContext
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.BalancerStrategyType
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
     * Build the runtime configuration for normal startup.
     */
    fun getV2rayConfig(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid)
                ?: return ConfigResult(
                    status = false,
                    guid = guid,
                    errorMessage = "Failed to build config context"
                )
            if (configContext.isCustom) {
                return buildV2rayCustomConfig(configContext, routeAether = true)
            }
            val dependency = AetherDependency.of(configContext.resolvedOutbounds)
            aetherFailure(context, guid, dependency)?.let { return it }
            if (takesExitNodeName(dependency, configContext.resolvedOutbounds)) return exitNodeNameFailure(context, guid)
            val v2rayConfig = buildUnifiedConfig(configContext)
            // Matixray: what the Aether core sends out leaves through Xray.
            val secondaryPort = AetherCoreManager.secondarySocksPort
            val core = (dependency as? AetherDependency.Single)?.core?.let {
                if (lacksChainHop(it, v2rayConfig.outbounds)) return chainHopFailure(context, guid)
                routeAetherThroughXray(v2rayConfig, it, secondaryPort) ?: return secondaryPortFailure(context, guid, secondaryPort)
            }
            return toConfigResult(context, configContext, v2rayConfig, core)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config", e)
            return ConfigResult(
                status = false,
                guid = guid,
                errorMessage = "Failed to get V2ray config: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Build a lightweight configuration for latency testing.
     *
     * The core flow is reused, then non-essential sections are removed. A configuration that runs
     * on an Aether core dials it where it listens, whether the session's or a test's runs there.
     */
    fun getV2rayConfig4Speedtest(context: Context, guid: String): ConfigResult {
        try {
            val configContext = CoreConfigContextBuilder.build(context, guid)
                ?: return ConfigResult(
                    status = false,
                    guid = guid,
                    errorMessage = "Failed to build config context"
                )
            if (configContext.isCustom) {
                return buildV2rayCustomConfig(configContext)
            }
            // Only the primary outbound is measured; the routing outbounds lose their rules below.
            val dependency = AetherDependency.of(configContext.resolvedOutbounds.take(1))
            aetherFailure(context, guid, dependency)?.let { return it }
            if (takesExitNodeName(dependency, configContext.resolvedOutbounds)) return exitNodeNameFailure(context, guid)
            val v2rayConfig = buildUnifiedConfig(configContext)
            postProcessForSpeedtest(v2rayConfig)

            // Not routed through an inbound of this configuration: a test's core of its own dials out through
            // the exit of the process's Xray, see AetherCoreManager.exitConfiguration, and the session's core,
            // which a test may measure through, through the session's inbound.
            val core = (dependency as? AetherDependency.Single)?.core
            if (core != null && lacksChainHop(core, v2rayConfig.outbounds)) return chainHopFailure(context, guid)
            return toConfigResult(context, configContext, v2rayConfig, core)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to get V2ray config for speedtest", e)
            return ConfigResult(
                status = false,
                guid = guid,
                errorMessage = "Failed to get V2ray config: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Build configuration for custom profiles.
     *
     * A custom configuration asks for an Aether core with its command line as aetherCommand; the
     * result names that core. With [routeAether], for the session and for an export, what the core
     * sends out leaves through Xray, see [AetherDependency.routeThroughXray].
     */
    private fun buildV2rayCustomConfig(
        configContext: CoreConfigContext,
        routeAether: Boolean = false,
    ): ConfigResult {
        val context = configContext.context
        val raw = MmkvManager.decodeServerRaw(configContext.guid)
            ?: return ConfigResult(
                status = false,
                guid = configContext.guid,
                errorMessage = "Failed to build config context, config is empty"
            )
        val result = ConfigResult(true, configContext.guid, raw)

        val json = JsonUtil.parseString(raw)?.takeIf { it.isJsonObject }?.asJsonObject ?: return result

        val dependency = AetherDependency.ofCustom(json)
        aetherFailure(context, configContext.guid, dependency)?.let { return it }
        if (dependency is AetherDependency.Single) {
            result.aetherCore = dependency.core
        }

        // Inject or remove traffic statistics configuration based on user preference
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) == true) {
            if (!json.has("stats")) {
                json.add("stats", JsonObject())
            }
            if (!json.has("policy")) {
                val policyObj = JsonObject()
                val systemObj = JsonObject()
                systemObj.addProperty("statsOutboundUplink", true)
                systemObj.addProperty("statsOutboundDownlink", true)
                policyObj.add("system", systemObj)
                json.add("policy", policyObj)
            }
        } else {
            json.remove("stats")
            // Keep user-defined policy levels, only strip the stats-related system block
            json.get("policy")?.takeIf { it.isJsonObject }?.asJsonObject?.let { policy ->
                policy.remove("system")
                if (policy.entrySet().isEmpty()) {
                    json.remove("policy")
                }
            }
        }

        // Last, so that the entries for the Aether core go after everything else the app adds.
        fun serialized(): ConfigResult {
            val core = result.aetherCore
            if (routeAether && core != null) {
                val secondaryPort = AetherCoreManager.secondarySocksPort
                when (val routing = AetherDependency.routeThroughXray(json, core, secondaryPort)) {
                    is AetherDependency.Routing.Routed -> result.aetherCore = routing.core
                    AetherDependency.Routing.PortTaken -> return secondaryPortFailure(context, configContext.guid, secondaryPort)
                    is AetherDependency.Routing.ExitNodeSelected ->
                        return exitNodeSelectorFailure(context, configContext.guid, routing.selector)
                }
            }
            return JsonUtil.toJsonPretty(json)?.let { result.copy(content = it) } ?: result
        }

        if (!needTun()) {
            return serialized()
        }

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
            val templateConfig = initV2rayConfig(configContext)
            templateConfig.inbounds.firstOrNull { it.tag == "tun" }?.let { inboundTun ->
                inboundTun.settings?.mtu = SettingsManager.getVpnMtu()
                // The injected inbound must be able to map the fake IPs of a fakedns config back
                // to domains; the user's own inbounds are left as written
                if (hasFakeDnsServer(json)) {
                    inboundTun.sniffing?.destOverride?.let { if ("fakedns" !in it) it.add("fakedns") }
                }
                inboundsJson.add(JsonUtil.parseString(JsonUtil.toJson(inboundTun)))
            }
        }

        return serialized()
    }

    /**
     * Check whether the DNS servers of a custom config include fakedns, written either as the
     * plain "fakedns" string or as a server object whose address is "fakedns".
     */
    private fun hasFakeDnsServer(json: JsonObject): Boolean {
        val servers = json.get("dns")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("servers")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return false
        return servers.any { server ->
            val address = if (server.isJsonObject) server.asJsonObject.get("address") else server
            address != null && address.isJsonPrimitive && address.asJsonPrimitive.isString
                    && address.asString == "fakedns"
        }
    }

    /**
     * Build one unified configuration for every non-custom profile type.
     *
     * The analyzed outbound plan is consumed in order and converted to concrete
     * outbounds before routing, DNS, and runtime extras are assembled.
     */
    private fun buildUnifiedConfig(configContext: CoreConfigContext): V2rayConfig {
        require(configContext.resolvedOutbounds.isNotEmpty()) { "resolvedOutbounds must not be empty for a non-CUSTOM context" }
        val primaryResolvedOutbound = configContext.resolvedOutbounds.first()

        val v2rayConfig = initV2rayConfig(configContext)
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = primaryResolvedOutbound.profile.remarks

        configureInbounds(v2rayConfig)

        if (v2rayConfig.outbounds.isNotEmpty()) {
            v2rayConfig.outbounds.removeAt(0)
        }
        val existingTags = v2rayConfig.outbounds.mapTo(mutableSetOf()) { it.tag }
        val policyGroupBalancerTags = mutableMapOf<String, String>()
        val balancerStrategies = mutableListOf<BalancerStrategy>()

        // resolvedOutbounds is a single ordered plan: index 0 is primary and must be prepended,
        // the rest are routing outbounds and can be appended.
        configContext.resolvedOutbounds.forEachIndexed { index, spec ->
            buildOutbounds(
                resolvedOutbound = spec,
                prepend = index == 0,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
                policyGroupBalancerTags = policyGroupBalancerTags,
                balancerStrategies = balancerStrategies,
            )
        }

        // User routing rules (policyGroupBalancerTags rewrites TAG_PROXY→balancer when main is POLICYGROUP).
        configureRouting(configContext, v2rayConfig, policyGroupBalancerTags)
        configureDns(configContext, v2rayConfig, policyGroupBalancerTags)
        configureLocalDns(configContext, v2rayConfig)
        configureRootModeDns(v2rayConfig)

        // (added by getDns / getCustomLocalDns) to use the balancer, then add
        // the catch-all balancer rule.
        if (primaryResolvedOutbound.resolvedType == CoreResolvedType.POLICYGROUP) {
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
        }

        applyObservability(v2rayConfig, balancerStrategies)
        applySpeedDisabled(v2rayConfig)
        resolveOutboundDomainsToHosts(v2rayConfig)

        return v2rayConfig
    }

    /**
     * Convert one analyzed outbound entry into concrete outbounds and register
     * them to the runtime configuration.
     */
    private fun buildOutbounds(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: MutableMap<String, String>,
        balancerStrategies: MutableList<BalancerStrategy>,
    ) {
        if (resolvedOutbound.tag in existingTags) {
            LogUtil.w(AppConfig.TAG, "Resolved outbound tag '${resolvedOutbound.tag}' already exists, skipping duplicated entry")
            return
        }

        when (resolvedOutbound.resolvedType) {
            CoreResolvedType.NORMAL -> handleNormalResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
            )

            CoreResolvedType.PROXYCHAIN -> handleProxyChainResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
            )

            CoreResolvedType.POLICYGROUP -> handlePolicyGroupResolvedOutbound(
                resolvedOutbound = resolvedOutbound,
                prepend = prepend,
                existingTags = existingTags,
                v2rayConfig = v2rayConfig,
                policyGroupBalancerTags = policyGroupBalancerTags,
                balancerStrategies = balancerStrategies,
            )
        }
    }

    /**
     * Build and insert a single-node outbound entry.
     */
    private fun handleNormalResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
    ) {
        val profile = resolvedOutbound.resolvedProfiles.firstOrNull() ?: run {
            LogUtil.w(AppConfig.TAG, "NORMAL resolved outbound '${resolvedOutbound.tag}' has empty resolvedProfiles, skipping")
            return
        }
        val outbound = convertProfile2Outbound(profile) ?: run {
            LogUtil.w(AppConfig.TAG, "Could not convert NORMAL resolved outbound '${resolvedOutbound.tag}' profile to outbound, skipping")
            return
        }
        outbound.tag = resolvedOutbound.tag
        if (prepend) {
            v2rayConfig.outbounds.add(0, outbound)
        } else {
            v2rayConfig.outbounds.add(outbound)
        }
        existingTags.add(resolvedOutbound.tag)
    }

    /**
     * Build and insert a multi-hop chain entry.
     *
     * Matixray: an Aether hop dials through no hop by its outbound, which only reaches the core on the
     * loopback address; the core dials out through the hop after it instead, whose outbound, as built
     * here, becomes the core's exit-node by its tag alone, see [routeAetherThroughXray]. A second chain
     * whose Aether hop dials out through the same hops leaves its own copy of that hop under its tag.
     */
    private fun handleProxyChainResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
    ) {
        val chain = resolvedOutbound.resolvedProfiles
            .mapNotNull { profile -> convertProfile2Outbound(profile)?.let { profile to it } }
        val chainOutbounds = chain.map { it.second }.toMutableList()
        if (chainOutbounds.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "PROXYCHAIN resolved outbound '${resolvedOutbound.tag}' has no valid profiles, skipping")
            return
        }
        if (chainOutbounds.size == 1) {
            val outbound = chainOutbounds.first()
            outbound.tag = resolvedOutbound.tag
            if (prepend) {
                v2rayConfig.outbounds.add(0, outbound)
            } else {
                v2rayConfig.outbounds.add(outbound)
            }
            existingTags.add(resolvedOutbound.tag)
            return
        }

        val aetherHop = chain.indexOfFirst { it.first.configType == EConfigType.AETHER }
        val exitNodeTaken = AppConfig.TAG_EXIT_NODE in existingTags
        val chainTags = chainOutbounds.mapIndexed { index, _ ->
            if (index == 0) {
                resolvedOutbound.tag
            } else if (aetherHop >= 0 && index == aetherHop + 1 && !exitNodeTaken) {
                AppConfig.TAG_EXIT_NODE
            } else {
                "${AppConfig.TAG_PROXY}-${resolvedOutbound.tag}-$index"
            }
        }
        if (chainTags.any { it in existingTags }) {
            LogUtil.w(
                AppConfig.TAG,
                "PROXYCHAIN resolved outbound '${resolvedOutbound.tag}' has colliding hop tags, skipping"
            )
            return
        }

        chainOutbounds.forEachIndexed { index, outbound ->
            outbound.tag = chainTags[index]
        }
        for (i in 0 until chainOutbounds.size - 1) {
            if (i == aetherHop) continue
            chainOutbounds[i].ensureSockopt().dialerProxy = chainOutbounds[i + 1].tag
        }

        if (prepend) {
            v2rayConfig.outbounds.addAll(0, chainOutbounds)
        } else {
            v2rayConfig.outbounds.addAll(chainOutbounds)
        }
        chainOutbounds.forEach { existingTags.add(it.tag) }
    }

    /**
     * Build and insert a policy-group entry and its balancer metadata.
     */
    private fun handlePolicyGroupResolvedOutbound(
        resolvedOutbound: CoreConfigContext.ResolvedOutbound,
        prepend: Boolean,
        existingTags: MutableSet<String>,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: MutableMap<String, String>,
        balancerStrategies: MutableList<BalancerStrategy>,
    ) {
        val memberPairs = resolvedOutbound.resolvedProfiles.mapNotNull { profile ->
            convertProfile2Outbound(profile)?.let { ob -> ob to profile }
        }
        if (memberPairs.isEmpty()) {
            LogUtil.w(AppConfig.TAG, "POLICYGROUP resolved outbound '${resolvedOutbound.tag}' has no valid member outbounds, skipping")
            return
        }

        val memberTagPrefix = "${AppConfig.TAG_PROXY}-${resolvedOutbound.tag}-"
        val membersToAdd = mutableListOf<V2rayConfig.OutboundBean>()
        memberPairs.forEachIndexed { index, (outbound, profile) ->
            val memberTag = "$memberTagPrefix${index + 1}-${profile.remarks.trim()}"
            if (memberTag in existingTags) {
                return@forEachIndexed
            }
            outbound.tag = memberTag
            membersToAdd.add(outbound)
            existingTags.add(memberTag)
        }

        if (membersToAdd.isEmpty()) {
            LogUtil.w(
                AppConfig.TAG,
                "POLICYGROUP resolved outbound '${resolvedOutbound.tag}' produced no unique member tags, skipping"
            )
            return
        }

        if (prepend) {
            v2rayConfig.outbounds.addAll(0, membersToAdd)
        } else {
            v2rayConfig.outbounds.addAll(membersToAdd)
        }

        val balancerTag = if (resolvedOutbound.tag == AppConfig.TAG_PROXY) {
            AppConfig.TAG_BALANCER
        } else {
            "${AppConfig.TAG_BALANCER_PRE}-${resolvedOutbound.tag}"
        }
        val strategyType = BalancerStrategyType.from(resolvedOutbound.profile.policyGroupType)
        val fallbackTag = if (strategyType.supportsObservatory && resolvedOutbound.profile.policyGroupTestOutbounds != false) {
            resolvedOutbound.profile.policyGroupFallbackTag
                ?.takeIf { it.isNotEmpty() && it != AppConfig.TAG_PROXY }
            // Xray excludes dead random/roundRobin candidates only when fallbackTag is set;
            // without this default, an enabled empty field creates no observatory.
                ?: membersToAdd.first().tag
        } else null
        val strategy = buildBalancerStrategy(
            strategyType = strategyType,
            selector = listOf(memberTagPrefix),
            balancerTag = balancerTag,
            fallbackTag = fallbackTag,
        )
        val existingBalancers = v2rayConfig.routing.balancers?.toMutableList() ?: mutableListOf()
        if (existingBalancers.none { it.tag == balancerTag }) {
            existingBalancers.add(strategy.balancer)
            v2rayConfig.routing.balancers = existingBalancers
        }
        balancerStrategies.add(strategy)
        policyGroupBalancerTags[resolvedOutbound.tag] = balancerTag
    }

    /**
     * Trim runtime sections that are not needed for latency testing.
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

    /**
     * Serialize a runtime configuration into a standard result object. The Aether core it runs on
     * is written into it as aetherCommand, so that the exported configuration runs again as a custom
     * one.
     * The ECH outbounds of its profiles are linked and appended as it is serialized; one that cannot be
     * used fails it with a message from [context] meant for the screen.
     */
    private fun toConfigResult(
        context: Context,
        configContext: CoreConfigContext,
        v2rayConfig: V2rayConfig,
        core: AetherCore?,
    ): ConfigResult {
        v2rayConfig.aetherCommand = core?.command
        // Matixray: the ECH outbounds of the profiles get their tags and go after every other outbound, as written
        val content = when (val serialized = EchOutbound.serialize(v2rayConfig)) {
            is EchOutbound.Result.Done -> serialized.content
            is EchOutbound.Result.Invalid -> return echOutboundFailure(configContext.guid, serialized, when (serialized.error) {
                EchOutbound.Error.INVALID_JSON ->
                    context.getString(R.string.toast_malformed_json_detail, context.getString(R.string.server_lab_ech_outbound))
                EchOutbound.Error.NEEDS_ECH_CONFIG_LIST -> context.getString(R.string.toast_ech_outbound_needs_ech_config_list)
                EchOutbound.Error.INVALID_TAG -> context.getString(R.string.toast_ech_outbound_invalid_tag)
            })
            is EchOutbound.Result.TagConflict -> return echOutboundFailure(
                configContext.guid,
                serialized,
                context.getString(R.string.toast_ech_outbound_tag_conflict, serialized.tag),
            )
        }
        return ConfigResult(
            status = true,
            guid = configContext.guid,
            content = content,
            aetherCore = core,
        )
    }

    /**
     * Matixray: has what the Aether [core] sends out leave through Xray: the secondary-socks inbound on
     * [port], three above the Aether listen port, after the other inbounds, with no sniffing; the
     * exit-node of the core, after the other outbounds, unless a hop of a chain the core dials out
     * through is the exit-node already, see [handleProxyChainResolvedOutbound]: a freedom outbound
     * with its profile's finalMask and dialMode; and a rule ahead of every other that sends what comes
     * in on that inbound out by that outbound. Returns the core told to dial out through the inbound,
     * or null, with nothing added, when an inbound of the configuration listens on [port] already. A
     * core that names an upstream of its own, as a profile's hand-written command may, is left as it is.
     */
    internal fun routeAetherThroughXray(v2rayConfig: V2rayConfig, core: AetherCore, port: Int): AetherCore? {
        if (core.hasUpstream) return core
        if (v2rayConfig.inbounds.any { it.port == port }) return null
        v2rayConfig.inbounds.add(
            V2rayConfig.InboundBean(
                tag = AppConfig.TAG_SECONDARY_SOCKS,
                port = port,
                protocol = "mixed",
                listen = AppConfig.LOOPBACK,
                settings = V2rayConfig.InboundBean.InSettingsBean(udp = true),
            )
        )
        if (v2rayConfig.outbounds.none { it.tag == AppConfig.TAG_EXIT_NODE }) {
            v2rayConfig.outbounds.add(CoreOutboundBuilder.toOutboundAetherExit(core.exit))
        }
        v2rayConfig.routing.rules.add(
            0,
            V2rayConfig.RoutingBean.RulesBean(
                inboundTag = listOf(AppConfig.TAG_SECONDARY_SOCKS),
                outboundTag = AppConfig.TAG_EXIT_NODE,
            )
        )
        return core.through(port)
    }

    /** Matixray: a configuration with an inbound of its own on the secondary-socks [port], as a failure whose message is meant for the screen. */
    private fun secondaryPortFailure(context: Context, guid: String, port: Int): ConfigResult {
        LogUtil.w(AppConfig.TAG, "An inbound of the configuration listens on the Aether secondary SOCKS port $port, guid=$guid")
        return ConfigResult(
            status = false,
            guid = guid,
            errorMessage = context.getString(R.string.aether_secondary_port_taken, port),
            localizedError = true,
        )
    }

    /**
     * Matixray: true when a configuration on an Aether core, [dependency], has among [outbounds] a routing
     * target or a fallback named exit-node, an outbound tag of its own. That tag is the one the core
     * dials out by: the core would take that profile for its exit-node, or dial into itself.
     */
    internal fun takesExitNodeName(dependency: AetherDependency, outbounds: List<CoreConfigContext.ResolvedOutbound>): Boolean =
        dependency is AetherDependency.Single && outbounds.any { it.tag == AppConfig.TAG_EXIT_NODE }

    /** Matixray: see [takesExitNodeName], as a failure whose message is meant for the screen. */
    private fun exitNodeNameFailure(context: Context, guid: String): ConfigResult {
        LogUtil.w(AppConfig.TAG, "A routing target or a fallback is named ${AppConfig.TAG_EXIT_NODE}, the tag of the Aether exit-node, guid=$guid")
        return ConfigResult(
            status = false,
            guid = guid,
            errorMessage = context.getString(R.string.aether_exit_node_name_taken),
            localizedError = true,
        )
    }

    /**
     * Matixray: true when [core] dials out through a hop of its proxy chain, but [outbounds] have no
     * exit-node: the hop could not be built, and the core would reach WARP without it.
     */
    internal fun lacksChainHop(core: AetherCore, outbounds: List<V2rayConfig.OutboundBean>): Boolean =
        core.exit.hops != null && outbounds.none { it.tag == AppConfig.TAG_EXIT_NODE }

    /** Matixray: see [lacksChainHop], as a failure whose message is meant for the screen. */
    private fun chainHopFailure(context: Context, guid: String): ConfigResult {
        LogUtil.w(AppConfig.TAG, "The chain hop the Aether core dials out through could not be built, guid=$guid")
        return ConfigResult(
            status = false,
            guid = guid,
            errorMessage = context.getString(R.string.aether_chain_hop_missing),
            localizedError = true,
        )
    }

    /**
     * Matixray: a custom configuration with a balancer or an observatory that would pick the exit-node by
     * [selector], see [AetherDependency.routeThroughXray], as a failure whose message is meant for the screen.
     */
    private fun exitNodeSelectorFailure(context: Context, guid: String, selector: String): ConfigResult {
        LogUtil.w(AppConfig.TAG, "A balancer or an observatory of the custom configuration would pick the Aether exit-node, guid=$guid")
        return ConfigResult(
            status = false,
            guid = guid,
            errorMessage = context.getString(R.string.aether_custom_exit_node_selected, selector),
            localizedError = true,
        )
    }

    /** Matixray: a configuration whose ECH outbound cannot be used, as a failure whose message is meant for the screen. */
    private fun echOutboundFailure(guid: String, result: EchOutbound.Result, message: String): ConfigResult {
        LogUtil.w(AppConfig.TAG, "ECH outbound cannot be used: $result, guid=$guid")
        return ConfigResult(status = false, guid = guid, errorMessage = message, localizedError = true)
    }

    /**
     * A configuration the one Aether core cannot serve, as a failure whose message is a resource
     * string meant for the screen; null when the configuration is fine.
     */
    private fun aetherFailure(context: Context, guid: String, dependency: AetherDependency): ConfigResult? {
        val message = when (dependency) {
            AetherDependency.None, is AetherDependency.Single -> return null
            AetherDependency.Conflicting -> context.getString(R.string.aether_config_single_profile)
            AetherDependency.TwoExits -> context.getString(R.string.aether_config_two_exits)
            is AetherDependency.TwoAetherHops -> context.getString(R.string.aether_chain_one_profile)
            is AetherDependency.UnusableCommand -> context.getString(R.string.aether_custom_invalid_command, dependency.written)
            is AetherDependency.NoOutbound -> context.getString(R.string.aether_custom_no_outbound, AppConfig.LOOPBACK, dependency.port)
        }
        LogUtil.w(AppConfig.TAG, "Aether cannot serve this configuration: $dependency, guid=$guid")
        return ConfigResult(status = false, guid = guid, errorMessage = message, localizedError = true)
    }

    /**
     * Load the base template from cache or assets and parse it.
     */
    private fun initV2rayConfig(configContext: CoreConfigContext): V2rayConfig {
        val context = configContext.context
        val assets: String
        if (needTun()) {
            assets = initConfigCacheWithTun ?: Utils.readTextFromAssets(context, "v2ray_config_with_tun.json")
            if (TextUtils.isEmpty(assets)) {
                error("Missing asset: v2ray_config_with_tun.json")
            }
            initConfigCacheWithTun = assets
        } else {
            assets = initConfigCache ?: Utils.readTextFromAssets(context, "v2ray_config.json")
            if (TextUtils.isEmpty(assets)) {
                error("Missing asset: v2ray_config.json")
            }
            initConfigCache = assets
        }
        return JsonUtil.fromJson(assets, V2rayConfig::class.java)
            ?: error("Failed to parse config template")
    }


    //endregion


    //region some sub function

    private fun needTun(): Boolean {
        return SettingsManager.isVpnMode() && !SettingsManager.isUsingHevTun()
    }

    /**
     * Configure inbound listeners and related runtime options.
     */
    private fun configureInbounds(v2rayConfig: V2rayConfig) {
        val vpn = SettingsManager.isVpnMode()
        val useHev = SettingsManager.isUsingHevTun()
        val forcedByHev = vpn && useHev
        val forcedBySocksRoot = SettingsManager.isRootMode()
                || MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)

        val enableLocalProxy = forcedByHev || forcedBySocksRoot || MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)

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
        inbound1.settings?.udp = MmkvManager.decodeSettingsBool(AppConfig.PREF_SOCKS_ENABLE_UDP, AppConfig.DEFAULT_SOCKS_ENABLE_UDP)
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
        val fakedns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED, true)
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
            val inbound2 = JsonUtil.fromJson(JsonUtil.toJson(inbound1), V2rayConfig.InboundBean::class.java)
                ?: error("Failed to clone inbound template")
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
    }

    /**
     * Collect domain rules that target one outbound tag.
     */
    private fun collectUserRuleDomainsByTag(tag: String): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = AdultContentLock.withLock(MmkvManager.decodeRoutingRulesets())
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
     * Collect domain rules that target non-builtin outbound tags.
     */
    private fun collectCustomOutboundDomains(): ArrayList<String> {
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
     * Configure local DNS inbounds, outbounds, and routing rules.
     */
    private fun configureLocalDns(configContext: CoreConfigContext, v2rayConfig: V2rayConfig) {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED, true)) {
            return
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED, true)) {
            val geositeCn = arrayListOf(AppConfig.GEOSITE_CN)
            val routingDomains = configContext.routingDomainRules
                .asSequence()
                .filter { it.outboundTag != AppConfig.TAG_BLOCKED }
                .flatMap { it.domain.asSequence() }
                .toList()
                .distinct()
            val finalDomain = geositeCn + routingDomains
            // fakedns with all domains to make it always top priority
            // Matixray: no "fakedns" block, so Xray-core applies its default fake IP pools
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
                    settings = V2rayConfig.OutboundBean.OutSettingsBean(userLevel = 12),
                    streamSettings = null,
                    mux = null
                )
            )
        }
    }

    /**
     * In the root mode the whole device's traffic (incl. raw DNS) is funneled
     * into the core's SOCKS inbound, exactly like the VPN+hev path. Hijack port-53 to the
     * core's DNS module so queries are resolved via the configured resolver through the
     * proxy instead of leaking to (or being mis-resolved by) the local network resolver.
     * Independent of the local-DNS toggle, which is not exposed for root mode.
     */
    private fun configureRootModeDns(v2rayConfig: V2rayConfig) {
        if (!SettingsManager.isRootMode()) return

        if (v2rayConfig.routing.rules.none { it.outboundTag == "dns-out" && it.port == "53" }) {
            v2rayConfig.routing.rules.add(
                0,
                V2rayConfig.RoutingBean.RulesBean(
                    inboundTag = arrayListOf("socks"),
                    outboundTag = "dns-out",
                    port = "53",
                )
            )
        }
        if (v2rayConfig.outbounds.none { it.protocol == "dns" && it.tag == "dns-out" }) {
            v2rayConfig.outbounds.add(
                V2rayConfig.OutboundBean(
                    protocol = "dns",
                    tag = "dns-out",
                    settings = V2rayConfig.OutboundBean.OutSettingsBean(userLevel = 12),
                    streamSettings = null,
                    mux = null
                )
            )
        }
    }

    /**
     * Remove speed-test runtime sections when the feature is disabled.
     */
    private fun applySpeedDisabled(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy?.system = null
        }
    }

    /*
    /**
     * Configure DNS servers, hosts, and DNS routing rules.
     */
    private fun configureDns(
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>,
    ) {
        val hosts = mutableMapOf<String, Any>()
        val servers = ArrayList<Any>()

        //remote Dns
        val remoteDns = SettingsManager.getRemoteDnsServers()
        val proxyDomain = (collectUserRuleDomainsByTag(AppConfig.TAG_PROXY) + collectCustomOutboundDomains()).distinct()
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
        val directDomain = collectUserRuleDomainsByTag(AppConfig.TAG_DIRECT)
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
        val blkDomain = collectUserRuleDomainsByTag(AppConfig.TAG_BLOCKED)
        if (blkDomain.isNotEmpty()) {
            hosts.putAll(blkDomain.map { it to AppConfig.LOOPBACK })
        }

        // hardcode googleapi rule to fix play store problems
        hosts[AppConfig.GOOGLEAPIS_CN_DOMAIN] = AppConfig.GOOGLEAPIS_COM_DOMAIN

        // hardcode popular Android Private DNS rule to fix localhost DNS problem
        hosts[AppConfig.DNS_ALIDNS_DOMAIN] = AppConfig.DNS_ALIDNS_ADDRESSES
        hosts[AppConfig.DNS_CISCO_SSE_DOMAIN] = AppConfig.DNS_CISCO_SSE_ADDRESSES
        hosts[AppConfig.DNS_CISCO_UMBRELLA_DOMAIN] = AppConfig.DNS_CISCO_UMBRELLA_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONE_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONE_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_COM_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_COM_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_WARP_DOMAIN] = AppConfig.DNS_CLOUDFLARE_WARP_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOH_DOMAIN] = AppConfig.DNS_DNSPOD_DOH_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOT_DOMAIN] = AppConfig.DNS_DNSPOD_DOT_ADDRESSES
        hosts[AppConfig.DNS_GOOGLE_DOMAIN] = AppConfig.DNS_GOOGLE_ADDRESSES
        hosts[AppConfig.DNS_QUAD9_DOMAIN] = AppConfig.DNS_QUAD9_ADDRESSES
        hosts[AppConfig.DNS_SB_DOMAIN] = AppConfig.DNS_SB_ADDRESSES
        hosts[AppConfig.DNS_YANDEX_DOMAIN] = AppConfig.DNS_YANDEX_ADDRESSES

        //User DNS hosts
        val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
        if (userHosts.isNotNullEmpty()) {
            val userHostsMap = userHosts?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.filter { it.contains(":") }
                ?.associate { it.split(":").let { (k, v) -> k to v } }
            if (userHostsMap != null) {
                hosts.putAll(userHostsMap)
            }
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
        val dnsProxyBalancerTag = policyGroupBalancerTags[AppConfig.TAG_PROXY]
        if (dnsProxyBalancerTag != null) {
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    balancerTag = dnsProxyBalancerTag,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        } else {
            v2rayConfig.routing.rules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_PROXY,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        }
    }
    */

    /**
     * Configure DNS servers, hosts, and DNS routing rules.
     */
    private fun configureDns(
        configContext: CoreConfigContext,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>,
    ) {
        val servers = ArrayList<Any>()
        val remoteDns = SettingsManager.getRemoteDnsServers()
        val domesticDns = SettingsManager.getDomesticDnsServers()

        remoteDns.forEach { servers.add(it) }

        val hosts = buildDnsHostsFromRoutingRules(configContext)
        val cnDomesticDnsTags = buildDnsCnModeFromRoutingRules(configContext, servers, domesticDns)
        val domesticDnsTags = buildDnsFromRoutingRules(
            configContext = configContext,
            servers = servers,
            remoteDns = remoteDns,
            domesticDns = domesticDns
        )
        domesticDnsTags.addAll(cnDomesticDnsTags)

        v2rayConfig.dns = V2rayConfig.DnsBean(
            servers = servers,
            hosts = hosts,
            tag = AppConfig.TAG_DNS,
            enableParallelQuery = if ((domesticDns.size + remoteDns.size) > 2) true else null
        )

        // DNS routing, inserted at the top so user rules cannot hijack DNS module queries
        val dnsRouteRules = mutableListOf<V2rayConfig.RoutingBean.RulesBean>()
        if (domesticDnsTags.isNotEmpty()) {
            dnsRouteRules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_DIRECT,
                    inboundTag = ArrayList(domesticDnsTags),
                    domain = null
                )
            )
        }

        val dnsProxyBalancerTag = policyGroupBalancerTags[AppConfig.TAG_PROXY]
        if (dnsProxyBalancerTag != null) {
            dnsRouteRules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    balancerTag = dnsProxyBalancerTag,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        } else {
            dnsRouteRules.add(
                V2rayConfig.RoutingBean.RulesBean(
                    outboundTag = AppConfig.TAG_PROXY,
                    inboundTag = arrayListOf(AppConfig.TAG_DNS),
                    domain = null
                )
            )
        }
        v2rayConfig.routing.rules.addAll(0, dnsRouteRules)
    }

    private fun buildDnsHostsFromRoutingRules(configContext: CoreConfigContext): MutableMap<String, Any> {
        val hosts = mutableMapOf<String, Any>()

        val blockDomains = configContext.routingDomainRules
            .asSequence()
            .filter { it.outboundTag == AppConfig.TAG_BLOCKED }
            .flatMap { it.domain.asSequence() }
            .toList()
        if (blockDomains.isNotEmpty()) {
            hosts.putAll(blockDomains.map { it to AppConfig.LOOPBACK })
        }

        hosts[AppConfig.GOOGLEAPIS_CN_DOMAIN] = AppConfig.GOOGLEAPIS_COM_DOMAIN
        hosts[AppConfig.DNS_ALIDNS_DOMAIN] = AppConfig.DNS_ALIDNS_ADDRESSES
        hosts[AppConfig.DNS_CISCO_SSE_DOMAIN] = AppConfig.DNS_CISCO_SSE_ADDRESSES
        hosts[AppConfig.DNS_CISCO_UMBRELLA_DOMAIN] = AppConfig.DNS_CISCO_UMBRELLA_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONE_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONE_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_ONEDOT_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_COM_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_COM_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_DNS_DOMAIN] = AppConfig.DNS_CLOUDFLARE_DNS_ADDRESSES
        hosts[AppConfig.DNS_CLOUDFLARE_WARP_DOMAIN] = AppConfig.DNS_CLOUDFLARE_WARP_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOH_DOMAIN] = AppConfig.DNS_DNSPOD_DOH_ADDRESSES
        hosts[AppConfig.DNS_DNSPOD_DOT_DOMAIN] = AppConfig.DNS_DNSPOD_DOT_ADDRESSES
        hosts[AppConfig.DNS_GOOGLE_DOMAIN] = AppConfig.DNS_GOOGLE_ADDRESSES
        hosts[AppConfig.DNS_QUAD9_DOMAIN] = AppConfig.DNS_QUAD9_ADDRESSES
        hosts[AppConfig.DNS_SB_DOMAIN] = AppConfig.DNS_SB_ADDRESSES
        hosts[AppConfig.DNS_YANDEX_DOMAIN] = AppConfig.DNS_YANDEX_ADDRESSES

        val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
        if (userHosts.isNotNullEmpty()) {
            val userHostsMap = userHosts?.split(",").orEmpty()
                .filter { it.isNotBlank() && it.contains(":") }
                .associate {
                    // Use limit = 2 to split only at the first colon.
                    // This ensures that IPv6 addresses (which contain multiple colons)
                    // are preserved entirely in the second part.
                    val parts = it.split(":", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
            hosts.putAll(userHostsMap)
        }

        return hosts
    }

    private fun buildDnsCnModeFromRoutingRules(configContext: CoreConfigContext, servers: ArrayList<Any>, domesticDns: List<String>): List<String> {
        val cnRegionFilter = { domain: String ->
            domain.startsWith("geosite:") && (domain.endsWith("-cn") || domain.endsWith("@cn"))
                    || domain == AppConfig.GEOSITE_CN
        }
        val isCnRoutingMode = configContext.routingDomainRules
            .asSequence()
            .filter { it.outboundTag == AppConfig.TAG_DIRECT }
            .flatMap { it.domain.asSequence() }
            .any { it == AppConfig.GEOSITE_CN }

        if (!isCnRoutingMode) {
            return emptyList()
        }

        val geoipCn = arrayListOf(AppConfig.GEOIP_CN)
        val cnDomains = configContext.routingDomainRules
            .asSequence()
            .filter { it.outboundTag == AppConfig.TAG_DIRECT }
            .flatMap { it.domain.asSequence() }
            .filter { cnRegionFilter(it) }
            .toList()
        if (cnDomains.isEmpty()) {
            return emptyList()
        }

        val cnDomesticDnsTags = mutableListOf<String>()
        domesticDns.forEachIndexed { index, address ->
            val cnDomesticDnsTag = "${AppConfig.TAG_DOMESTIC_DNS}_cn_expect_${index}"
            servers.add(
                V2rayConfig.DnsBean.ServersBean(
                    address = address,
                    domains = cnDomains,
                    expectIPs = geoipCn,
                    skipFallback = true,
                    finalQuery = true,
                    tag = cnDomesticDnsTag
                )
            )
            cnDomesticDnsTags.add(cnDomesticDnsTag)
        }
        return cnDomesticDnsTags
    }

    private fun buildDnsFromRoutingRules(
        configContext: CoreConfigContext,
        servers: ArrayList<Any>,
        remoteDns: List<String>,
        domesticDns: List<String>,
    ): MutableList<String> {
        val domesticDnsTags = mutableListOf<String>()
        configContext.routingDomainRules.forEachIndexed { ruleIndex, rule ->
            when (rule.outboundTag) {
                AppConfig.TAG_DIRECT -> {
                    domesticDns.forEachIndexed { dnsIndex, address ->
                        val tag = "${AppConfig.TAG_DOMESTIC_DNS}_${ruleIndex}_$dnsIndex"
                        servers.add(
                            V2rayConfig.DnsBean.ServersBean(
                                address = address,
                                domains = rule.domain,
                                skipFallback = true,
                                finalQuery = true,
                                tag = tag
                            )
                        )
                        domesticDnsTags.add(tag)
                    }
                }

                AppConfig.TAG_BLOCKED -> Unit
                else -> {
                    servers.add(
                        V2rayConfig.DnsBean.ServersBean(
                            address = remoteDns.first(),
                            domains = rule.domain,
                        )
                    )
                }
            }
        }
        return domesticDnsTags
    }

    //endregion


    //region outbound related functions


    /**
     * Resolve outbound domains to IPs and write resolved hosts to DNS map.
     */
    private fun resolveOutboundDomainsToHosts(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, AppConfig.DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD) != "1") {
            return
        }

        val proxyOutboundList = v2rayConfig.getAllProxyOutbound()
        val dns = v2rayConfig.dns ?: return
        val newHosts = dns.hosts?.toMutableMap() ?: mutableMapOf()
        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true

        for (item in proxyOutboundList) {
            val domain = item.getServerAddress()
            if (domain.isNullOrEmpty()) {
                continue
            }

            if (newHosts.containsKey(domain)) {
                item.ensureSockopt().domainStrategy = "UseIP"
                item.ensureSockopt().happyEyeballs = V2rayConfig.OutboundBean.StreamSettingsBean.HappyEyeballsBean(
                    prioritizeIPv6 = preferIpv6,
                    interleave = 2
                )
                continue
            }

            val resolvedIps = HttpUtil.resolveHostToIP(domain, preferIpv6)
            if (resolvedIps.isNullOrEmpty()) {
                continue
            }

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
     * Convert one profile object into one outbound object.
     */
    private fun convertProfile2Outbound(profileItem: ProfileItem): V2rayConfig.OutboundBean? {
        return CoreOutboundBuilder.convert(profileItem)
    }

    //endregion


    //region routing related functions


    /**
     * Merge probe settings from all balancer strategies into the runtime config.
     */
    private fun applyObservability(v2rayConfig: V2rayConfig, strategies: List<BalancerStrategy>) {
        val allObsSelectors = strategies
            .mapNotNull { it.observatory?.subjectSelector }
            .flatten()
            .distinct()
        val obsTemplate = strategies.firstNotNullOfOrNull { it.observatory }
        if (obsTemplate != null && allObsSelectors.isNotEmpty()) {
            v2rayConfig.observatory = V2rayConfig.ObservatoryObject(
                subjectSelector = allObsSelectors,
                probeUrl = obsTemplate.probeUrl,
                probeInterval = obsTemplate.probeInterval,
                enableConcurrency = obsTemplate.enableConcurrency
            )
        }

        val allBurstSelectors = strategies
            .mapNotNull { it.burstObservatory?.subjectSelector }
            .flatten()
            .distinct()
        val burstTemplate = strategies.firstNotNullOfOrNull { it.burstObservatory }
        if (burstTemplate != null && allBurstSelectors.isNotEmpty()) {
            v2rayConfig.burstObservatory = V2rayConfig.BurstObservatoryObject(
                subjectSelector = allBurstSelectors,
                pingConfig = burstTemplate.pingConfig
            )
        }
    }

    /**
     * Configure routing domain strategy and append enabled user rules.
     */
    private fun configureRouting(
        configContext: CoreConfigContext,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>
    ) {

        v2rayConfig.routing.domainStrategy =
            MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
                ?: "AsIs"

        val rulesetItems = AdultContentLock.withLock(MmkvManager.decodeRoutingRulesets())
        rulesetItems?.forEach { key ->
            appendRoutingUserRule(configContext, key, v2rayConfig, policyGroupBalancerTags)
        }
    }

    /**
     * Convert one rule item and append it to routing rules.
     */
    private fun appendRoutingUserRule(
        configContext: CoreConfigContext,
        item: RulesetItem?,
        v2rayConfig: V2rayConfig,
        policyGroupBalancerTags: Map<String, String>
    ) {
        val context = configContext.context
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

        val outboundTag = rule.outboundTag

        // Route rules targeting a custom policy-group tag should hit its balancer.
        policyGroupBalancerTags[outboundTag]?.let { balancerTag ->
            rule.outboundTag = null
            rule.balancerTag = balancerTag
        }

        // If the outbound tag is a custom one that failed to inject, fall back to proxy
        if (!outboundTag.isNullOrBlank()
            && outboundTag !in policyGroupBalancerTags
            && outboundTag !in AppConfig.BUILTIN_OUTBOUND_TAGS
            && v2rayConfig.outbounds.none { it.tag == outboundTag }
        ) {
            LogUtil.w(AppConfig.TAG, "Outbound tag '$outboundTag' not found, falling back to '${AppConfig.TAG_PROXY}'")
            rule.outboundTag = AppConfig.TAG_PROXY
        }

        v2rayConfig.routing.rules.add(rule)
    }


    /**
     * Build balancer and probe settings from one policy-group strategy value.
     */
    private fun buildBalancerStrategy(
        strategyType: BalancerStrategyType,
        selector: List<String>,
        balancerTag: String = AppConfig.TAG_BALANCER,
        fallbackTag: String? = null,
    ): BalancerStrategy {
        val probeUrl = MmkvManager.decodeSettingsString(AppConfig.PREF_DELAY_TEST_URL) ?: AppConfig.DELAY_TEST_URL
        val leastPingInterval = decodeObservatoryDuration(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL)
        val leastLoadInterval = decodeObservatoryDuration(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL)
        val leastLoadMethod = MmkvManager.decodeSettingsString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD)
        val leastLoadSampling = decodeObservatorySampling()
        val leastLoadTimeout = decodeObservatoryDuration(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT)
        val balancer = V2rayConfig.RoutingBean.BalancerBean(
            tag = balancerTag,
            selector = selector,
            fallbackTag = fallbackTag,
            strategy = V2rayConfig.RoutingBean.StrategyObject(type = strategyType.policyGroupType)
        )
        val observatory = if (strategyType.requiresObservatory || fallbackTag != null) {
            V2rayConfig.ObservatoryObject(
                subjectSelector = selector,
                probeUrl = probeUrl,
                probeInterval = leastPingInterval,
                enableConcurrency = true
            )
        } else null
        val burstObservatory = if (strategyType.requiresBurstObservatory) {
            V2rayConfig.BurstObservatoryObject(
                subjectSelector = selector,
                pingConfig = V2rayConfig.BurstObservatoryObject.PingConfigObject(
                    destination = probeUrl,
                    httpMethod = leastLoadMethod,
                    interval = leastLoadInterval,
                    sampling = leastLoadSampling,
                    timeout = leastLoadTimeout
                )
            )
        } else null
        return BalancerStrategy(balancer, observatory, burstObservatory)
    }

    private fun decodeObservatoryDuration(key: String, default: String): String {
        val value = MmkvManager.decodeSettingsString(key)?.trim()
        return if (!value.isNullOrEmpty() && AppConfig.OBSERVATORY_DURATION_PATTERN.matches(value)) {
            value
        } else {
            default
        }
    }

    private fun decodeObservatorySampling(): Int {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING)
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING.toInt()
    }

    /**
     * Carry balancer data plus optional probe settings for later merge.
     */
    private data class BalancerStrategy(
        val balancer: V2rayConfig.RoutingBean.BalancerBean,
        val observatory: V2rayConfig.ObservatoryObject? = null,
        val burstObservatory: V2rayConfig.BurstObservatoryObject? = null,
    )

    //endregion
}
