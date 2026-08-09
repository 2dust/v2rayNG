package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProbePlan
import com.v2ray.ang.dto.ProbeProfile
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.JsonUtil

/** Combines v2rayNG-generated real-delay configurations into one probe core. */
internal object ProbeConfigBuilder {
    data class Source(val guid: String, val config: V2rayConfig)

    fun build(sources: List<Source>, destination: String): ProbePlan {
        val outbounds = mutableListOf<V2rayConfig.OutboundBean>()
        val balancers = mutableListOf<V2rayConfig.RoutingBean.BalancerBean>()
        val profiles = mutableListOf<ProbeProfile>()
        val individualGuids = mutableListOf<String>()

        sources.forEachIndexed { index, source ->
            val prepared = try {
                prepareSource(source, index)
            } catch (_: Exception) {
                null
            }
            if (prepared == null) {
                individualGuids += source.guid
                return@forEachIndexed
            }
            outbounds += prepared.outbounds
            prepared.balancer?.let(balancers::add)
            profiles += prepared.profile
        }

        val routing = mutableMapOf<String, Any>(
            "domainStrategy" to "AsIs",
            "rules" to emptyList<Any>(),
        )
        if (balancers.isNotEmpty()) routing["balancers"] = balancers

        val config = mapOf(
            "log" to mapOf("loglevel" to "warning"),
            "outbounds" to outbounds,
            "routing" to routing,
            "burstObservatory" to mapOf(
                "subjectSelector" to emptyList<String>(),
                "pingConfig" to mapOf(
                    "destination" to destination,
                    "httpMethod" to DEFAULT_HTTP_METHOD,
                    "interval" to "1h",
                    "sampling" to 1,
                    "timeout" to DEFAULT_TIMEOUT,
                ),
            ),
        )
        return ProbePlan(
            content = JsonUtil.toJson(config),
            profiles = profiles,
            individualGuids = individualGuids,
        )
    }

    /** Validate and rewrite one source completely before exposing any part of it to the batch. */
    private fun prepareSource(source: Source, index: Int): PreparedSource? {
        val sourceOutbounds = source.config.outbounds
        val namespace = "probe-$index-"
        val tagMap = sourceOutbounds.associate { it.tag to "$namespace${it.tag}" }
        val primaryBalancer = source.config.routing.balancers
            ?.firstOrNull { it.tag == AppConfig.TAG_BALANCER }
        val strategyType = primaryBalancer?.strategy?.type?.lowercase()
        if (primaryBalancer != null &&
            (strategyType !in SUPPORTED_BALANCER_STRATEGIES || primaryBalancer.fallbackTag != null)
        ) {
            return null
        }

        // Resolve every reference before mutating anything, so a malformed source
        // cannot leave part of itself in the shared configuration.
        val mappedDialerProxies = sourceOutbounds.map { outbound ->
            outbound.streamSettings?.sockopt?.dialerProxy?.let { tagMap[it] ?: return null }
        }

        val profile: ProbeProfile
        val probeBalancer: V2rayConfig.RoutingBean.BalancerBean?
        if (primaryBalancer == null) {
            val proxyTag = tagMap[AppConfig.TAG_PROXY] ?: return null
            profile = ProbeProfile(source.guid, listOf(proxyTag))
            probeBalancer = null
        } else {
            val selectors = primaryBalancer.selector
            if (selectors.isEmpty() || selectors.any(String::isBlank)) return null
            val outboundTags = tagMap
                .filterKeys { tag -> selectors.any(tag::startsWith) }
                .values
                .toList()
            if (outboundTags.isEmpty()) return null
            probeBalancer = primaryBalancer.copy(
                tag = "$namespace${primaryBalancer.tag}",
                selector = selectors.map { "$namespace$it" },
            )
            profile = ProbeProfile(
                guid = source.guid,
                outboundTags = outboundTags,
                balancerTag = probeBalancer.tag,
            )
        }

        sourceOutbounds.forEachIndexed { outboundIndex, outbound ->
            outbound.tag = tagMap.getValue(outbound.tag)
            mappedDialerProxies[outboundIndex]?.let { mapped ->
                outbound.streamSettings?.sockopt?.dialerProxy = mapped
            }
        }
        return PreparedSource(sourceOutbounds, probeBalancer, profile)
    }

    private data class PreparedSource(
        val outbounds: List<V2rayConfig.OutboundBean>,
        val balancer: V2rayConfig.RoutingBean.BalancerBean?,
        val profile: ProbeProfile,
    )

    private val SUPPORTED_BALANCER_STRATEGIES = setOf(
        "leastping",
        "leastload",
    )
    private const val DEFAULT_HTTP_METHOD = "HEAD"
    private const val DEFAULT_TIMEOUT = "5s"
}
