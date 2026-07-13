package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.dto.OutboundProbeCandidate
import com.v2ray.ang.dto.OutboundProbePlan
import com.v2ray.ang.dto.OutboundProbeProfilePlan
import com.v2ray.ang.util.JsonUtil

/**
 * Combines independently generated speed-test configurations into one core.
 *
 * Every source gets a private tag namespace. Only outbound definitions and the
 * primary policy-group balancer are retained: the one-shot observatory probes
 * tagged outbound handlers directly, so unrelated routing and inbound state
 * must not be allowed to affect another profile in the same batch.
 */
object OutboundProbeConfigBuilder {
    data class Source(val guid: String, val content: String)

    fun build(
        sources: List<Source>,
        destination: String,
        timeout: String = DEFAULT_PROBE_TIMEOUT,
        samples: Int = 1,
    ): OutboundProbePlan {
        require(destination.isNotBlank()) { "probe destination is empty" }
        require(samples > 0) { "probe sample count must be positive" }

        val mergedOutbounds = JsonArray()
        val mergedBalancers = JsonArray()
        val profiles = mutableListOf<OutboundProbeProfilePlan>()
        val failedGuids = mutableListOf<String>()
        var batchSamples = samples
        var batchTimeout = timeout
        var leastLoadHttpMethod: String? = null

        sources.distinctBy { it.guid }.forEachIndexed { index, source ->
            val root = JsonUtil.parseString(source.content)
            val outbounds = root?.array("outbounds")
            if (outbounds == null || outbounds.size() == 0) {
                failedGuids += source.guid
                return@forEachIndexed
            }

            val namespace = "probe-$index-"
            val outboundElements = outbounds.toList()
            if (outboundElements.any { !it.isJsonObject }) {
                failedGuids += source.guid
                return@forEachIndexed
            }
            val outboundObjects = outboundElements.map { it.asJsonObject.deepCopy() }
            val originalTags = outboundObjects.mapIndexed { outboundIndex, outbound ->
                outbound.string("tag") ?: "outbound-$outboundIndex"
            }
            if (originalTags.toSet().size != originalTags.size) {
                failedGuids += source.guid
                return@forEachIndexed
            }

            val tagMap = linkedMapOf<String, String>()
            outboundObjects.forEachIndexed { outboundIndex, outbound ->
                val originalTag = originalTags[outboundIndex]
                val probeTag = "$namespace$originalTag"
                tagMap[originalTag] = probeTag
                outbound.addProperty("tag", probeTag)
            }
            if (outboundObjects.any { !remapOutboundReferences(it, tagMap) }) {
                failedGuids += source.guid
                return@forEachIndexed
            }

            val routing = root.obj("routing")
            val routingRules = routing?.array("rules")
                ?.mapNotNull { it.takeIf { rule -> rule.isJsonObject }?.asJsonObject }
                .orEmpty()
            val primaryBalancerTag = routingRules
                .lastOrNull { it.isCatchAllRule() && it.string("balancerTag") != null }
                ?.string("balancerTag")

            val originalBalancer = primaryBalancerTag?.let { wanted ->
                routing?.array("balancers")
                    ?.mapNotNull { it.takeIf { balancer -> balancer.isJsonObject }?.asJsonObject }
                    ?.firstOrNull { it.string("tag") == wanted }
            }
            if ((primaryBalancerTag != null && originalBalancer == null) ||
                (primaryBalancerTag == null && routingRules.any { it.string("balancerTag") != null })
            ) {
                // Direct batch probing cannot reproduce conditional routing.
                // Accept only the catch-all policy-group form emitted by v2rayNG.
                failedGuids += source.guid
                return@forEachIndexed
            }

            val localBalancers = JsonArray()
            val profile = if (originalBalancer != null) {
                buildPolicyProfile(
                    source.guid,
                    namespace,
                    originalBalancer,
                    tagMap,
                    localBalancers,
                )
            } else {
                val catchAllOutbound = routingRules.lastOrNull {
                    it.isCatchAllRule() && it.string("outboundTag") != null
                }?.string("outboundTag")
                if (catchAllOutbound != null && catchAllOutbound !in tagMap) {
                    failedGuids += source.guid
                    return@forEachIndexed
                }
                if (catchAllOutbound == null && routingRules.isNotEmpty()) {
                    failedGuids += source.guid
                    return@forEachIndexed
                }
                val routedTag = catchAllOutbound
                val runtimeTag = routedTag ?: tagMap.keys.first()
                OutboundProbeProfilePlan(
                    guid = source.guid,
                    candidates = listOf(
                        OutboundProbeCandidate(
                            probeTag = tagMap.getValue(runtimeTag),
                            runtimeTag = runtimeTag,
                        )
                    ),
                )
            }

            if (profile == null || profile.candidates.isEmpty()) {
                failedGuids += source.guid
            } else {
                if (originalBalancer?.obj("strategy")?.string("type")
                        ?.equals("leastLoad", ignoreCase = true) == true
                ) {
                    root.obj("burstObservatory")?.obj("pingConfig")?.let { pingConfig ->
                        batchSamples = maxOf(batchSamples, pingConfig.positiveInt("sampling") ?: 1)
                        batchTimeout = longerDuration(
                            batchTimeout,
                            pingConfig.string("timeout") ?: DEFAULT_PROBE_TIMEOUT,
                        )
                        val method = pingConfig.string("httpMethod")
                            ?.uppercase()
                            ?.takeIf { it == "GET" || it == "HEAD" }
                            ?: "HEAD"
                        leastLoadHttpMethod = when {
                            leastLoadHttpMethod == null -> method
                            leastLoadHttpMethod == "GET" || method == "GET" -> "GET"
                            else -> "HEAD"
                        }
                    }
                }
                outboundObjects.forEach { mergedOutbounds.add(it) }
                localBalancers.forEach { mergedBalancers.add(it) }
                profiles += profile
            }
        }

        val root = JsonObject().apply {
            add("log", JsonObject().apply { addProperty("loglevel", "warning") })
            add("outbounds", mergedOutbounds)
            add("routing", JsonObject().apply {
                addProperty("domainStrategy", "AsIs")
                add("rules", JsonArray())
                if (mergedBalancers.size() > 0) add("balancers", mergedBalancers)
            })
            add("burstObservatory", JsonObject().apply {
                add("subjectSelector", JsonArray())
                add("pingConfig", JsonObject().apply {
                    addProperty("destination", destination)
                    addProperty("httpMethod", leastLoadHttpMethod ?: DEFAULT_HTTP_METHOD)
                    addProperty("interval", "1h")
                    addProperty("sampling", batchSamples)
                    addProperty("timeout", batchTimeout)
                })
            })
        }

        return OutboundProbePlan(
            content = JsonUtil.toJsonPretty(root).orEmpty(),
            profiles = profiles,
            failedGuids = failedGuids,
            samples = batchSamples,
        )
    }

    private fun buildPolicyProfile(
        guid: String,
        namespace: String,
        sourceBalancer: JsonObject,
        tagMap: Map<String, String>,
        mergedBalancers: JsonArray,
    ): OutboundProbeProfilePlan? {
        val strategy = sourceBalancer.obj("strategy") ?: return null
        val strategyType = strategy.string("type")
        if (strategyType?.equals("leastPing", ignoreCase = true) != true &&
            strategyType?.equals("leastLoad", ignoreCase = true) != true
        ) return null
        if ((strategy.obj("settings")?.array("costs")?.size() ?: 0) > 0) {
            // Cost matchers refer to original outbound tags. Silently carrying
            // them into a namespaced batch would change leastLoad semantics.
            return null
        }

        val selectors = sourceBalancer.array("selector")
            ?.mapNotNull { it.takeIf { selector -> selector.isJsonPrimitive }?.asString }
            .orEmpty()
        val candidates = tagMap.entries
            .filter { (runtimeTag, _) -> selectors.any(runtimeTag::startsWith) }
            .map { (runtimeTag, probeTag) -> OutboundProbeCandidate(probeTag, runtimeTag) }
        if (candidates.isEmpty()) return null

        val balancer = sourceBalancer.deepCopy()
        val probeBalancerTag = "$namespace${sourceBalancer.string("tag").orEmpty()}"
        balancer.addProperty("tag", probeBalancerTag)
        balancer.add("selector", JsonArray().apply {
            selectors.forEach { add("$namespace$it") }
        })
        sourceBalancer.string("fallbackTag")?.let { fallback ->
            val mappedFallback = tagMap[fallback] ?: return null
            balancer.addProperty("fallbackTag", mappedFallback)
        }
        mergedBalancers.add(balancer)
        return OutboundProbeProfilePlan(guid, candidates, probeBalancerTag)
    }

    private fun remapOutboundReferences(outbound: JsonObject, tagMap: Map<String, String>): Boolean {
        outbound.obj("streamSettings")
            ?.obj("sockopt")
            ?.let { sockopt ->
                sockopt.string("dialerProxy")?.let { old ->
                    if (old.isNotBlank()) {
                        val mapped = tagMap[old] ?: return false
                        sockopt.addProperty("dialerProxy", mapped)
                    }
                }
            }
        outbound.obj("proxySettings")?.let { proxySettings ->
            proxySettings.string("tag")?.let { old ->
                if (old.isNotBlank()) {
                    val mapped = tagMap[old] ?: return false
                    proxySettings.addProperty("tag", mapped)
                }
            }
        }
        return true
    }

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.positiveInt(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }
            ?.runCatching { asInt }
            ?.getOrNull()
            ?.takeIf { it > 0 }

    private fun longerDuration(first: String, second: String): String {
        val firstMillis = durationMillis(first) ?: return second
        val secondMillis = durationMillis(second) ?: return first
        return if (secondMillis > firstMillis) second else first
    }

    private fun durationMillis(value: String): Long? {
        val match = DURATION_PATTERN.matchEntire(value.trim()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "ms" -> 1L
            "s" -> 1_000L
            "m" -> 60_000L
            "h" -> 3_600_000L
            else -> return null
        }
        return if (amount <= Long.MAX_VALUE / multiplier) amount * multiplier else Long.MAX_VALUE
    }

    private fun JsonObject.isCatchAllRule(): Boolean {
        val constrainedFields = listOf(
            "domain", "ip", "port", "sourcePort", "source", "user", "inboundTag",
            "protocol", "attrs", "process",
        )
        if (constrainedFields.any(::has)) return false
        val network = string("network")
        return network == null || network == "tcp,udp" || network == "tcp, udp"
    }

    private val DURATION_PATTERN = Regex("""([1-9]\d*)(ms|s|m|h)""")
    private const val DEFAULT_HTTP_METHOD = "GET"
    private const val DEFAULT_PROBE_TIMEOUT = "5s"
}
