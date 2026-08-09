package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProbePlan
import com.v2ray.ang.dto.ProbeProfile
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.JsonUtil

/** Combines v2rayNG-generated real-delay configurations into one probe core. */
object ProbeConfigBuilder {
    data class Source(val guid: String, val config: V2rayConfig)

    fun build(sources: List<Source>, destination: String): ProbePlan {
        val outbounds = mutableListOf<V2rayConfig.OutboundBean>()
        val balancers = mutableListOf<V2rayConfig.RoutingBean.BalancerBean>()
        val profiles = mutableListOf<ProbeProfile>()
        val individualGuids = mutableListOf<String>()

        sources.forEachIndexed { index, source ->
            val namespace = "probe-$index-"
            val tagMap = source.config.outbounds.associate { it.tag to "$namespace${it.tag}" }
            val primaryBalancer = source.config.routing.balancers
                ?.firstOrNull { it.tag == AppConfig.TAG_BALANCER }
            val strategyType = primaryBalancer?.strategy?.type

            if (primaryBalancer != null && strategyType !in OBSERVATORY_STRATEGIES) {
                individualGuids += source.guid
                return@forEachIndexed
            }

            source.config.outbounds.forEach { outbound ->
                outbound.tag = tagMap.getValue(outbound.tag)
                outbound.streamSettings?.sockopt?.dialerProxy?.let { dialerProxy ->
                    outbound.streamSettings?.sockopt?.dialerProxy = tagMap.getValue(dialerProxy)
                }
                outbounds += outbound
            }

            if (primaryBalancer == null) {
                profiles += ProbeProfile(source.guid, listOf(tagMap.getValue(AppConfig.TAG_PROXY)))
                return@forEachIndexed
            }

            val outboundTags = tagMap
                .filterKeys { tag -> primaryBalancer.selector.any(tag::startsWith) }
                .values
                .toList()
            val probeBalancer = primaryBalancer.copy(
                tag = "$namespace${primaryBalancer.tag}",
                selector = primaryBalancer.selector.map { "$namespace$it" },
                fallbackTag = primaryBalancer.fallbackTag?.let(tagMap::getValue),
            )
            balancers += probeBalancer
            profiles += ProbeProfile(source.guid, outboundTags, probeBalancer.tag)

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
            content = JsonUtil.toJsonPretty(config).orEmpty(),
            profiles = profiles,
            individualGuids = individualGuids,
        )
    }

    private val OBSERVATORY_STRATEGIES = setOf("leastPing", "leastLoad")
    private const val DEFAULT_HTTP_METHOD = "HEAD"
    private const val DEFAULT_TIMEOUT = "5s"
}
