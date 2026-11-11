package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Repairs Cloudflare-fronted configs that behave badly on a restrictive network.
 *
 * Three things can actually be fixed from inside the app:
 *  - **Blocking**: the first bytes of a TLS ClientHello are a fingerprint; fragmenting them (the
 *    `fragment` feature the core already has) gets the connection through on ISPs that drop the
 *    handshake, which is what "the config stopped working" usually means.
 *  - **Bad edge**: anycast means every Cloudflare IP is not equal. Measuring the handshake against a
 *    sample of edge addresses and pointing the config at the fastest one is the single biggest
 *    throughput win for these configs.
 *  - **High latency**: multiplexing reuses one tunnel instead of a TCP+TLS setup per request.
 *
 * What it cannot fix, and does not pretend to: the quota of the Cloudflare plan behind a worker
 * (free plans cap daily requests) and the exit ISP's own upload limits.
 */
object CloudflareAssistant {

    /** Edge addresses that answer on 443 for every zone; a sample finds the fast ones. */
    private val EDGE_IPS = listOf(
        "104.16.0.1", "104.16.32.1", "104.16.64.1", "104.16.96.1", "104.16.128.1", "104.16.200.1",
        "104.17.0.1", "104.17.64.1", "104.17.128.1", "104.17.192.1",
        "104.18.0.1", "104.18.32.1", "104.18.96.1", "104.18.160.1",
        "104.19.0.1", "104.19.64.1", "104.19.128.1",
        "104.20.0.1", "104.20.64.1", "104.20.128.1",
        "104.21.0.1", "104.21.64.1", "104.21.128.1",
        "104.22.0.1", "104.22.32.1", "104.22.96.1",
        "104.24.0.1", "104.24.64.1",
        "104.25.0.1", "104.25.128.1",
        "104.26.0.1", "104.26.64.1",
        "104.27.0.1", "104.27.128.1",
        "162.159.0.1", "162.159.64.1", "162.159.128.1", "162.159.200.1",
        "172.64.0.1", "172.64.64.1", "172.64.128.1", "172.64.200.1",
        "172.66.0.1", "172.66.128.1", "172.66.200.1",
        "172.67.0.1", "172.67.64.1", "172.67.128.1", "172.67.200.1",
        "188.114.96.1", "188.114.97.1", "188.114.98.1", "188.114.99.1",
        "190.93.240.1", "190.93.241.1",
        "197.234.240.1", "198.41.128.1"
    )

    data class ScanResult(val address: String, val latencyMs: Long)

    /**
     * What the automatic pass did, so the screen and the log can say it without guessing.
     */
    data class RepairReport(
        val isCloudflare: Boolean = false,
        /** Real round trip of the config that came out of the repair, measured through the core. */
        val latencyMs: Long? = null,
        val unblocked: Boolean = false,
        val switchedAddress: String? = null,
        val newConfigGuid: String? = null,
        val newConfigName: String? = null,
    )

    fun isAutoEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_CLOUDFLARE_AUTO, true)

    fun setAutoEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_CLOUDFLARE_AUTO, enabled)
    }

    // -------------------------------------------------------------------------
    // Anti-block bundle
    // -------------------------------------------------------------------------

    fun isAntiBlockEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false)

    /**
     * Turns the "make this config work again" bundle on or off: TLS fragmentation plus multiplexing.
     * Values are the ones that survive the Iranian ISPs in practice (split the ClientHello, then keep
     * one tunnel busy instead of reconnecting for every request).
     */
    fun setAntiBlock(enabled: Boolean) {
        if (enabled) {
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, true)
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, "100-200")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_MAXSPLIT, "10")
            MmkvManager.encodeSettings(AppConfig.PREF_MUX_ENABLED, true)
            MmkvManager.encodeSettings(AppConfig.PREF_MUX_CONCURRENCY, "8")
            MmkvManager.encodeSettings(
                AppConfig.PREF_MUX_XUDP_CONCURRENCY,
                AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY
            )
        } else {
            MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, false)
            MmkvManager.encodeSettings(AppConfig.PREF_MUX_ENABLED, false)
        }
        SettingsChangeManager.makeRestartService()
    }

    // -------------------------------------------------------------------------
    // Current config
    // -------------------------------------------------------------------------

    fun selectedConfig(): ProfileItem? =
        MmkvManager.getSelectServer()?.let { guid -> MmkvManager.decodeServerConfig(guid) }

    fun selectedGuid(): String? = MmkvManager.getSelectServer()

    fun isCloudflareConfig(config: ProfileItem?): Boolean =
        addressLooksCloudflare(config?.server) ||
                addressLooksCloudflare(config?.sni) ||
                addressLooksCloudflare(config?.host)

    private fun addressLooksCloudflare(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val value = address.trim().lowercase()
        if (value.endsWith(".workers.dev") || value.endsWith(".pages.dev")) return true
        return isCloudflareIpv4(value)
    }

    // -------------------------------------------------------------------------
    // Scanning
    // -------------------------------------------------------------------------

    /** Measures every candidate edge and returns the reachable ones, fastest first. */
    suspend fun scan(
        sni: String?,
        timeoutMs: Int = 2_000,
        concurrency: Int = 10
    ): List<ScanResult> = coroutineScope {
        val gate = Semaphore(concurrency)
        EDGE_IPS.map { ip ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val latency = measure(ip, sni, timeoutMs)
                    if (latency == null) null else ScanResult(ip, latency)
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.latencyMs }
    }

    /** Latency of one edge address, or null when it is unreachable within [timeoutMs]. */
    suspend fun measure(address: String, sni: String? = null, timeoutMs: Int = 2_000): Long? =
        withContext(Dispatchers.IO) {
            try {
                val started = System.nanoTime()
                Socket().use { raw ->
                    raw.connect(InetSocketAddress(address, PORT), timeoutMs)
                    raw.soTimeout = timeoutMs
                    if (!sni.isNullOrBlank()) {
                        handshake(raw, sni, timeoutMs)
                    }
                }
                (System.nanoTime() - started) / 1_000_000
            } catch (e: Exception) {
                null
            }
        }

    private fun handshake(raw: Socket, sni: String, timeoutMs: Int) {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        val tls = context.socketFactory.createSocket(raw, sni, PORT, true) as SSLSocket
        tls.soTimeout = timeoutMs
        tls.sslParameters = tls.sslParameters.apply { serverNames = listOf(SNIHostName(sni)) }
        tls.startHandshake()
        tls.close()
    }

    // -------------------------------------------------------------------------
    // Applying the result
    // -------------------------------------------------------------------------

    /**
     * The local group the tools write to. Created on first use and remembered, so the tab keeps its
     * identity even after the app restarts; nothing here talks to a server.
     */
    fun ensureGroup(groupName: String): String {
        MmkvManager.decodeSettingsString(AppConfig.PREF_CLOUDFLARE_GROUP)
            ?.takeIf { it.isNotBlank() && MmkvManager.decodeSubscription(it) != null }
            ?.let { return it }

        // An older install may already have a group with this name: reuse it instead of a duplicate.
        MmkvManager.decodeSubscriptions()
            .firstOrNull { it.subscription.url.isBlank() && it.subscription.remarks == groupName }
            ?.let { existing ->
                MmkvManager.encodeSettings(AppConfig.PREF_CLOUDFLARE_GROUP, existing.guid)
                return existing.guid
            }

        val guid = Utils.getUuid()
        MmkvManager.encodeSubscription(
            guid,
            SubscriptionItem(remarks = groupName, url = "", enabled = true),
        )
        MmkvManager.encodeSettings(AppConfig.PREF_CLOUDFLARE_GROUP, guid)
        LogUtil.i(AppConfig.TAG, "Cloudflare: created the local group $groupName ($guid)")
        return guid
    }

    /**
     * How many configs the Cloudflare group currently holds. Reads only: asking for the count must
     * not create an empty tab on a phone that never used these tools.
     */
    fun groupSize(groupName: String): Int {
        val guid = MmkvManager.decodeSettingsString(AppConfig.PREF_CLOUDFLARE_GROUP)
            ?.takeIf { it.isNotBlank() && MmkvManager.decodeSubscription(it) != null }
            ?: MmkvManager.decodeSubscriptions()
                .firstOrNull { it.subscription.url.isBlank() && it.subscription.remarks == groupName }
                ?.guid
            ?: return 0
        return MmkvManager.decodeServerList(guid).size
    }

    /**
     * Creates (or refreshes) a copy of [sourceGuid] that points at [address] while keeping the
     * Cloudflare hostname for SNI and for the WebSocket Host header — the edge routes by name, so
     * both have to stay.
     *
     * The source profile is never modified: the result is a new entry inside the Cloudflare group.
     * A second call for the same source updates the copy made the first time, so daily repairs do
     * not pile up near-identical profiles.
     *
     * @return the GUID of the stored copy, or null when the source has no usable host.
     */
    fun saveCloudflareCopy(
        sourceGuid: String,
        address: String,
        groupId: String,
        remarksSuffix: String,
    ): String? {
        val source = MmkvManager.decodeServerConfig(sourceGuid) ?: return null
        val originalAddress = source.server?.trim().orEmpty()
        if (originalAddress.isEmpty()) return null

        val domain = when {
            !isIpv4Literal(originalAddress) -> originalAddress
            !source.sni.isNullOrBlank() -> source.sni
            !source.host.isNullOrBlank() -> source.host
            else -> return null
        }

        // The profile may already be one of ours (the auto pass selects what it builds), so it is
        // refreshed in place instead of becoming a copy of a copy. Anything else gets a copy, and a
        // second repair of the same source refreshes that copy rather than making another entry.
        val isOwnCopy = source.subscriptionId == groupId
        val previous = if (isOwnCopy) null else {
            MmkvManager.decodeSettingsString(AppConfig.PREF_CLOUDFLARE_COPY_PREFIX + sourceGuid)
                ?.takeIf { it.isNotBlank() && MmkvManager.decodeServerConfig(it) != null }
        }
        val targetGuid = if (isOwnCopy) sourceGuid else previous.orEmpty()

        val baseRemarks = source.remarks.trim()
        val remarks = when {
            isOwnCopy -> baseRemarks
            baseRemarks.endsWith(remarksSuffix) -> baseRemarks
            else -> "$baseRemarks $remarksSuffix".trim()
        }

        val copy = source.copy(
            server = address,
            sni = source.sni?.takeIf { it.isNotBlank() } ?: domain,
            host = source.host?.takeIf { it.isNotBlank() } ?: domain,
            remarks = remarks,
            subscriptionId = groupId,
        )

        return try {
            val saved = MmkvManager.encodeServerConfig(targetGuid, copy)
            if (!isOwnCopy) {
                MmkvManager.encodeSettings(AppConfig.PREF_CLOUDFLARE_COPY_PREFIX + sourceGuid, saved)
            }
            SettingsChangeManager.makeRestartService()
            SettingsChangeManager.makeSetupGroupTab()
            LogUtil.i(AppConfig.TAG, "Cloudflare: ${copy.remarks} -> $address (sni=$domain)")
            saved
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Cloudflare: could not store the Cloudflare copy", e)
            null
        }
    }

    /**
     * Measures [guid] through the core exactly like the app's own ping test, and remembers the
     * number so the config list shows it too. This is the real round trip of that config; the
     * numbers on the edge list are only handshake latency and are not presented as a config ping.
     */
    suspend fun measureRealPing(context: Context, guid: String): Long? {
        val latency = try {
            ConfigBenchmark.pingConfigs(context, listOf(guid)).values.firstOrNull()
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Cloudflare: real ping failed for $guid", e)
            null
        }
        if (latency != null && latency > 0L) {
            MmkvManager.encodeServerTestDelayMillis(guid, latency)
            return latency
        }
        return null
    }

    /**
     * The whole fix in one call: copy the selected config into the Cloudflare group pointing at
     * [address], make that copy the selected profile and measure its real latency.
     */
    suspend fun buildCloudflareConfig(
        context: Context,
        sourceGuid: String,
        address: String,
        groupName: String,
        remarksSuffix: String,
    ): RepairReport? {
        // Validate before anything is created, so a profile without a usable host never leaves an
        // empty Cloudflare tab behind.
        val source = MmkvManager.decodeServerConfig(sourceGuid) ?: return null
        val sourceAddress = source.server?.trim().orEmpty()
        if (sourceAddress.isEmpty()) return null
        if (isIpv4Literal(sourceAddress) && source.sni.isNullOrBlank() && source.host.isNullOrBlank()) {
            return null
        }

        val groupId = ensureGroup(groupName)
        val guid = saveCloudflareCopy(sourceGuid, address, groupId, remarksSuffix) ?: return null
        MmkvManager.setSelectServer(guid)
        val latency = measureRealPing(context, guid)
        return RepairReport(
            isCloudflare = true,
            latencyMs = latency,
            switchedAddress = address,
            newConfigGuid = guid,
            newConfigName = MmkvManager.decodeServerConfig(guid)?.remarks,
        )
    }

    // -------------------------------------------------------------------------
    // Automatic repair
    // -------------------------------------------------------------------------

    /**
     * The hands-off path used when the Cloudflare screen opens and once a day from the maintenance
     * job: measure the selected config, and only touch anything when it is actually broken.
     *
     * Rules that keep it from being disruptive: a healthy config is left alone, the anti-block bundle
     * is only switched on when it is off, and a copy is only produced when the current edge does not
     * answer at all or crawls. The produced copy carries a real, measured latency.
     */
    suspend fun autoRepair(
        context: Context,
        groupName: String,
        remarksSuffix: String,
    ): RepairReport {
        val guid = selectedGuid() ?: return RepairReport()
        val config = selectedConfig() ?: return RepairReport()
        if (!isCloudflareConfig(config)) return RepairReport()

        val host = config.server?.trim().orEmpty()
        if (host.isEmpty()) return RepairReport(isCloudflare = true)

        val sni = config.sni?.takeIf { it.isNotBlank() } ?: config.host
        val before = measure(host, sni)
        val healthy = before != null && before <= HEALTHY_LATENCY_MS
        if (healthy && isAntiBlockEnabled()) {
            return RepairReport(isCloudflare = true, latencyMs = before)
        }

        var unblocked = false
        if (!isAntiBlockEnabled()) {
            setAntiBlock(true)
            unblocked = true
        }

        var switched: String? = null
        if (before == null || before > SLOW_LATENCY_MS) {
            val best = scan(sni).firstOrNull()
            val worthIt = best != null &&
                    (before == null || best.latencyMs + IP_SWITCH_MARGIN_MS < before)
            if (worthIt) switched = best?.address
        }

        // A repair never rewrites the user's own profile. It produces a Cloudflare copy with a real,
        // measured latency, and only when the edge changed or the old one did not answer at all.
        if (switched != null || before == null) {
            val copied = buildCloudflareConfig(
                context = context,
                sourceGuid = guid,
                address = switched ?: host,
                groupName = groupName,
                remarksSuffix = remarksSuffix,
            )
            if (copied != null) {
                val report = copied.copy(unblocked = unblocked, latencyMs = copied.latencyMs ?: before)
                LogUtil.i(
                    AppConfig.TAG,
                    "Cloudflare auto repair: copy=${report.newConfigName} ping=${report.latencyMs}"
                )
                return report
            }
        }

        LogUtil.i(
            AppConfig.TAG,
            "Cloudflare auto repair: latency=$before unblocked=$unblocked copy=none"
        )
        return RepairReport(isCloudflare = true, latencyMs = before, unblocked = unblocked)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private const val PORT = 443

    /** Up to this, a config counts as working and is left untouched. */
    private const val HEALTHY_LATENCY_MS = 1_200L

    /** Beyond this, another edge is worth trying. */
    private const val SLOW_LATENCY_MS = 1_500L

    /** A new edge has to be clearly better, otherwise the config keeps flapping. */
    private const val IP_SWITCH_MARGIN_MS = 150L

    private fun isIpv4Literal(value: String): Boolean = IPV4_REGEX.matches(value)

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    private fun octets(ip: String): IntArray? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val values = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i].toIntOrNull() ?: return null
            if (part !in 0..255) return null
            values[i] = part
        }
        return values
    }

    /**
     * True for the address blocks Cloudflare announces. Configs that point at a domain still resolve
     * into one of these, so the check also answers "is this really Cloudflare?" before the app offers
     * to touch the config.
     */
    fun isCloudflareIpv4(address: String?): Boolean {
        val ip = address?.trim() ?: return false
        val octets = octets(ip) ?: return false
        return CLOUDFLARE_RANGES.any { block ->
            octets[0] == block.first &&
                    octets[1] in block.secondFrom..block.secondTo &&
                    octets[2] in block.thirdFrom..block.thirdTo
        }
    }

    /** A Cloudflare block: fixed first octet, plus the second/third octet windows it covers. */
    private data class Block(
        val first: Int,
        val secondFrom: Int,
        val secondTo: Int,
        val thirdFrom: Int,
        val thirdTo: Int,
    )

    /** The blocks Cloudflare announces; the fourth octet is always free. */
    private val CLOUDFLARE_RANGES = listOf(
        Block(103, 21, 21, 244, 247),
        Block(103, 22, 22, 200, 203),
        Block(103, 31, 31, 4, 7),
        Block(104, 16, 27, 0, 255),
        Block(108, 162, 162, 192, 255),
        Block(131, 0, 0, 72, 75),
        Block(141, 101, 101, 64, 127),
        Block(162, 158, 159, 0, 255),
        Block(172, 64, 71, 0, 255),
        Block(173, 245, 245, 48, 63),
        Block(188, 114, 114, 96, 111),
        Block(190, 93, 93, 240, 255),
        Block(197, 234, 234, 240, 243),
        Block(198, 41, 41, 128, 255)
    )
}
