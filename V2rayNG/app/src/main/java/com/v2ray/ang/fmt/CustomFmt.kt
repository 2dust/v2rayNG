package com.v2ray.ang.fmt

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

object CustomFmt : FmtBase() {
    /**
     * Parses a JSON string into a ProfileItem object.
     *
     * Accepts a complete core configuration or a bare outbound object, which
     * [normalize] embeds into a complete configuration first.
     *
     * @param str the JSON string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String, configType: EConfigType = EConfigType.CUSTOM): ProfileItem {
        // A non-object input is passed through unchanged so Gson still reports it as malformed.
        val normalized = normalize(str) ?: str
        val fullConfig = JsonUtil.fromJson(normalized, V2rayConfig::class.java)
        val outbound = fullConfig?.getProxyOutbound()

        val config = ProfileItem.create(configType)

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        // VLESS/VMess/Trojan outbounds carry their endpoint inside
        // settings.vnext / settings.servers, which the typed DTO does not map.
        // Read it from JSON so profiles that go through the normal outbound
        // path (unified config, ping, launcher validation) still see a server.
        if (config.server.isNullOrBlank()) {
            val endpoint = firstEndpointAddressAndPort(normalized)
            if (endpoint != null) {
                config.server = endpoint.first
                if (config.serverPort.isNullOrBlank()) {
                    config.serverPort = endpoint.second
                }
            }
        }

        return config
    }

    /**
     * Reads address and port of the first endpoint from a full config's
     * primary outbound. Supports VLESS/VMess ("vnext") and Trojan
     * ("servers") styles where the endpoint is not a plain settings field.
     */
    private fun firstEndpointAddressAndPort(configJson: String): Pair<String, String>? {
        val root = JsonUtil.parseString(configJson)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val outbound = root.getAsJsonArray("outbounds")?.firstOrNull()?.asJsonObject ?: return null
        val settings = outbound.getAsJsonObject("settings") ?: return null
        val endpoints = settings.getAsJsonArray("vnext") ?: settings.getAsJsonArray("servers") ?: return null
        val endpoint = endpoints.firstOrNull()?.asJsonObject ?: return null
        val address = endpoint.get("address")?.asString ?: return null
        val port = endpoint.get("port")?.asInt?.toString() ?: return null
        return address to port
    }

    /**
     * Converts accepted custom input into a complete core configuration.
     * @param str the JSON string to normalize
     * @return the normalized configuration, or null if it is not a JSON object
     */
    fun normalize(str: String): String? {
        val json = JsonUtil.parseString(str) ?: return null
        return if (isOutboundObject(json)) JsonUtil.toJson(wrapOutbound(json)) else JsonUtil.toJson(simplifyFallback(json))
    }

    fun isOutboundJson(str: String): Boolean =
        JsonUtil.parseString(str)?.let { isOutboundObject(it) } == true

    /**
     * Whether the JSON object is a bare outbound object instead of a complete
     * core configuration.
     */
    fun isOutboundObject(json: JsonObject): Boolean {
        if (json.has("inbounds") || json.has("outbounds") || json.has("routing")) {
            return false
        }
        return !stringOrNull(json, "protocol").isNullOrBlank()
    }

    fun toUri(profile: ProfileItem, raw: String): String {
        if (raw.isBlank()) return ""
        val root = JsonUtil.parseString(raw)?.takeIf { it.isJsonObject }?.asJsonObject
        val outbound = root?.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
            ?: raw
        // Field values mirror v2rayN's share-item schema: ConfigType 13 is
        // EConfigType.Outbound and CoreType 2 is the Xray core.
        val shareItem = JsonObject().apply {
            addProperty("IndexId", Utils.getUuid())
            addProperty("ConfigType", 13)
            addProperty("CoreType", 2)
            addProperty("ConfigVersion", 4)
            addProperty("DisplayLog", true)
            addProperty("Remarks", profile.remarks)
            addProperty("Port", 0)
            addProperty("AlterId", 0)
            add("CustomOutboundObj", JsonUtil.parseString(outbound.toString()))
        }
        return AppConfig.V2RAYNFMTS + "outbound/" + Utils.encode(JsonUtil.toJson(shareItem))
    }

    /**
     * Reduces multi-endpoint settings (vnext, servers) of one outbound object
     * to the first entry. Public so the runtime outbound reader can apply the
     * same constraint to configs stored before normalization did it.
     */
    fun simplifyOutboundEndpoints(outbound: JsonObject) {
        val protocol = stringOrNull(outbound, "protocol") ?: return
        val settings = outbound.getAsJsonObject("settings") ?: return

        if (protocol.equals("vless", ignoreCase = true) || protocol.equals("vmess", ignoreCase = true)) {
            val vnext = settings.getAsJsonArray("vnext")
            if (vnext != null && vnext.size() > 1) {
                settings.add("vnext", JsonArray().apply { add(vnext[0]) })
            }
        }
        if (protocol.equals("trojan", ignoreCase = true)) {
            val servers = settings.getAsJsonArray("servers")
            if (servers != null && servers.size() > 1) {
                settings.add("servers", JsonArray().apply { add(servers[0]) })
            }
        }
    }

    /**
     * Simplifies a core configuration object, handling both a bare outbound
     * and a full config that carries an outbounds array.
     */
    fun simplifyFallback(json: JsonObject): JsonObject {
        json.getAsJsonArray("outbounds")?.takeIf { it.size() > 0 }?.let { outbounds ->
            simplifyOutboundEndpoints(outbounds[0].asJsonObject)
        } ?: simplifyOutboundEndpoints(json)
        return json
    }

    /**
     * Embeds a bare outbound object into a minimal complete configuration.
     */
    private fun wrapOutbound(outbound: JsonObject): JsonObject {
        val simplified = simplifyFallback(outbound)
        val wrapped = simplified.deepCopy()
        val tag = stringOrNull(wrapped, "tag")?.takeIf { it.isNotBlank() } ?: AppConfig.TAG_PROXY
        wrapped.addProperty("tag", tag)

        val outbounds = JsonArray()
        outbounds.add(wrapped)

        val routing = JsonObject().apply {
            addProperty("domainStrategy", "AsIs")
            add("rules", JsonArray())
        }

        return JsonObject().apply {
            addProperty("remarks", tag)
            add("inbounds", socksInbounds())
            add("outbounds", outbounds)
            add("routing", routing)
        }
    }

    private fun socksInbounds(): JsonArray = JsonArray().apply {
        add(
            JsonObject().apply {
                addProperty("port", AppConfig.PORT_SOCKS.toInt())
                addProperty("protocol", "socks")
                addProperty("listen", AppConfig.LOOPBACK)
            }
        )
    }

    private fun stringOrNull(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
