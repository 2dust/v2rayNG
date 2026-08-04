package com.v2ray.ang.util

import android.content.Context
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import java.io.File
import java.nio.charset.Charset

/**
 * Helpers for raw JSON (CUSTOM) profiles.
 *
 * The typed [com.v2ray.ang.dto.V2rayConfig] model only knows the flat
 * `settings.address` / `settings.port` shape this app generates itself, so any
 * third-party config that uses `vnext`, `servers`, `server` or wireguard `peers`
 * loses its server address when parsed into it. Latency tests need that address,
 * hence this JSON-tree based extraction.
 */
object CustomConfigUtil {

    /** Outbounds that never reach the remote server and must not be tested. */
    private val NON_PROXY_PROTOCOLS = setOf(AppConfig.PROTOCOL_FREEDOM, "blackhole", "dns", "loopback")

    /**
     * Returns the raw config text of a CUSTOM profile.
     *
     * @param context The context.
     * @param guid The server GUID.
     * @param fallback Optional text to use when nothing is stored (e.g. profile.server).
     * @return The raw config text, or null if not found.
     */
    fun getRawConfig(context: Context, guid: String, fallback: String? = null): String? {
        MmkvManager.decodeServerRaw(guid)?.takeIf { it.isNotBlank() }?.let { return it }

        val legacyFile = listOf(
            File(context.filesDir, "$guid.json"),
            File(context.filesDir, "$guid.txt"),
            File(context.filesDir, guid)
        ).firstOrNull { it.exists() }
        if (legacyFile != null) {
            try {
                return legacyFile.readText()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read custom config file", e)
            }
        }

        return fallback?.takeIf { it.isNotBlank() }
    }

    /**
     * Parses a raw config, transparently decoding base64 wrapped payloads.
     *
     * @param raw The raw config text.
     * @return The parsed JsonObject, or null if it is not a config.
     */
    fun parseConfig(raw: String?): JsonObject? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.startsWith("{")) return JsonUtil.parseString(text)

        val decoded = try {
            String(Base64.decode(text, Base64.DEFAULT), Charset.forName("UTF-8")).trim()
        } catch (e: Exception) {
            null
        }
        if (decoded.isNullOrEmpty() || !decoded.startsWith("{")) return null
        return JsonUtil.parseString(decoded)
    }

    /**
     * Finds the outbound that actually connects to the remote server.
     *
     * @param config The parsed config.
     * @return The proxy outbound, or null if there is none.
     */
    fun getProxyOutbound(config: JsonObject?): JsonObject? {
        val outbounds = config?.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val index = getProxyOutboundIndex(outbounds)
        if (index < 0) return null
        return outbounds.get(index).asJsonObject
    }

    /**
     * Finds the index of the outbound that actually connects to the remote server.
     * Outbounds whose server address can be resolved are preferred.
     *
     * @param outbounds The outbounds array.
     * @return The index of the proxy outbound, or -1 if there is none.
     */
    fun getProxyOutboundIndex(outbounds: JsonArray): Int {
        var fallbackIndex = -1
        for (i in 0 until outbounds.size()) {
            val outbound = outbounds.get(i).takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val protocol = outbound.stringOrNull("protocol")?.lowercase().orEmpty()
            if (protocol.isEmpty() || protocol in NON_PROXY_PROTOCOLS) continue
            if (extractHostAndPort(outbound) != null) return i
            if (fallbackIndex < 0) fallbackIndex = i
        }
        return fallbackIndex
    }

    /**
     * Extracts host and port from an outbound, covering every known settings shape.
     *
     * @param outbound The outbound object.
     * @return The host and port pair, or null if it cannot be determined.
     */
    fun extractHostAndPort(outbound: JsonObject): Pair<String, Int>? {
        val settings = outbound.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

        // Xray simplified shape (also what this app generates): { "address": .., "port": .. }
        hostAndPortOf(settings.stringOrNull("address"), settings.portOrNull("port"))?.let { return it }

        // vmess/vless: { "vnext": [..] } - trojan/shadowsocks/socks/http: { "servers": [..] }
        for (key in listOf("vnext", "servers")) {
            val server = settings.firstObjectOrNull(key) ?: continue
            hostAndPortOf(server.stringOrNull("address"), server.portOrNull("port"))?.let { return it }
        }

        // hysteria/tuic style: { "server": "host:port" } or { "server": "host", "server_port": 443 }
        settings.stringOrNull("server")?.let { server ->
            splitHostAndPort(server)?.let { return it }
            val port = settings.portOrNull("server_port") ?: settings.portOrNull("port")
            hostAndPortOf(server, port)?.let { return it }
        }

        // wireguard: { "peers": [ { "endpoint": "host:port" } ] }
        settings.firstObjectOrNull("peers")?.stringOrNull("endpoint")?.let { endpoint ->
            splitHostAndPort(endpoint)?.let { return it }
        }

        return null
    }

    /**
     * Splits an "host:port" endpoint, IPv6 literals included.
     *
     * @param endpoint The endpoint string.
     * @return The host and port pair, or null if the endpoint is malformed.
     */
    private fun splitHostAndPort(endpoint: String): Pair<String, Int>? {
        val value = endpoint.trim()
        if (!value.contains(":")) return null
        // Bare IPv6 without brackets, e.g. "2001:db8::1"
        if (value.count { it == ':' } > 1 && !value.startsWith("[")) return null
        return hostAndPortOf(value.substringBeforeLast(":"), parsePort(value.substringAfterLast(":")))
    }

    private fun hostAndPortOf(host: String?, port: Int?): Pair<String, Int>? {
        val address = host?.trim()?.removeSurrounding("[", "]").orEmpty()
        if (address.isEmpty() || address.contains("{")) return null
        if (port == null || port !in 1..65535) return null
        return Pair(address, port)
    }

    /**
     * Parses a port value, tolerating port hopping ranges such as "443-8443".
     */
    private fun parsePort(value: String?): Int? {
        val digits = value?.trim()?.takeWhile { it.isDigit() }.orEmpty()
        return digits.toIntOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return element.asString?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.portOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return parsePort(element.asString)
    }

    private fun JsonObject.firstObjectOrNull(key: String): JsonObject? {
        val array = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return array.firstOrNull { it.isJsonObject }?.asJsonObject
    }
}
