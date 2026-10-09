package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

/**
 * Matixray: the ECH outbound of a profile is a whole Xray outbound, written as raw JSON, that the ECH
 * config query is sent through (tlsSettings.echSockopt.dialerProxy). It is appended to the generated
 * configuration after every other outbound, exactly as written.
 */
object EchOutbound {

    enum class Error { INVALID_JSON, NEEDS_ECH_CONFIG_LIST, INVALID_TAG }

    sealed interface Result {
        data class Done(val content: String) : Result
        data class Invalid(val error: Error) : Result
        data class TagConflict(val tag: String) : Result
    }

    /**
     * Whether the ECH outbound of [profile] is used, which is where the editor shows it: under TLS on
     * VMess, VLESS, Shadowsocks and Trojan, and on Hysteria2, whose blank security is saved as TLS.
     * Anywhere else it is kept, but neither checked nor used.
     */
    fun appliesTo(profile: ProfileItem): Boolean = when (profile.configType) {
        EConfigType.VMESS, EConfigType.VLESS, EConfigType.SHADOWSOCKS, EConfigType.TROJAN ->
            profile.security == AppConfig.TLS
        EConfigType.HYSTERIA2 -> profile.security.isNullOrBlank() || profile.security == AppConfig.TLS
        else -> false
    }

    /**
     * Checks the ECH outbound of [profile] where it applies: a JSON object, next to an echConfigList,
     * with a tag that is not empty, not direct or block, and does not start with proxy (balancers pick
     * their members by that prefix, so such an outbound would carry the proxied traffic).
     *
     * @return null when the ECH outbound is empty, does not apply, or is valid.
     */
    fun validate(profile: ProfileItem): Error? =
        if (appliesTo(profile)) validate(profile.echOutbound, profile.echConfigList) else null

    fun tagOf(outbound: JsonObject): String? {
        val tag = outbound.get("tag")
        return if (tag != null && tag.isJsonPrimitive && tag.asJsonPrimitive.isString) tag.asString else null
    }

    /**
     * Serializes [config] with the ECH outbounds that CoreOutboundBuilder attached to its TLS outbounds:
     * the echSockopt of each of those outbounds points at the tag that its ECH outbound has in the
     * configuration, and each ECH outbound is appended once, after every other outbound.
     *
     * Outbounds with the same ECH outbound share it. A different ECH outbound under a tag that an
     * earlier one has gets a numbered tag ("ech-2") that no other outbound has: the tag only links a
     * proxy outbound to its ECH outbound, and a group, a chain or the routing outbounds put unrelated
     * profiles in one configuration. A tag as written that another outbound already has is a conflict
     * rather than a redirect of the ECH config query, and an ECH outbound the editor never checked, as
     * an imported one may be, fails the configuration rather than letting the query go direct.
     */
    fun serialize(config: V2rayConfig): Result {
        val usedTags = config.outbounds.map { it.tag }.toSet()
        val linked = mutableListOf<Pair<JsonObject, String>>()
        for (outbound in config.outbounds) {
            val tlsSettings = outbound.streamSettings?.tlsSettings ?: continue
            val text = tlsSettings.echOutbound ?: continue
            validate(text, tlsSettings.echConfigList)?.let { return Result.Invalid(it) }
            val echOutbound = parse(text) ?: return Result.Invalid(Error.INVALID_JSON)
            val tag = linked.firstOrNull { it.first == echOutbound }?.second
                ?: freeTag(tagOf(echOutbound).orEmpty(), usedTags, linked.map { it.second }).also { linked.add(echOutbound to it) }
            tlsSettings.echSockopt = OutboundBean.StreamSettingsBean.SockoptBean(dialerProxy = tag)
        }
        linked.firstOrNull { it.second in usedTags }?.let { return Result.TagConflict(it.second) }

        val content = JsonUtil.toJsonPretty(config).orEmpty()
        if (linked.isEmpty()) return Result.Done(content)
        val json = parse(content) ?: error("The generated configuration is not a JSON object")
        val outbounds = json.get("outbounds") as? JsonArray ?: JsonArray().also { json.add("outbounds", it) }
        for ((echOutbound, tag) in linked) {
            echOutbound.addProperty("tag", tag)
            outbounds.add(echOutbound)
        }
        return Result.Done(JsonUtil.toJsonPretty(json).orEmpty())
    }

    private fun validate(echOutbound: String?, echConfigList: String?): Error? {
        if (echOutbound.isNullOrBlank()) return null
        val outbound = parse(echOutbound) ?: return Error.INVALID_JSON
        if (echConfigList.isNullOrBlank()) return Error.NEEDS_ECH_CONFIG_LIST
        val tag = tagOf(outbound)
        if (tag.isNullOrBlank() || tag == AppConfig.TAG_DIRECT || tag == AppConfig.TAG_BLOCKED ||
            tag.startsWith(AppConfig.TAG_PROXY)
        ) {
            return Error.INVALID_TAG
        }
        return null
    }

    /** [tag] as written, or, when an earlier ECH outbound has it, its first number that no outbound has. */
    private fun freeTag(tag: String, usedTags: Set<String>, linkedTags: List<String>): String {
        if (tag !in linkedTags) return tag
        var number = 2
        while ("$tag-$number" in linkedTags || "$tag-$number" in usedTags) number++
        return "$tag-$number"
    }

    /** Parses like JsonUtil.parseString, without logging: the text is the user's, not a failure to report. */
    private fun parse(json: String?): JsonObject? =
        json?.let { runCatching { JsonParser.parseString(it) as? JsonObject }.getOrNull() }
}
