package com.v2ray.ang.fmt

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import java.net.IDN
import java.net.URI

object CustomFmt : FmtBase() {
    data class Metadata(
        val remarks: String?,
        val serverCount: Int,
        val server: String?,
        val serverPort: String?,
    )

    /**
     * Parses a JSON string into a ProfileItem object.
     *
     * @param str the JSON string to parse
     * @return the parsed ProfileItem object
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val metadata = parseMetadata(str)

        config.remarks = metadata.remarks ?: System.currentTimeMillis().toString()
        config.server = metadata.server
        config.serverPort = metadata.serverPort

        return config
    }

    /** Reads display metadata without rewriting or narrowing the user's raw Xray JSON. */
    fun parseMetadata(str: String): Metadata {
        val element = JsonUtil.fromJson(str, JsonElement::class.java)
        val json = when {
            element == null || element.isJsonNull -> null
            element.isJsonObject -> element.asJsonObject
            else -> throw JsonSyntaxException("Custom configuration must be a JSON object")
        }
        val servers = json?.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.flatMap { element ->
                val outbound = element.takeIf { it.isJsonObject }?.asJsonObject
                val protocol = outbound?.get("protocol").stringValue()
                readServers(outbound?.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject, protocol)
            }.orEmpty()
        val single = servers.singleOrNull()
        val address = single?.first?.takeIf(::isValidAddress)
        return Metadata(
            remarks = json?.get("remarks").stringValue(),
            serverCount = servers.size,
            server = address,
            serverPort = single?.second?.takeIf { address != null && it.toIntOrNull() in 1..65535 },
        )
    }

    private fun readServers(settings: JsonObject?, protocol: String?): List<Pair<String?, String?>> {
        val key = when (protocol?.lowercase()) {
            "vmess", "vless" -> "vnext"
            "trojan", "shadowsocks", "socks", "http", "hysteria", "hysteria2" -> "servers"
            "wireguard" -> "peers"
            else -> return emptyList()
        }
        if (key == "peers") {
            return settings?.get("peers")?.takeIf { it.isJsonArray }?.asJsonArray?.map { peer ->
                val endpoint = peer.takeIf { it.isJsonObject }?.asJsonObject?.get("endpoint").stringValue()
                val address = endpoint?.substringBeforeLast(':', "")?.removeSurrounding("[", "]")
                address to endpoint?.substringAfterLast(':', "")
            }.orEmpty()
        }
        // Xray gives flat settings precedence over legacy vnext/servers arrays.
        if (settings?.get("address")?.isJsonNull == false) {
            return listOf(readServer(settings))
        }
        return settings?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.map {
            readServer(it.takeIf { element -> element.isJsonObject }?.asJsonObject)
        }?.takeIf { it.isNotEmpty() } ?: listOf(null to null)
    }

    private fun readServer(server: JsonObject?): Pair<String?, String?> =
        server?.get("address").stringValue() to server?.get("port")
            ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString

    private fun JsonElement?.stringValue(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun isValidAddress(address: String): Boolean {
        if (address.isBlank() || address.any { it.isWhitespace() }) return false
        return try {
            if (':' in address) {
                val literal = address.removeSurrounding("[", "]")
                URI("http://[$literal]").host != null
            } else {
                val ascii = IDN.toASCII(address, IDN.USE_STD3_ASCII_RULES)
                val parts = ascii.split('.')
                val validIpv4 = parts.size != 4 || parts.any { part -> part.any { !it.isDigit() } }
                    || parts.all { it.toIntOrNull() in 0..255 }
                ascii.isNotEmpty() && ascii.length <= 253 && ascii != "." && validIpv4
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.net.URISyntaxException) {
            false
        }
    }
}
