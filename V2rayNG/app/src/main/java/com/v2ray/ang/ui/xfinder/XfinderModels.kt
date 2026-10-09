package com.v2ray.ang.ui.xfinder

import com.google.gson.JsonParser
import java.net.URLDecoder
import java.util.Base64

/** Ping quality bucket used to colour a config or IP row. */
enum class PingLevel { NONE, GOOD, MEDIUM, BAD }

/** Colour bucket for a latency: under 500 ms good, up to 1000 ms medium, above that bad. */
fun pingLevelOf(ms: Int?): PingLevel = when {
    ms == null -> PingLevel.NONE
    ms < 500 -> PingLevel.GOOD
    ms <= 1000 -> PingLevel.MEDIUM
    else -> PingLevel.BAD
}

/** One line of the Xfinder list. [raw] is the share link and also the stable list key. */
data class XfinderConfig(
    val raw: String,
    val protocol: String,
    val name: String,
    val server: String,
    val port: String,
    val pingMs: Int?,
)

/** Pure parser for the Xfinder `top20.txt` / `all.txt` bodies. */
object XfinderParser {
    private val pingRegex = Regex("""(\d+)\s*ms""", RegexOption.IGNORE_CASE)

    fun parse(text: String?): List<XfinderConfig> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.contains("://") }
            .distinct()
            .mapNotNull(::parseLine)
            .toList()
    }

    fun parseLine(line: String): XfinderConfig? {
        val raw = line.trim()
        val scheme = raw.substringBefore("://", "").lowercase()
        if (scheme.isEmpty()) return null
        val body = raw.substringAfter("://")
        val fragmentName = decodeFragment(body.substringAfterLast('#', ""))

        val name: String
        val server: String
        val port: String
        if (scheme == "vmess") {
            val json = decodeVmess(body.substringBefore('#'))
            name = json?.get("ps").orEmpty().ifBlank { fragmentName }
            server = json?.get("add").orEmpty()
            port = json?.get("port").orEmpty()
        } else {
            name = fragmentName
            val hostPort = extractHostPort(body)
            server = hostPort.first
            port = hostPort.second
        }
        val ping = pingRegex.find(name)?.groupValues?.get(1)?.toIntOrNull()
        return XfinderConfig(
            raw = raw,
            protocol = protocolLabel(scheme),
            name = name.ifBlank { raw.substringBefore('?').substringBefore('#') },
            server = server,
            port = port,
            pingMs = ping,
        )
    }

    fun protocolLabel(scheme: String): String = when (scheme.lowercase()) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "SS"
        else -> scheme.uppercase()
    }

    private fun decodeFragment(fragment: String): String {
        if (fragment.isEmpty()) return ""
        return try {
            // '+' is kept literal so a name like "A+B" survives; spaces arrive as %20.
            URLDecoder.decode(fragment.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            fragment
        }
    }

    private fun extractHostPort(body: String): Pair<String, String> {
        val beforeQuery = body.substringBefore('#').substringBefore('?')
        val authority = beforeQuery.substringBefore('/')
        if (authority.contains('@')) return splitHostPort(authority.substringAfterLast('@'))
        // Legacy ss://BASE64(method:pass@host:port); base64 itself may contain '/', so decode before cutting at it.
        val decoded = decodeBase64(beforeQuery.trimEnd('/')) ?: return splitHostPort(authority)
        return splitHostPort(decoded.substringAfterLast('@'))
    }

    private fun splitHostPort(hostPort: String): Pair<String, String> {
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end > 0) {
                return hostPort.substring(1, end) to hostPort.substring(end + 1).removePrefix(":")
            }
        }
        val idx = hostPort.lastIndexOf(':')
        return if (idx > 0) hostPort.substring(0, idx) to hostPort.substring(idx + 1) else hostPort to ""
    }

    private fun decodeVmess(payload: String): Map<String, String>? {
        val text = decodeBase64(payload) ?: return null
        return try {
            val obj = JsonParser.parseString(text).asJsonObject
            obj.entrySet().associate { (k, v) -> k to (if (v.isJsonNull) "" else v.asString) }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBase64(input: String): String? {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        if (clean.isEmpty()) return null
        return try {
            String(Base64.getMimeDecoder().decode(clean.replace('-', '+').replace('_', '/')), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** One scanned CDN IP from `clean_ips.json`. */
data class ScannedIp(
    val ip: String,
    val ms: Int,
    val colo: String?,
    val country: String?,
    val source: String,
    val sourceEmoji: String,
    val pinned: Boolean,
) {
    /** "LAX • USA", or whichever half exists. */
    val location: String
        get() = listOfNotNull(colo?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
            .joinToString(" • ")
}

/** Counters shown in the three boxes above the radar. */
data class ScannerStats(val total: Int, val online: Int, val pinned: Int)

data class ScannerData(val ips: List<ScannedIp>, val stats: ScannerStats)

/** Pure parser for the Scanner-matix JSON. */
object ScannerParser {
    private val fallbackEmoji = mapOf("cloudflare" to "☁️", "fastly" to "⚡", "cloudfront" to "🟧")

    fun parse(json: String?): ScannerData? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val results = root.getAsJsonArray("results") ?: return null
            val ips = results.mapNotNull { element ->
                val o = element.asJsonObject
                val ip = o.get("ip")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                val source = o.get("source")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                ScannedIp(
                    ip = ip,
                    ms = o.get("ms")?.takeIf { !it.isJsonNull }?.asInt ?: Int.MAX_VALUE,
                    colo = o.get("colo")?.takeIf { !it.isJsonNull }?.asString,
                    country = o.get("country")?.takeIf { !it.isJsonNull }?.asString,
                    source = source,
                    sourceEmoji = o.get("source_emoji")?.takeIf { !it.isJsonNull }?.asString
                        ?: fallbackEmoji[source].orEmpty(),
                    pinned = o.get("pinned")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                )
            }.distinctBy { it.ip }
            val sorted = sortIps(ips)
            val online = root.get("online_count")?.takeIf { !it.isJsonNull }?.asInt ?: sorted.size
            val total = root.get("total_tested")?.takeIf { !it.isJsonNull }?.asInt ?: sorted.size
            val pinned = root.get("pinned_count")?.takeIf { !it.isJsonNull }?.asInt
                ?: sorted.count { it.pinned }
            ScannerData(sorted, ScannerStats(total = total, online = online, pinned = pinned))
        } catch (e: Exception) {
            null
        }
    }

    /** Pinned IPs first, then ascending latency within each group. */
    fun sortIps(ips: List<ScannedIp>): List<ScannedIp> =
        ips.sortedWith(compareByDescending<ScannedIp> { it.pinned }.thenBy { it.ms })
}
