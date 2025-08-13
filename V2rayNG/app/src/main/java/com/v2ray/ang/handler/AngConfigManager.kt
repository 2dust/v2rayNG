package com.v2ray.ang.handler

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.HY2
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.fmt.CustomFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.handler.SpeedtestManager
import java.net.URI
import java.net.InetSocketAddress
import java.net.Socket

object AngConfigManager {

    // ====== Successive Halving Helpers ======
    /**
     * Candidate wrapper used during successive halving. Each candidate holds its
     * associated GUID, profile object and a mutable delay value used for ranking.
     */
    private data class Candidate(val guid: String, val profile: ProfileItem, var delayMs: Long = Long.MAX_VALUE)

    /**
     * Produces a deterministic signature for a profile in order to deduplicate
     * near‑identical nodes. This signature attempts to capture the aspects of a
     * profile that uniquely determine its connection characteristics: server, port,
     * network type and other transport parameters such as host, sni, path and flow.
     */
    private fun signatureForDedup(p: ProfileItem): String {
        val b = StringBuilder()
        b.append(p.server ?: "").append(':').append(p.serverPort ?: "")
        b.append('|').append(p.network ?: "")
        b.append('|').append(p.host ?: "").append('|').append(p.sni ?: "").append('|').append(p.path ?: "")
        b.append('|').append(p.publicKey ?: "").append('|').append(p.shortId ?: "")
        b.append('|').append(p.flow ?: "").append('|').append(p.headerType ?: "")
        b.append('|').append(p.username ?: "").append('|').append(p.method ?: "")
        return b.toString()
    }

    /**
     * Deduplicates a list of candidates based on their signature. Keeps the first
     * occurrence of each signature and discards duplicates.
     */
    private fun dedupCandidates(list: List<Candidate>): List<Candidate> {
        val seen = LinkedHashMap<String, Candidate>(list.size)
        for (c in list) {
            val sig = signatureForDedup(c.profile)
            if (!seen.containsKey(sig)) {
                seen[sig] = c
            }
        }
        return ArrayList(seen.values)
    }

    /**
     * Performs a very fast raw TCP connect for TCP-like transports in order to
     * quickly eliminate dead endpoints. For non‑TCP protocols (e.g. kcp/quic),
     * the candidate is kept without testing to avoid false negatives.
     *
     * @param cands the list of candidates to filter.
     * @param timeoutMs connection timeout in milliseconds.
     * @return a filtered list containing only candidates that respond to a raw
     * connect within the timeout or those that are UDP-only.
     */
    private fun preFilterByRawConnectCandidates(cands: List<Candidate>, timeoutMs: Int = 300): List<Candidate> {
        val tcpish = setOf<String?>(null, "", "tcp", "ws", "grpc", "h2", "http", "httpupgrade")
        val kept = ArrayList<Candidate>(cands.size)
        for (c in cands) {
            try {
                val host = c.profile.server ?: continue
                val port = c.profile.serverPort?.toIntOrNull() ?: continue
                val network = c.profile.network?.lowercase()
                if (network !in tcpish) {
                    // Skip raw connect for UDP‑based transports.
                    kept.add(c)
                    continue
                }
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    kept.add(c)
                }
            } catch (e: Exception) {
                Log.d(AppConfig.TAG, "SH: drop (raw connect) ${c.profile.remarks} ${c.profile.server}:${c.profile.serverPort} -> ${e.javaClass.simpleName}")
            }
        }
        return if (kept.isNotEmpty()) kept else cands
    }

    /**
     * Measures a realistic ping using the existing speedtest infrastructure in
     * V2rayConfigManager and SpeedtestManager. It tries to construct a temporary
     * V2ray configuration for the candidate GUID and then performs one or more
     * ping measurements, returning the best (lowest) latency observed.
     *
     * @param context Android context used to obtain speedtest configuration.
     * @param guid server GUID to test.
     * @param repeats number of times to measure; the minimum of these values is returned.
     * @return the measured latency in milliseconds, or a large sentinel value if failed.
     */
    private fun measureRealPingMs(context: Context, guid: String, repeats: Int = 1): Long {
        var best: Long = Long.MAX_VALUE
        for (i in 0 until repeats) {
            try {
                val conf = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
                if (!conf.status) continue
                val one = SpeedtestManager.realPing(conf.content)
                if (one > 0 && one < best) best = one
            } catch (_: Exception) {
                // ignore
            }
        }
        return if (best == Long.MAX_VALUE) -1L else best
    }

    /**
     * Core successive halving selection. Given a list of GUIDs, it deduplicates,
     * performs a raw connect filter, then iteratively measures latency and keeps
     * only the top performers until a target size is reached. The final pass
     * performs deeper measurements on the remaining candidates.
     *
     * @param context Android context used to obtain speedtest configuration.
     * @param guidList list of server GUIDs to select from.
     * @param targetK desired number of servers in the final selection.
     * @return a list of GUIDs representing the selected top candidates.
     */
    private fun successiveHalvingSelect(context: Context, guidList: List<String>, targetK: Int = 128): List<String> {
        if (guidList.isEmpty()) return emptyList()

        // Load candidate profiles and wrap into Candidate objects.
        val loaded = guidList.mapNotNull { g ->
            val p = MmkvManager.decodeServerConfig(g)
            if (p != null) Candidate(g, p) else null
        }

        // Stage 0: dedup identical endpoints.
        var pool = dedupCandidates(loaded)

        // For small lists, do a quick pass: raw filter then a single ping to order.
        if (pool.size <= targetK * 2) {
            pool = preFilterByRawConnectCandidates(pool, 300)
            for (c in pool) {
                c.delayMs = measureRealPingMs(context, c.guid, 1).let { if (it <= 0) Long.MAX_VALUE / 2 else it }
            }
            return pool.sortedBy { it.delayMs }.take(targetK).map { it.guid }
        }

        // Stage 1: raw connect filter.
        pool = preFilterByRawConnectCandidates(pool, 300)

        // Stage 2: iterative halving with light single ping.
        var current = pool
        var round = 0
        while (current.size > targetK * 4 && round < 3) {
            for (c in current) {
                c.delayMs = measureRealPingMs(context, c.guid, 1).let { if (it <= 0) Long.MAX_VALUE / 2 else it }
            }
            current = current.sortedBy { it.delayMs }
            val keep = kotlin.math.max(targetK * 2, current.size / 2)
            current = current.take(keep)
            round++
        }

        // Stage 3: final deep measurement on the remaining pool.
        val finalist = current.take(kotlin.math.max(targetK * 2, kotlin.math.min(current.size, targetK * 4)))
        for (c in finalist) {
            c.delayMs = measureRealPingMs(context, c.guid, 3).let { if (it <= 0) Long.MAX_VALUE / 2 else it }
        }
        return finalist.sortedBy { it.delayMs }.take(targetK).map { it.guid }
    }


    /**
     * Shares the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares non-custom configurations to the clipboard.
     *
     * @param context The context.
     * @param serverList The list of server GUIDs.
     * @return The number of configurations shared.
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
            return sb.lines().count() - 1
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share non-custom configs to clipboard", e)
            return -1
        }
    }

    /**
     * Shares the configuration as a QR code.
     *
     * @param guid The GUID of the configuration.
     * @return The QR code bitmap.
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return QRCodeDecoder.createQRCode(conf)

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config as QR code", e)
            return null
        }
    }

    /**
     * Shares the full content of the configuration to the clipboard.
     *
     * @param context The context.
     * @param guid The GUID of the configuration.
     * @return The result code.
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = V2rayConfigManager.getV2rayConfig(context, guid)
            if (result.status) {
                val config = MmkvManager.decodeServerConfig(guid)
                if (config?.configType == EConfigType.HYSTERIA2) {
                    val socksPort = Utils.findFreePort(listOf(100 + SettingsManager.getSocksPort(), 0))
                    val hy2Config = Hysteria2Fmt.toNativeConfig(config, socksPort)
                    Utils.setClipboard(context, JsonUtil.toJsonPretty(hy2Config) + "\n" + result.content)
                    return 0
                }
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share full content to clipboard", e)
            return -1
        }
        return 0
    }

    /**
     * Shares the configuration.
     *
     * @param guid The GUID of the configuration.
     * @return The configuration string.
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.CUSTOM -> ""
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.HTTP -> ""
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
                EConfigType.HYSTERIA2 -> Hysteria2Fmt.toUri(config)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to share config for GUID: $guid", e)
            return ""
        }
    }

    /**
     * Imports a batch of configurations.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return A pair containing the number of configurations and subscriptions imported.
     */
    fun importBatchConfig(server: String?, subid: String, append: Boolean): Pair<Int, Int> {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }

        var countSub = parseBatchSubscription(server)
        if (countSub <= 0) {
            countSub = parseBatchSubscription(Utils.decode(server))
        }
        if (countSub > 0) {
            updateConfigViaSubAll()
        }

        return count to countSub
    }

    /**
     * Parses a batch of subscriptions.
     *
     * @param servers The servers string.
     * @return The number of subscriptions parsed.
     */
    private fun parseBatchSubscription(servers: String?): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
                .distinct()
                .forEach { str ->
                    if (Utils.isValidSubUrl(str)) {
                        count += importUrlAsSubscription(str)
                    }
                }
            return count
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse batch subscription", e)
        }
        return 0
    }

    /**
     * Parses a batch of configurations.
     *
     * @param servers The servers string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }
            val removedSelectedServer =
                if (!TextUtils.isEmpty(subid) && !append) {
                    MmkvManager.decodeServerConfig(
                        MmkvManager.getSelectServer().orEmpty()
                    )?.let {
                        if (it.subscriptionId == subid) {
                            return@let it
                        }
                        return@let null
                    }
                } else {
                    null
                }
            if (!append) {
                MmkvManager.removeServerViaSubid(subid)
            }

            val subItem = MmkvManager.decodeSubscription(subid)
            var count = 0
            servers.lines()
                .distinct()
                .reversed()
                .forEach {
                    val resId = parseConfig(it, subid, subItem, removedSelectedServer)
                    if (resId == 0) {
                        count++
                    }
                }
            return count
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse batch config", e)
        }
        return 0
    }

    /**
     * Parses a custom configuration server.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @return The number of configurations parsed.
     */
    private fun parseCustomConfigServer(server: String?, subid: String): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val serverList: Array<Any> =
                    JsonUtil.fromJson(server, Array<Any>::class.java)

                if (serverList.isNotEmpty()) {
                    var count = 0
                    for (srv in serverList.reversed()) {
                        val config = CustomFmt.parse(JsonUtil.toJson(srv)) ?: continue
                        config.subscriptionId = subid
                        val key = MmkvManager.encodeServerConfig("", config)
                        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(srv) ?: "")
                        count += 1
                    }
                    return count
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse custom config server JSON array", e)
            }

            try {
                // For compatibility
                val config = CustomFmt.parse(server) ?: return 0
                config.subscriptionId = subid
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse custom config server as single config", e)
            }
            return 0
        } else if (server.startsWith("[Interface]") && server.contains("[Peer]")) {
            try {
                val config = WireguardFmt.parseWireguardConfFile(server) ?: return R.string.toast_incorrect_protocol
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.encodeServerRaw(key, server)
                return 1
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to parse WireGuard config file", e)
            }
            return 0
        } else {
            return 0
        }
    }

    /**
     * Parses the configuration from a QR code or string.
     *
     * @param str The configuration string.
     * @param subid The subscription ID.
     * @param subItem The subscription item.
     * @param removedSelectedServer The removed selected server.
     * @return The result code.
     */
    private fun parseConfig(
        str: String?,
        subid: String,
        subItem: SubscriptionItem?,
        removedSelectedServer: ProfileItem?
    ): Int {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return R.string.toast_none_data
            }

            val config = if (str.startsWith(EConfigType.VMESS.protocolScheme)) {
                VmessFmt.parse(str)
            } else if (str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme)) {
                ShadowsocksFmt.parse(str)
            } else if (str.startsWith(EConfigType.SOCKS.protocolScheme)) {
                SocksFmt.parse(str)
            } else if (str.startsWith(EConfigType.TROJAN.protocolScheme)) {
                TrojanFmt.parse(str)
            } else if (str.startsWith(EConfigType.VLESS.protocolScheme)) {
                VlessFmt.parse(str)
            } else if (str.startsWith(EConfigType.WIREGUARD.protocolScheme)) {
                WireguardFmt.parse(str)
            } else if (str.startsWith(EConfigType.HYSTERIA2.protocolScheme) || str.startsWith(HY2)) {
                Hysteria2Fmt.parse(str)
            } else {
                null
            }

            if (config == null) {
                return R.string.toast_incorrect_protocol
            }
            //filter
            if (subItem?.filter != null && subItem.filter?.isNotEmpty() == true && config.remarks.isNotEmpty()) {
                val matched = Regex(pattern = subItem.filter ?: "")
                    .containsMatchIn(input = config.remarks)
                if (!matched) return -1
            }

            config.subscriptionId = subid
            val guid = MmkvManager.encodeServerConfig("", config)
            if (removedSelectedServer != null &&
                config.server == removedSelectedServer.server && config.serverPort == removedSelectedServer.serverPort
            ) {
                MmkvManager.setSelectServer(guid)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to parse config", e)
            return -1
        }
        return 0
    }

    /**
     * Updates the configuration via all subscriptions.
     *
     * @return The number of configurations updated.
     */
    fun updateConfigViaSubAll(): Int {
        var count = 0
        try {
            MmkvManager.decodeSubscriptions().forEach {
                count += updateConfigViaSub(it)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update config via all subscriptions", e)
            return 0
        }
        return count
    }

    /**
     * Updates the configuration via a subscription.
     *
     * @param it The subscription item.
     * @return The number of configurations updated.
     */
    fun updateConfigViaSub(it: Pair<String, SubscriptionItem>): Int {
        try {
            if (TextUtils.isEmpty(it.first)
                || TextUtils.isEmpty(it.second.remarks)
                || TextUtils.isEmpty(it.second.url)
            ) {
                return 0
            }
            if (!it.second.enabled) {
                return 0
            }
            val url = HttpUtil.toIdnUrl(it.second.url)
            if (!Utils.isValidUrl(url)) {
                return 0
            }
            if (!it.second.allowInsecureUrl) {
                if (!Utils.isValidSubUrl(url)) {
                    return 0
                }
            }
            Log.i(AppConfig.TAG, url)

            var configText = try {
                val httpPort = SettingsManager.getHttpPort()
                HttpUtil.getUrlContentWithUserAgent(url, 15000, httpPort)
            } catch (e: Exception) {
                Log.e(AppConfig.ANG_PACKAGE, "Update subscription: proxy not ready or other error", e)
                ""
            }
            if (configText.isEmpty()) {
                configText = try {
                    HttpUtil.getUrlContentWithUserAgent(url)
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Update subscription: Failed to get URL content with user agent", e)
                    ""
                }
            }
            if (configText.isEmpty()) {
                return 0
            }
            return parseConfigViaSub(configText, it.first, false)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update config via subscription", e)
            return 0
        }
    }

    /**
     * Parses the configuration via a subscription.
     *
     * @param server The server string.
     * @param subid The subscription ID.
     * @param append Whether to append the configurations.
     * @return The number of configurations parsed.
     */
    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }
        return count
    }

    /**
     * Imports a URL as a subscription.
     *
     * @param url The URL.
     * @return The number of subscriptions imported.
     */
    private fun importUrlAsSubscription(url: String): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.second.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }

    /**
     * Creates an intelligent selection configuration based on multiple server configurations.
     *
     * @param context The application context used for configuration generation.
     * @param guidList The list of server GUIDs to be included in the intelligent selection.
     *                 Each GUID represents a server configuration that will be combined.
     * @param subid The subscription ID to associate with the generated configuration.
     *              This helps organize the configuration under a specific subscription.
     * @return The GUID key of the newly created intelligent selection configuration,
     *         or null if the operation fails (e.g., empty guidList or configuration parsing error).
     */
    fun createIntelligentSelection(
        context: Context,
        guidList: List<String>,
        subid: String
    ): String? {
        if (guidList.isEmpty()) {
            return null
        }
        // When there are many servers, perform successive halving to pick the top candidates.
        val targetK = 128
        val selectedGuids = if (guidList.size > targetK) {
            successiveHalvingSelect(context, guidList, targetK)
        } else {
            guidList
        }
        val result = V2rayConfigManager.genV2rayConfig(context, selectedGuids) ?: return null
        val config = CustomFmt.parse(JsonUtil.toJson(result)) ?: return null
        config.subscriptionId = subid
        val key = MmkvManager.encodeServerConfig("", config)
        MmkvManager.encodeServerRaw(key, JsonUtil.toJsonPretty(result) ?: "")
        return key
    }
}
