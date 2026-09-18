package com.v2ray.ang.util

import com.v2ray.ang.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/**
 * Per-subscription content transformer used right after the subscription body
 * is fetched and before it is parsed into profiles.
 *
 * Two independent switches driven by the subscription settings:
 *  - [decodeBase64ToText]: whole-body or per-line base64 blobs are decoded to
 *    plain link lists / JSON text.
 *  - [customToLinks]: custom JSON configs (xray outbound objects/arrays,
 *    sing-box style entries) are converted into regular share links so they
 *    can be edited with the normal profile UI.
 */
object SubscriptionContentConverter {

    fun convert(content: String, decodeBase64: Boolean, customToLinks: Boolean, keepBalancers: Boolean = false): String {
        // We don't early return here anymore so we can always compact JSONs.
        LogUtil.i("SubConverter", "v2 convert: b64=$decodeBase64 links=$customToLinks balancers=$keepBalancers")
        return walk(content, decodeBase64, customToLinks, keepBalancers)
    }

    /**
     * Splits text that contains multiple top-level JSON objects separated by
     * newlines into individual JSON strings. Uses a brace-depth counter so
     * nested objects inside arrays don't cause premature splits.
     */
    private fun splitConcatenatedJson(text: String): List<String> {
        val trimmed = text.trim()
        // A single whole JSON value doesn't need splitting.
        if (isWholeJsonValue(trimmed)) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false

        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue

            when {
                c == '{' || c == '[' -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' || c == ']' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        chunks.add(trimmed.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return chunks.ifEmpty { listOf(trimmed) }
    }

    private fun walk(input: String, b64: Boolean, j2l: Boolean, kb: Boolean = false): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return input

        // If the input contains multiple top-level JSON objects concatenated,
        // process each object independently to avoid destroying pretty-printed JSON.
        if (trimmed.startsWith("{")) {
            val split = splitConcatenatedJson(trimmed)
            if (split.size > 1) {
                val out = StringBuilder()
                for (chunk in split) {
                    val p = walk(chunk, b64, j2l, kb)
                    if (p.isNotBlank()) out.append(p).append("\n")
                }
                return out.toString().trim()
            }
        }

        // Whole body is one base64 blob -> decode first and recurse.
        if (b64) {
            tryDecodeBase64(trimmed)?.let { decoded ->
                return walk(decoded, b64, j2l, kb)
            }
        }

        // Whole body is a single JSON value (compact or pretty) -> handle it.
        if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && isWholeJsonValue(trimmed)) {
            if (j2l) {
                convertJsonText(trimmed, kb)?.let { return it }
            } else {
                return try {
                    if (trimmed.startsWith("[")) {
                        val arr = JSONArray(trimmed)
                        val sb = StringBuilder()
                        for (i in 0 until arr.length()) {
                            sb.append(arr.getJSONObject(i).toString()).append("\n")
                        }
                        sb.toString().trim()
                    } else {
                        JSONObject(trimmed).toString()
                    }
                } catch (e: Exception) {
                    trimmed
                }
            }
        }

        val res = StringBuilder()
        input.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach

            if (b64) {
                tryDecodeBase64(t)?.let { decoded ->
                    res.append(walk(decoded, b64, j2l, kb)).append("\n")
                    return@forEach
                }
            }

            if (j2l && (t.startsWith("{") || t.startsWith("["))) {
                // When keepBalancers is on, balancer configs pass through as raw JSON.
                if (kb && t.startsWith("{")) {
                    try {
                        val probe = JSONObject(t)
                        if (isBalancerConfig(probe)) {
                            res.append(probe.toString()).append("\n")
                            return@forEach
                        }
                    } catch (_: Throwable) { }
                }
                convertJsonText(t, kb)?.let {
                    res.append(it).append("\n")
                    return@forEach
                }
            }

            res.append(t).append("\n")
        }
        return res.toString().trim()
    }

    /** true if the whole string is one valid JSON value (no trailing junk). */
    private fun isWholeJsonValue(s: String): Boolean = try {
        val tokener = JSONTokener(s)
        tokener.nextValue()
        tokener.nextClean().code == 0
    } catch (_: Throwable) {
        false
    }

    /** True when a config is a balancer/multi-server that must stay as custom JSON. */
    private fun isBalancerConfig(root: org.json.JSONObject): Boolean {
        // Has routing.balancers array
        if (root.optJSONObject("routing")?.optJSONArray("balancers")?.length() ?: 0 > 0) return true
        // Multiple proxy outbounds
        val obs = root.optJSONArray("outbounds") ?: return false
        var proxyCount = 0
        for (i in 0 until obs.length()) {
            val o = obs.optJSONObject(i) ?: continue
            val p = o.optString("protocol", o.optString("type"))
            if (p in PROXY_PROTOCOLS || isShadowsocks(o)) proxyCount++
        }
        return proxyCount > 1
    }

    private fun convertJsonText(t: String, keepBalancers: Boolean = false): String? = try {
        if (t.startsWith("[")) {
            val arr = JSONArray(t)
            val out = StringBuilder()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj == null) {
                    arr.opt(i)?.toString()?.takeIf { it != "null" && it.isNotEmpty() }
                        ?.let { out.append(it).append("\n") }
                    continue
                }
                // Full configs carry many proxy outbounds; emit one link each.
                val links = processJsonAll(obj, keepBalancers)
                if (links.isNotEmpty()) {
                    links.forEach { out.append(it).append("\n") }
                } else {
                    out.append(obj.toString()).append("\n")
                }
            }
            out.toString().trim().takeIf { it.isNotEmpty() }
        } else {
            processJsonAll(JSONObject(t), keepBalancers).joinToString("\n").takeIf { it.isNotEmpty() }
        }
    } catch (_: Throwable) {
        null
    }

    private val PROXY_SCHEMES = arrayOf(
        "vless://", "vmess://", "trojan://", "ss://", "ssr://",
        "hysteria://", "hysteria2://", "hy2://", "tuic://", "socks://",
        "http://", "https://"
    )

    /**
     * Decodes a base64 blob only when the result looks like a config list:
     * strict UTF-8, no control characters, first line starts with a proxy
     * scheme or JSON. Anything else returns null and the text passes through.
     */
    private fun tryDecodeBase64(input: String): String? {
        if (input.length < 10) return null
        val cleaned = input.trim()

        var hasStd = false
        var hasUrl = false
        for (c in cleaned) {
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' || c == '\t' ||
                    c == '=' || c == '\r' || c == '\n' -> {}
                c == '+' || c == '/' -> hasStd = true
                c == '-' || c == '_' -> hasUrl = true
                else -> return null
            }
        }
        if (hasStd && hasUrl) return null
        val flag = if (hasUrl) android.util.Base64.URL_SAFE else android.util.Base64.DEFAULT

        return try {
            val data = android.util.Base64.decode(cleaned, flag)
            if (data.isEmpty()) return null
            val decodedRaw = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(data))
                .toString()
            for (ch in decodedRaw) {
                val cc = ch.code
                if (cc == 0x7f || (cc < 0x20 && cc != 0x09 && cc != 0x0a && cc != 0x0d)) return null
            }
            val decoded = decodedRaw.trimStart()
            val firstLine = decoded.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart() ?: return null
            val looksLikeJson = firstLine.startsWith("{") || firstLine.startsWith("[")
            val looksLikeProxyList = PROXY_SCHEMES.any { firstLine.startsWith(it, ignoreCase = true) }
            if (looksLikeJson || looksLikeProxyList) decoded.trim() else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    //region JSON outbound -> share link

    /**
     * Processes one JSON config object. Balancer/multi-server configs return
     * empty list so the caller preserves them as compact JSON custom profiles.
     * Single-outbound configs are converted to their share link normally.
     */
    private fun processJsonAll(root: JSONObject, keepBalancers: Boolean = false): List<String> {
        // Balancer configs stay as custom only when keepBalancers is explicitly on.
        if (keepBalancers && isBalancerConfig(root)) return emptyList()
        // Bare sing-box / direct outbound object (single server by definition)
        val sbType = root.optString("type")
        if (sbType.isNotEmpty() && (root.has("server") || root.has("server_port"))) {
            val rem = root.optString("tag", "")
            val built = when (sbType) {
                "vless" -> buildSbVless(root, rem)
                "vmess" -> buildSbVmess(root, rem)
                "trojan" -> buildSbTrojan(root, rem)
                "shadowsocks" -> buildSbShadowsocks(root, rem)
                "hysteria2" -> buildSbHysteria2(root, rem)
                "tuic" -> buildTuic(root, rem)
                "socks" -> buildUserPassLink("socks", root, rem)
                "http" -> buildUserPassLink("http", root, rem)
                else -> null
            }
            return if (built != null) listOf(built) else emptyList()
        }

        // Bare vmess/tuic outbound
        val proto0 = root.optString("protocol", root.optString("type"))
        if (proto0 == "vmess") {
            buildVmess(root, root.optString("tag", root.optString("remarks", "")))?.let {
                return listOf(it)
            }
            return emptyList()
        }
        if (proto0 == "tuic") {
            buildTuic(root, root.optString("tag", root.optString("remarks", "")))?.let {
                return listOf(it)
            }
            return emptyList()
        }

        // Full xray config: count convertible proxy outbounds
        val obs = root.optJSONArray("outbounds")
            ?: return if (isShadowsocks(root)) {
                buildShadowsocks(root, root.optString("remarks", ""))?.let { listOf(it) } ?: emptyList()
            } else emptyList()

        val rem = root.optString("remarks", "")
        val proxies = mutableListOf<Pair<org.json.JSONObject, String>>() // ob to protocol
        for (i in 0 until obs.length()) {
            val ob = obs.optJSONObject(i) ?: continue
            val p = ob.optString("protocol", ob.optString("type"))
            if (p in PROXY_PROTOCOLS || isShadowsocks(ob)) {
                proxies.add(Pair(ob, p))
            }
        }

        // Exactly one proxy outbound -> convert to a link.
        if (proxies.size == 1) {
            val (ob, p) = proxies[0]
            val built = when (p) {
                "vless" -> buildVless(ob, rem)
                "vmess" -> buildVmess(ob, rem)
                "shadowsocks" -> buildShadowsocks(ob, rem)
                "trojan" -> buildTrojan(ob, rem)
                "hysteria", "hysteria2" -> buildHysteriaX(ob, rem)
                "tuic" -> buildTuic(ob, rem)
                else -> if (isShadowsocks(ob)) buildShadowsocks(ob, rem) else null
            }
            if (built != null) return listOf(built)
        }

        // Multiple proxy outbounds (balancer / multi-server) -> emit one link per proxy.
        if (proxies.size > 1) {
            val links = mutableListOf<String>()
            for ((ob, p) in proxies) {
                val built = when (p) {
                    "vless" -> buildVless(ob, rem)
                    "vmess" -> buildVmess(ob, rem)
                    "shadowsocks" -> buildShadowsocks(ob, rem)
                    "trojan" -> buildTrojan(ob, rem)
                    "hysteria", "hysteria2" -> buildHysteriaX(ob, rem)
                    "tuic" -> buildTuic(ob, rem)
                    else -> if (isShadowsocks(ob)) buildShadowsocks(ob, rem) else null
                }
                if (built != null) links.add(built)
            }
            if (links.isNotEmpty()) return links
        }

        return emptyList()
    }

    private val PROXY_PROTOCOLS = setOf(
        "vless", "vmess", "shadowsocks", "trojan",
        "hysteria", "hysteria2", "tuic", "socks", "http"
    )

    private fun isShadowsocks(obj: JSONObject): Boolean {
        if (obj.has("server") && obj.has("server_port") && obj.has("password") && obj.has("method")) return true
        val servers = obj.optJSONObject("settings")?.optJSONArray("servers")
        if (servers != null && servers.length() > 0) {
            val s = servers.getJSONObject(0)
            if (s.has("address") && s.has("port") && s.has("password") && s.has("method")) return true
        }
        return false
    }

    private fun buildVless(ob: JSONObject, rem: String): String? = try {
        val s = ob.optJSONObject("settings") ?: return null
        val vnext = s.optJSONArray("vnext") ?: return null
        if (vnext.length() == 0) return null
        val vn = vnext.getJSONObject(0)
        val users = vn.optJSONArray("users") ?: return null
        if (users.length() == 0) return null
        val u = users.getJSONObject(0)
        val ss = ob.optJSONObject("streamSettings")
        val rs = ss?.optJSONObject("realitySettings")
        val enc = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        val tlsSettings = ss?.optJSONObject("tlsSettings")
        val tlsAlpnArr = tlsSettings?.optJSONArray("alpn")
        val tlsAlpn = tlsAlpnArr?.let { a -> (0 until a.length()).mapNotNull { a.optString(it) }
            .filter { it.isNotEmpty() }.joinToString(",") } ?: ""
        val tlsInsecure = tlsSettings?.optBoolean("allowInsecure", false) == true
        val tlsFp = tlsSettings?.optString("fingerprint", "") ?: ""
        val fp = rs?.optString("fingerprint", "")?.takeIf { it.isNotEmpty() }
            ?: tlsFp.takeIf { it.isNotEmpty() } ?: "chrome"
        val pbk = rs?.optString("publicKey", "") ?: ""
        val sid = rs?.optString("shortId", "") ?: ""
        val sni = rs?.optString("serverName", "") ?: ""
        val security = ss?.optString("security", "none") ?: "none"
        val type = ss?.optString("network", "tcp") ?: "tcp"
        val params = linkedMapOf(
            "encryption" to u.optString("encryption", "none"),
            "flow" to u.optString("flow", ""),
            "fp" to fp,
            "pbk" to pbk,
            "security" to security,
            "sid" to sid,
            "sni" to sni,
            "type" to type,
        )
        if (tlsAlpn.isNotEmpty()) params["alpn"] = tlsAlpn
        if (tlsInsecure) params["allowInsecure"] = "1"
        // Transports are unusable without their parameters
        val wsSettings = ss?.optJSONObject("wsSettings")
        val httpSettings = ss?.optJSONObject("httpSettings")
        (wsSettings?.optString("path") ?: httpSettings?.optString("path"))
            ?.takeIf { it.isNotEmpty() }?.let { params["path"] = it }
        (wsSettings?.optJSONObject("headers")?.optString("Host")
            ?: httpSettings?.optJSONArray("host")?.optString(0))
            ?.takeIf { it.isNotEmpty() }?.let { params["host"] = it }
        val grpcSettings = ss?.optJSONObject("grpcSettings")
        grpcSettings?.optString("serviceName")?.takeIf { it.isNotEmpty() }?.let { params["serviceName"] = it }
        grpcSettings?.optString("mode")?.takeIf { it.isNotEmpty() }?.let { params["mode"] = it }
        grpcSettings?.optString("authority")?.takeIf { it.isNotEmpty() }?.let { params["authority"] = it }
        // multi-host http outbound: Host is an array
        httpSettings?.optJSONArray("host")?.let { a ->
            val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                .joinToString(",")
            if (j.isNotEmpty()) params["host"] = j
        }
        // tcp/kcp/quic header types and kcp tuning
        when (type) {
            "tcp" -> ss?.optJSONObject("tcpSettings")?.optJSONObject("header")
                ?.optString("type")?.takeIf { it != "none" }?.let { params["headerType"] = it }
            "kcp" -> {
                ss?.optJSONObject("kcpSettings")?.optJSONObject("header")
                    ?.optString("type")?.takeIf { it != "none" }?.let { params["headerType"] = it }
                ss?.optJSONObject("kcpSettings")?.optString("seed")
                    ?.takeIf { it.isNotEmpty() }?.let { params["seed"] = it }
                ss?.optJSONObject("kcpSettings")?.optInt("mtu")?.takeIf { it > 0 }
                    ?.let { params["mtu"] = it.toString() }
                ss?.optJSONObject("kcpSettings")?.optInt("tti")?.takeIf { it > 0 }
                    ?.let { params["tti"] = it.toString() }
            }
            "quic" -> {
                val q = ss?.optJSONObject("quicSettings")
                q?.optString("security")?.takeIf { it.isNotEmpty() }?.let { params["quicSecurity"] = it }
                q?.optString("key")?.takeIf { it.isNotEmpty() }?.let { params["key"] = it }
                q?.optJSONObject("header")?.optString("type")?.takeIf { it != "none" }
                    ?.let { params["headerType"] = it }
            }
            "httpupgrade" -> ss?.optJSONObject("httpupgradeSettings")?.let { h ->
                h.optString("path")?.takeIf { it.isNotEmpty() }?.let { params["path"] = it }
                h.optJSONObject("headers")?.optString("Host")
                    ?.takeIf { it.isNotEmpty() }?.let { params["host"] = it }
            }
            "splithttp", "splitHttp", "xhttp" -> ss?.optJSONObject("splitHttpSettings")
                ?.takeIf { it.length() > 0 } ?: ss?.optJSONObject("xhttpSettings")?.let { x ->
                x.optString("path")?.takeIf { it.isNotEmpty() }?.let { params["path"] = it }
                x.optJSONArray("host")?.let { a ->
                    val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                        .joinToString(",")
                    if (j.isNotEmpty()) params["host"] = j
                }
                x.optString("mode")?.takeIf { it.isNotEmpty() }?.let { params["mode"] = it }
                x.optJSONObject("extra")?.toString()?.takeIf { it != "null" }?.let { params["extra"] = it }
            }
        }
        // xhttp transport
        ss?.optJSONObject("xhttpSettings")?.let { x ->
            x.optString("path")?.takeIf { it.isNotEmpty() }?.let { params["path"] = it }
            x.optJSONArray("host")?.let { a ->
                val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                    .joinToString(",")
                if (j.isNotEmpty()) params["host"] = j
            }
            x.optString("mode")?.takeIf { it.isNotEmpty() }?.let { params["mode"] = it }
            x.optJSONObject("extra")?.toString()?.takeIf { it != "null" }?.let { params["extra"] = it }
        }
        // shop QUIC tuning block (finalmask) rides along as fm
        ss?.optJSONObject("finalmask")?.toString()
            ?.takeIf { it != "null" && it.isNotEmpty() }?.let { params["fm"] = it }
        // reality spiderX
        rs?.optString("spiderX")?.takeIf { it.isNotEmpty() }?.let { params["spx"] = it }
        // TLS ECH
        tlsSettings?.optString("echConfigList")?.takeIf { it.isNotEmpty() }
            ?.let { params["ech"] = it }
        val query = params.filterValues { it.isNotEmpty() }
            .entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        "vless://${u.getString("id")}@${vn.getString("address")}:${vn.getInt("port")}?$query#$enc"
    } catch (_: Exception) {
        null
    }

    private fun buildVmess(ob: JSONObject, rem: String): String? = try {
        val linkJson = JSONObject()
        linkJson.put("v", "2")

        val settings = ob.optJSONObject("settings")
        val vnext = settings?.optJSONArray("vnext")?.optJSONObject(0)

        val addr = ob.optString("server", vnext?.optString("address", "") ?: "")
        val port = if (ob.has("server_port")) ob.getInt("server_port") else vnext?.optInt("port", 0) ?: 0
        val user0 = vnext?.optJSONArray("users")?.optJSONObject(0)
        val uuid = ob.optString("uuid", user0?.optString("id", "") ?: "")
        val alterId = user0?.optInt("alterId", 0) ?: 0
        val security = user0?.optString("security", "auto") ?: "auto"

        linkJson.put("add", addr)
        linkJson.put("port", port.toString())
        linkJson.put("id", uuid)
        linkJson.put("aid", alterId.toString())
        linkJson.put("scy", security)

        val transport = ob.optJSONObject("transport")
        val stream = ob.optJSONObject("streamSettings")
        val net = transport?.optString("type") ?: stream?.optString("network") ?: "tcp"
        linkJson.put("net", net)

        val tlsObj = ob.optJSONObject("tls")
        val isTls = tlsObj?.optBoolean("enabled") ?: (stream?.optString("security") == "tls")
        linkJson.put("tls", if (isTls) "tls" else "")
        val xTls = stream?.optJSONObject("tlsSettings")
        xTls?.optString("serverName")?.takeIf { it.isNotEmpty() }?.let { linkJson.put("sni", it) }
        xTls?.optJSONArray("alpn")?.let { a ->
            val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                .joinToString(",")
            if (j.isNotEmpty()) linkJson.put("alpn", j)
        }
        xTls?.optString("fingerprint")?.takeIf { it.isNotEmpty() }?.let { linkJson.put("fp", it) }
        if (xTls?.optBoolean("allowInsecure", false) == true) linkJson.put("allowInsecure", 1)

        if (net == "ws") {
            val ws = transport ?: stream?.optJSONObject("wsSettings")
            linkJson.put("path", ws?.optString("path"))
            val host = ws?.optJSONObject("headers")?.optString("Host") ?: ws?.optString("headers")
            if (host != null) linkJson.put("host", host)
        }

        val finalRem = if (ob.has("tag")) ob.getString("tag") else ob.optString("remarks", rem)
        linkJson.put("ps", finalRem)

        val base64 = android.util.Base64.encodeToString(linkJson.toString().toByteArray(), android.util.Base64.NO_WRAP)
        "vmess://$base64"
    } catch (_: Exception) {
        null
    }

    private fun buildShadowsocks(ob: JSONObject, rem: String): String? = try {
        val address: String
        val port: Int
        val method: String
        val password: String
        if (ob.has("server")) {
            address = ob.getString("server")
            port = ob.getInt("server_port")
            method = ob.getString("method")
            password = ob.getString("password")
        } else {
            val s = ob.optJSONObject("settings")?.optJSONArray("servers")?.getJSONObject(0) ?: return null
            address = s.getString("address")
            port = s.getInt("port")
            method = s.getString("method")
            password = s.getString("password")
        }
        val credentials = "$method:$password"
        val ui = android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
        val finalRem = ob.optString("remarks", rem)
        val encRem = URLEncoder.encode(finalRem, "UTF-8").replace("+", "%20")
        val plugin = ob.optString("plugin")
        val pluginQ = if (plugin.isNullOrEmpty()) "" else "/?plugin=" + URLEncoder.encode(plugin, "UTF-8")
        "ss://$ui@$address:$port$pluginQ#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildTrojan(ob: JSONObject, rem: String): String? = try {
        val server = ob.optJSONObject("settings")?.optJSONArray("servers")?.optJSONObject(0) ?: return null
        val address = server.optString("address")
        val port = server.optInt("port")
        val password = server.optString("password")

        val ss = ob.optJSONObject("streamSettings")
        val network = ss?.optString("network")
        val security = ss?.optString("security")

        val query = mutableMapOf<String, String>()
        if (!network.isNullOrEmpty()) query["type"] = network

        val tTls = ss?.optJSONObject("tlsSettings") ?: ss?.optJSONObject("realitySettings")
        if (security == "tls" || security == "reality") {
            tTls?.optString("serverName")?.takeIf { it.isNotEmpty() }?.let {
                query["sni"] = it
                query["host"] = it
            }
        }
        tTls?.optJSONArray("alpn")?.let { a ->
            val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                .joinToString(",")
            if (j.isNotEmpty()) query["alpn"] = j
        }
        tTls?.optString("fingerprint")?.takeIf { it.isNotEmpty() }?.let { query["fp"] = it }
        if (tTls?.optBoolean("allowInsecure", false) == true) query["allowInsecure"] = "1"

        if (network == "ws") {
            val ws = ss?.optJSONObject("wsSettings")
            ws?.optString("path")?.takeIf { it.isNotEmpty() }?.let { query["path"] = it }
            ws?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotEmpty() }?.let { query["host"] = it }
        }

        val queryStr = query.entries.sortedBy { it.key }.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val queryString = if (queryStr.isNotEmpty()) "?$queryStr" else ""
        val encPass = URLEncoder.encode(password, "UTF-8")
        val encRem = URLEncoder.encode(ob.optString("remarks", rem), "UTF-8").replace("+", "%20")

        "trojan://$encPass@$address:$port$queryString#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildHysteria2(ob: JSONObject, rem: String): String? = try {
        val server = ob.optJSONObject("settings")?.optJSONArray("servers")?.optJSONObject(0) ?: return null
        val address = server.optString("address")
        val port = server.optInt("port")

        val ss = ob.optJSONObject("streamSettings")
        val hy2 = ss?.optJSONObject("hy2Settings")
        val password = hy2?.optString("password") ?: ""
        val obfs = hy2?.optJSONObject("obfs")
        val obfsType = obfs?.optString("type")
        val obfsPassword = obfs?.optString("password")
        val sni = ss?.optJSONObject("tlsSettings")?.optString("serverName")

        val query = StringBuilder()
        if (!obfsType.isNullOrEmpty()) query.append("&obfs=").append(URLEncoder.encode(obfsType, "UTF-8"))
        if (!obfsPassword.isNullOrEmpty()) query.append("&obfs-password=").append(URLEncoder.encode(obfsPassword, "UTF-8"))
        if (!sni.isNullOrEmpty()) query.append("&sni=").append(URLEncoder.encode(sni, "UTF-8"))

        val queryString = if (query.isNotEmpty()) "?" + query.substring(1) else ""
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")

        "hysteria2://$password@$address:$port$queryString#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildTuic(ob: JSONObject, rem: String): String? = try {
        val address = ob.optString("server")
        val port = ob.optInt("server_port")
        val uuid = ob.optString("uuid")
        val password = ob.optString("password")

        val query = mutableMapOf<String, String>()
        ob.optString("congestion_control").takeIf { it.isNotEmpty() }?.let { query["congestion_control"] = it }
        ob.optString("udp_relay_mode").takeIf { it.isNotEmpty() }?.let { query["udp_relay_mode"] = it }

        val tls = ob.optJSONObject("tls")
        if (tls != null && tls.optBoolean("enabled", false)) {
            tls.optString("server_name").takeIf { it.isNotEmpty() }?.let { query["sni"] = it }
            tls.optJSONArray("alpn")?.optString(0)?.takeIf { it.isNotEmpty() }?.let { query["alpn"] = it }
            if (tls.optBoolean("insecure", false)) query["allow_insecure"] = "1"
        }

        val queryStr = query.entries.sortedBy { it.key }.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val queryString = if (queryStr.isNotEmpty()) "?$queryStr" else ""

        val finalRem = if (ob.has("tag")) ob.getString("tag") else ob.optString("remarks", rem)
        val encRem = URLEncoder.encode(finalRem, "UTF-8").replace("+", "%20")

        "tuic://$uuid:$password@$address:$port$queryString#$encRem"
    } catch (_: Exception) {
        null
    }

    /**
     * Hysteria v1/v2 as served inside full xray configs by proxy shops:
     * address/port live either in settings{address,port} or
     * settings.servers[0], auth in hysteriaSettings{version,auth}.
     */
    private fun buildHysteriaX(ob: JSONObject, rem: String): String? = try {
        val settings = ob.optJSONObject("settings")
        val serverObj = settings?.optJSONArray("servers")?.optJSONObject(0)
        val address = (settings?.optString("address", "") ?: "")
            .ifEmpty { serverObj?.optString("address", "") ?: "" }
        val port = if (settings?.has("port") == true) settings.optInt("port")
        else serverObj?.optInt("port", 0) ?: 0
        if (address.isEmpty() || port <= 0) return null

        val stream = ob.optJSONObject("streamSettings")
        val hy = stream?.optJSONObject("hysteriaSettings")
        val version = hy?.optInt("version")
            ?: settings?.optInt("version")
            ?: if (ob.optString("protocol") == "hysteria2") 2 else 1
        // Our parser has no hysteria v1 - keep the entry as a custom config
        // instead of emitting a dead link.
        if (version < 2) return null
        val auth = hy?.optString("auth")
            ?: serverObj?.optString("password")
            ?: settings?.optString("auth")
            ?: ""

        val tls = ob.optJSONObject("streamSettings")?.optJSONObject("tlsSettings")
        val fmObj = stream?.optJSONObject("finalmask")?.toString()
            ?: ob.optJSONObject("streamSettings")?.optJSONObject("finalmask")?.toString()

        val sni = tls?.optString("serverName")
        val alpn = tls?.optJSONArray("alpn")?.optString(0)

        val query = StringBuilder()
        if (version < 2 && auth.isNotEmpty()) query.append("&auth=").append(URLEncoder.encode(auth, "UTF-8"))
        sni?.takeIf { it.isNotEmpty() }?.let { query.append("&sni=").append(URLEncoder.encode(it, "UTF-8")) }
        alpn?.takeIf { it.isNotEmpty() }?.let { query.append("&alpn=").append(URLEncoder.encode(it, "UTF-8")) }
        if (tls?.optBoolean("allowInsecure", false) == true) query.append("&insecure=1")
        val obfsObj = hy?.optJSONObject("obfs") ?: settings?.optJSONObject("obfs")
        obfsObj?.optString("type")?.takeIf { it == "salamander" }?.let { query.append("&obfs=salamander") }
        obfsObj?.optString("password")?.takeIf { it.isNotEmpty() }
            ?.let { query.append("&obfs-password=").append(URLEncoder.encode(it, "UTF-8")) }
        hy?.optString("pinSHA256")?.takeIf { it.isNotEmpty() }
            ?.let { query.append("&pinSHA256=").append(URLEncoder.encode(it, "UTF-8")) }
        // finalmask/fm intentionally omitted: the URL-encoded JSON blob
        // breaks URI parsing and the connection parameters it carries are
        // bandwidth tuning hints, not connection requirements.
        val queryString = if (query.isNotEmpty()) "?" + query.substring(1) else ""

        val encRem = URLEncoder.encode(ob.optString("remarks", rem), "UTF-8").replace("+", "%20")
        if (version >= 2) {
            // v2 carries auth in userinfo position
            "hysteria2://$auth@$address:$port$queryString#$encRem"
        } else {
            "hysteria://$address:$port$queryString#$encRem"
        }
    } catch (_: Exception) {
        null
    }

    //region sing-box direct outbounds

    private fun sbTls(root: JSONObject): JSONObject? =
        root.optJSONObject("tls")?.takeIf { it.optBoolean("enabled", true) }

    private fun sbTransportType(root: JSONObject): String =
        root.optJSONObject("transport")?.optString("type")?.takeIf { it.isNotEmpty() } ?: "tcp"

    private fun buildSbVless(root: JSONObject, rem: String): String? = try {
        val uuid = root.optString("uuid")
        val address = root.optString("server")
        val port = root.optInt("server_port")
        if (uuid.isEmpty() || address.isEmpty() || port <= 0) return null

        val tls = sbTls(root)
        val sbAlpnArr = tls?.optJSONArray("alpn")
        val sbAlpn = sbAlpnArr?.let { a -> (0 until a.length()).mapNotNull { a.optString(it) }
            .filter { it.isNotEmpty() }.joinToString(",") } ?: ""
        val sbInsec = tls?.optBoolean("insecure", false) == true
        val reality = tls?.optJSONObject("reality")
        val params = linkedMapOf(
            "encryption" to "none",
            "flow" to root.optString("flow", ""),
            "fp" to (tls?.optJSONObject("utls")?.optString("fingerprint") ?: ""),
            "pbk" to (reality?.optString("public_key") ?: ""),
            "security" to if (tls != null) "tls" else "none",
            "sid" to (reality?.optString("short_id") ?: ""),
            "sni" to (tls?.optString("server_name") ?: ""),
            "type" to sbTransportType(root),
        )
        val transport = root.optJSONObject("transport")
        val query = StringBuilder()
        params.forEach { (k, v) ->
            if (v.isNotEmpty()) query.append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8"))
        }
        transport?.optString("path")?.takeIf { it.isNotEmpty() }?.let {
            query.append("&path=").append(URLEncoder.encode(it, "UTF-8"))
        }
        transport?.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotEmpty() }?.let {
            query.append("&host=").append(URLEncoder.encode(it, "UTF-8"))
        }
        if (sbAlpn.isNotEmpty()) query.append("&alpn=").append(URLEncoder.encode(sbAlpn, "UTF-8"))
        if (sbInsec) query.append("&allowInsecure=1")
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        "vless://$uuid@$address:$port?${query.substring(1)}#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildSbVmess(root: JSONObject, rem: String): String? = try {
        val linkJson = JSONObject()
        linkJson.put("v", "2")
        linkJson.put("add", root.optString("server"))
        linkJson.put("port", root.optInt("server_port").toString())
        linkJson.put("id", root.optString("uuid"))
        linkJson.put("aid", root.optInt("alter_id", 0).toString())
        linkJson.put("scy", root.optString("security", "auto"))

        val net = sbTransportType(root)
        linkJson.put("net", net)
        val tls = sbTls(root)
        linkJson.put("tls", if (tls != null) "tls" else "")
        linkJson.put("sni", tls?.optString("server_name") ?: "")

        if (net == "ws") {
            val ws = root.optJSONObject("transport")
            linkJson.put("path", ws?.optString("path"))
            val host = ws?.optJSONObject("headers")?.optString("Host")
            if (host != null) linkJson.put("host", host)
        }

        linkJson.put("ps", rem)
        val base64 = android.util.Base64.encodeToString(linkJson.toString().toByteArray(), android.util.Base64.NO_WRAP)
        "vmess://$base64"
    } catch (_: Exception) {
        null
    }

    private fun buildSbTrojan(root: JSONObject, rem: String): String? = try {
        val password = root.optString("password")
        val address = root.optString("server")
        val port = root.optInt("server_port")
        if (password.isEmpty() || address.isEmpty() || port <= 0) return null

        val query = mutableMapOf<String, String>()
        query["type"] = sbTransportType(root)
        val sbTlsObj = sbTls(root)
        sbTlsObj?.optString("server_name")?.takeIf { it.isNotEmpty() }?.let {
            query["sni"] = it
            query["host"] = it
        }
        sbTlsObj?.optJSONArray("alpn")?.let { a ->
            val j = (0 until a.length()).mapNotNull { a.optString(it) }.filter { it.isNotEmpty() }
                .joinToString(",")
            if (j.isNotEmpty()) query["alpn"] = j
        }
        if (sbTlsObj?.optBoolean("insecure", false) == true) query["allowInsecure"] = "1"
        val queryStr = query.entries.sortedBy { it.key }.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val encPass = URLEncoder.encode(password, "UTF-8")
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        "trojan://$encPass@$address:$port?$queryStr#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildSbShadowsocks(root: JSONObject, rem: String): String? = try {
        val method = root.optString("method")
        val password = root.optString("password")
        val address = root.optString("server")
        val port = root.optInt("server_port")
        if (method.isEmpty() || address.isEmpty() || port <= 0) return null
        val ui = android.util.Base64.encodeToString("$method:$password".toByteArray(), android.util.Base64.NO_WRAP)
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        val plugin = root.optString("plugin")
        val pluginQ = if (plugin.isEmpty()) "" else "/?plugin=" + URLEncoder.encode(plugin, "UTF-8")
        "ss://$ui@$address:$port$pluginQ#$encRem"
    } catch (_: Exception) {
        null
    }

    private fun buildSbHysteria2(root: JSONObject, rem: String): String? = try {
        val password = root.optString("password") ?: ""
        val address = root.optString("server")
        val port = root.optInt("server_port")
        if (address.isEmpty() || port <= 0) return null

        val obfs = root.optJSONObject("obfs")
        val sbTls2 = root.optJSONObject("tls")
        val sni = sbTls2?.optString("server_name")
        val sbAlpnArr2 = sbTls2?.optJSONArray("alpn")
        val sbAlpn2 = sbAlpnArr2?.let { a -> (0 until a.length()).mapNotNull { a.optString(it) }
            .filter { it.isNotEmpty() }.joinToString(",") } ?: ""

        val query = StringBuilder()
        obfs?.optString("type")?.takeIf { it.isNotEmpty() }?.let { query.append("&obfs=").append(URLEncoder.encode(it, "UTF-8")) }
        obfs?.optString("password")?.takeIf { it.isNotEmpty() }?.let { query.append("&obfs-password=").append(URLEncoder.encode(it, "UTF-8")) }
        sni?.takeIf { it.isNotEmpty() }?.let { query.append("&sni=").append(URLEncoder.encode(it, "UTF-8")) }
        if (sbAlpn2.isNotEmpty()) query.append("&alpn=").append(URLEncoder.encode(sbAlpn2, "UTF-8"))
        if (sbTls2?.optBoolean("insecure", false) == true) query.append("&insecure=1")
        root.optString("pinSHA256").takeIf { it.isNotEmpty() }
            ?.let { query.append("&pinSHA256=").append(URLEncoder.encode(it, "UTF-8")) }

        val queryString = if (query.isNotEmpty()) "?" + query.substring(1) else ""
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        "hysteria2://$password@$address:$port/$queryString#$encRem"
    } catch (_: Exception) {
        null
    }

    /** socks/http share links; v2rayNG parses user:pass@host:port natively. */
    private fun buildUserPassLink(scheme: String, root: JSONObject, rem: String): String? = try {
        val address = root.optString("server")
        val port = root.optInt("server_port")
        if (address.isEmpty() || port <= 0) return null
        val user = root.optString("username")
        val pass = root.optString("password")
        val userinfo = if (user.isEmpty() && pass.isEmpty()) "" else "$user:$pass@"
        val encRem = URLEncoder.encode(rem, "UTF-8").replace("+", "%20")
        "$scheme://$userinfo$address:$port#$encRem"
    } catch (_: Exception) {
        null
    }
    //endregion
}
