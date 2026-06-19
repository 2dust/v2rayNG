package com.v2ray.ang.core.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.ERunMode
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.PackageUidResolver
import com.v2ray.ang.util.Utils
import java.io.File

/**
 * Installs and removes the iptables / ip-rule routing that pushes system-wide traffic
 * into the core for the root run mode ("Root mode", [ERunMode.TUN2SOCKS]).
 *
 * A bundled `tun2socks` binary (run as root) creates a tun device and forwards it to the
 * in-process core's SOCKS inbound; a mangle MARK chain plus a dedicated routing table /
 * ip rule steer all traffic into the tun. Full TCP + UDP.
 *
 * All rules live in dedicated chains ([AppConfig.ROOT_IPTABLES_CHAIN] in the mangle
 * table, [AppConfig.ROOT_FWD_CHAIN] for LAN sharing) plus a dedicated routing table, so
 * [teardown] is a clean, bounded flush. Teardown runs before every setup (to clear stale
 * rules) and on every stop path — leaving rules behind after the core dies would break
 * the device's connectivity.
 */
object RootProxyManager {

    private const val CHAIN = AppConfig.ROOT_IPTABLES_CHAIN
    private const val TUN = AppConfig.ROOT_TUN_NAME
    private const val TABLE = AppConfig.ROOT_ROUTE_TABLE
    private const val PRIORITY = AppConfig.ROOT_RULE_PRIORITY
    private const val FWMARK = AppConfig.ROOT_FWMARK
    private const val MARK = AppConfig.ROOT_MARK_ROUTE

    // Local / private / multicast destinations that must never be proxied.
    private val bypassCidrs = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "240.0.0.0/4"
    )

    fun start(context: Context, mode: ERunMode): Boolean {
        teardown(context)
        val script = when (mode) {
            ERunMode.TUN2SOCKS -> buildTun2socksSetup(context) ?: return false
            else -> {
                LogUtil.w(AppConfig.TAG, "RootProxyManager: mode $mode not supported")
                return false
            }
        }
        val result = RootShell.runScript(context, "setup_rules.sh", script)
        if (!result.success) {
            LogUtil.e(AppConfig.TAG, "RootProxyManager: setup failed, rolling back:\n${result.output}")
            teardown(context)
            return false
        }
        LogUtil.i(AppConfig.TAG, "RootProxyManager: $mode rules installed")
        return true
    }

    /**
     * Set up LAN/tethering sharing while the device itself uses another mode (e.g. VPN
     * mode). Runs a dedicated client tun2socks into the in-process core's SOCKS inbound
     * and forwards tethered clients into it, WITHOUT capturing the device's own traffic
     * (that keeps flowing through the VpnService). Requires root.
     */
    fun startClientSharing(context: Context): Boolean {
        teardown(context)
        val script = buildTun2socksSetup(context, captureDeviceTraffic = false, forceLanShare = true)
            ?: return false
        val result = RootShell.runScript(context, "setup_rules.sh", script)
        if (!result.success) {
            LogUtil.e(AppConfig.TAG, "RootProxyManager: client sharing setup failed:\n${result.output}")
            teardown(context)
            return false
        }
        LogUtil.i(AppConfig.TAG, "RootProxyManager: LAN client sharing installed")
        return true
    }

    /** Remove all rules and stop helper processes. Safe to call repeatedly. */
    fun stop(context: Context) {
        teardown(context)
        LogUtil.i(AppConfig.TAG, "RootProxyManager: rules removed")
    }

    private fun teardown(context: Context) {
        RootShell.runScript(context, "teardown_rules.sh", buildTeardown(context))
    }

    // --------------------------------------------------------------- TUN2SOCKS

    /**
     * @param captureDeviceTraffic when true (Root mode) the device's own OUTPUT traffic is
     *   marked into the tun. When false (VPN-mode LAN sharing) the device keeps using the
     *   VpnService and only forwarded clients are routed into this tun.
     * @param forceLanShare force the LAN/tethering forward rules on regardless of the pref
     *   (used by VPN-mode sharing, where the whole point is forwarding clients).
     */
    private fun buildTun2socksSetup(
        context: Context,
        captureDeviceTraffic: Boolean = true,
        forceLanShare: Boolean = false,
    ): String? {
        val bin = File(context.applicationInfo.nativeLibraryDir, AppConfig.ROOT_TUN2SOCKS_BIN)
        if (!bin.exists()) {
            LogUtil.e(AppConfig.TAG, "RootProxyManager: tun2socks binary missing at ${bin.absolutePath}")
            return null
        }
        val appUid = context.applicationInfo.uid
        val port = SettingsManager.getSocksPort()
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val logFile = File(runDir, "tun2socks.log").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val lanShare = forceLanShare || MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)
        val corePid = android.os.Process.myPid()

        // Per-app proxy/bypass (mirrors what VpnService does via allowed/disallowed apps).
        val perAppEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY)
        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        val selectedUids = if (perAppEnabled) {
            val pkgs = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)?.toList().orEmpty()
            if (pkgs.isNotEmpty()) PackageUidResolver.packageNamesToUids(context, pkgs) else emptyList()
        } else {
            emptyList()
        }

        return buildString {
            appendLine("set -e")
            appendLine("BIN='${bin.absolutePath}'")
            // Protect the core (this app process) from the Android low-memory killer.
            // system_server keeps recomputing oom_score_adj for app processes, so a single
            // write would be reverted — re-pin it from a small root loop instead.
            appendLine("nohup sh -c 'while true; do echo ${AppConfig.ROOT_OOM_SCORE} > /proc/$corePid/oom_score_adj 2>/dev/null; sleep 5; done' >/dev/null 2>&1 &")
            appendLine("echo \$! > '$oomGuardPid'")
            // tun device node
            appendLine("if [ ! -e /dev/net/tun ]; then mkdir -p /dev/net; mknod /dev/net/tun c 10 200; chmod 666 /dev/net/tun; fi")
            // start tun2socks (its own upstream sockets are fwmarked $FWMARK so they bypass the tun)
            appendLine("nohup \"\$BIN\" -device 'tun://$TUN' -proxy 'socks5://${AppConfig.LOOPBACK}:$port' -fwmark $FWMARK >'$logFile' 2>&1 &")
            appendLine("T2S_PID=\$!")
            appendLine("echo \$T2S_PID > '$pidFile'")
            appendLine("echo ${AppConfig.ROOT_OOM_SCORE} > /proc/\$T2S_PID/oom_score_adj 2>/dev/null || true")
            // wait for the interface to appear
            appendLine("i=0; while [ \$i -lt 20 ]; do ip link show $TUN >/dev/null 2>&1 && break; sleep 0.3; i=\$((i+1)); done")
            appendLine("ip link show $TUN >/dev/null 2>&1 || { echo 'tun device did not come up'; exit 1; }")
            // relax reverse-path filtering for the tun
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/$TUN/rp_filter 2>/dev/null || true")
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
            // address + default route in a dedicated table
            appendLine("ip addr add ${AppConfig.ROOT_TUN_ADDR_V4} dev $TUN 2>/dev/null || true")
            appendLine("ip link set dev $TUN up")
            appendLine("ip route replace default dev $TUN table $TABLE")
            appendLine("ip rule add fwmark $MARK table $TABLE priority $PRIORITY")
            // mark the device's own packets into the tun (Root mode only)
            if (captureDeviceTraffic) {
                append(buildMangleMarking("iptables", appUid, perAppEnabled, bypassApps, selectedUids))
            }
            // optionally route hotspot / USB-tethered clients through the tun too
            if (lanShare) {
                append(buildLanShareSetup())
            }
            if (captureDeviceTraffic && ipv6) {
                // IPv6 is best-effort: never fail the (working) IPv4 setup over it.
                appendLine("set +e")
                appendLine("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $TUN 2>/dev/null || true")
                appendLine("ip -6 route replace default dev $TUN table $TABLE 2>/dev/null || true")
                appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                append(buildMangleMarking("ip6tables", appUid, perAppEnabled, bypassApps, selectedUids))
            }
        }
    }

    /**
     * mangle OUTPUT marking chain (ipv4/ipv6). Mirrors VpnService's capture behavior:
     * - all-apps (no per-app): mark EVERY remaining uid (incl uid 0 + all system uids), so
     *   nothing is missed;
     * - bypass mode: the selected apps go fully direct, everything else is captured;
     * - proxy mode: only the selected apps are captured.
     */
    private fun buildMangleMarking(
        cmd: String,
        appUid: Int,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        selectedUids: List<String>,
    ): String {
        val proxyOnlySelected = perAppEnabled && !bypassApps && selectedUids.isNotEmpty()
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        return buildString {
            appendLine("$cmd -t mangle -N $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $CHAIN")
            // tun2socks' own upstream traffic and the app's own core traffic must not loop.
            appendLine("$cmd -t mangle -A $CHAIN -m mark --mark $FWMARK -j RETURN")
            appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $appUid -j RETURN")
            // bypass mode: selected apps go fully direct (incl their DNS)
            if (bypassSelected) {
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j RETURN") }
            }
            // Route DNS through the core for the proxied population (prevents DNS leaks and
            // CDN mis-resolution, e.g. Instagram media). Skipped in proxy-only mode, where
            // only the selected uids' own traffic is captured.
            if (!proxyOnlySelected) {
                appendLine("$cmd -t mangle -A $CHAIN -p udp --dport 53 -j MARK --set-xmark $MARK")
                appendLine("$cmd -t mangle -A $CHAIN -p tcp --dport 53 -j MARK --set-xmark $MARK")
            }
            // keep LAN / private destinations direct
            bypassCidrs.forEach { appendLine("$cmd -t mangle -A $CHAIN -d $it -j RETURN") }
            if (proxyOnlySelected) {
                // proxy only the selected apps
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j MARK --set-xmark $MARK") }
            } else {
                // all-apps / bypass: capture EVERY remaining uid (incl uid 0 + system uids)
                appendLine("$cmd -t mangle -A $CHAIN -j MARK --set-xmark $MARK")
            }
            appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A OUTPUT -j $CHAIN")
        }
    }

    // -------------------------------------------------- LAN / tethering sharing

    /**
     * Route Wi-Fi-hotspot / USB-tethered clients through the tun as well (ipv4).
     * Best-effort: wrapped in `set +e` so a failure here never breaks the working proxy.
     * Mirrors Magic_V2Ray's hotspot rules (FORWARD accept, DNS DNAT, source-based policy
     * routing for private client ranges, MSS clamp).
     */
    private fun buildLanShareSetup(): String {
        val fwd = AppConfig.ROOT_FWD_CHAIN
        val dnsChain = AppConfig.ROOT_DNS_CHAIN
        // Use the app's configured remote DNS (first plain IPv4) as the DNAT target for
        // tethered clients; fall back to the default when it's a DoH/DoT/IPv6 value that
        // can't be a DNAT target.
        val dns = SettingsManager.getRemoteDnsServers()
            .firstOrNull { Utils.isPureIpAddress(it) && !it.contains(":") }
            ?: AppConfig.ROOT_LAN_DNS
        val lanCidrs = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
        return buildString {
            appendLine("set +e")
            appendLine("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")
            // forward traffic to/from the tun
            appendLine("iptables -N $fwd 2>/dev/null || true")
            appendLine("iptables -F $fwd")
            appendLine("iptables -A $fwd -i $TUN -j ACCEPT")
            appendLine("iptables -A $fwd -o $TUN -j ACCEPT")
            appendLine("iptables -D FORWARD -j $fwd 2>/dev/null || true")
            appendLine("iptables -I FORWARD -j $fwd")
            // clamp MSS to avoid TLS fragmentation overhead through the tunnel
            appendLine("iptables -t mangle -D FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350 2>/dev/null || true")
            appendLine("iptables -t mangle -A FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350")
            // hijack tethered clients' DNS into a dedicated chain so it resolves through the
            // tunnel; the chain keeps teardown independent of the resolver IP
            appendLine("iptables -t nat -N $dnsChain 2>/dev/null || true")
            appendLine("iptables -t nat -F $dnsChain")
            lanCidrs.forEach {
                appendLine("iptables -t nat -A $dnsChain ! -i $TUN -d $it -p udp --dport 53 -j DNAT --to $dns")
            }
            appendLine("iptables -t nat -D PREROUTING -j $dnsChain 2>/dev/null || true")
            appendLine("iptables -t nat -A PREROUTING -j $dnsChain")
            // policy routing: return-path via main, LAN direct, the rest via the tun table
            appendLine("ip rule add iif lo goto 6000 pref 5000 2>/dev/null || true")
            appendLine("ip rule add iif $TUN lookup main suppress_prefixlength 0 pref 5010 2>/dev/null || true")
            appendLine("ip rule add iif $TUN goto 6000 pref 5020 2>/dev/null || true")
            appendLine("ip rule add to 10.0.0.0/8 lookup main pref 5025 2>/dev/null || true")
            appendLine("ip rule add to 172.16.0.0/12 lookup main pref 5026 2>/dev/null || true")
            appendLine("ip rule add to 192.168.0.0/16 lookup main pref 5027 2>/dev/null || true")
            appendLine("ip rule add from 10.0.0.0/8 lookup $TABLE pref 5030 2>/dev/null || true")
            appendLine("ip rule add from 172.16.0.0/12 lookup $TABLE pref 5040 2>/dev/null || true")
            appendLine("ip rule add from 192.168.0.0/16 lookup $TABLE pref 5050 2>/dev/null || true")
            appendLine("ip rule add nop pref 6000 2>/dev/null || true")
        }
    }

    // ---------------------------------------------------------------- teardown

    private fun buildTeardown(context: Context): String {
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val corePid = android.os.Process.myPid()
        return buildString {
            // mangle (TUN2SOCKS), both families
            for (cmd in listOf("iptables", "ip6tables")) {
                appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $CHAIN 2>/dev/null || true")
            }
            // routing rule + table
            appendLine("ip rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip -6 rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip route flush table $TABLE 2>/dev/null || true")
            appendLine("ip -6 route flush table $TABLE 2>/dev/null || true")
            // LAN / tethering sharing (always cleaned, harmless if it was never set up)
            appendLine("iptables -D FORWARD -j ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -F ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -X ${AppConfig.ROOT_FWD_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t mangle -D FORWARD -o $TUN -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss 1350 2>/dev/null || true")
            appendLine("iptables -t nat -D PREROUTING -j ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t nat -F ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            appendLine("iptables -t nat -X ${AppConfig.ROOT_DNS_CHAIN} 2>/dev/null || true")
            for (pref in listOf(5000, 5010, 5020, 5025, 5026, 5027, 5030, 5040, 5050, 6000)) {
                appendLine("ip rule del pref $pref 2>/dev/null || true")
            }
            // tun device down + helper process
            appendLine("ip link set dev $TUN down 2>/dev/null || true")
            appendLine("[ -f '$pidFile' ] && kill \$(cat '$pidFile') 2>/dev/null || true")
            appendLine("rm -f '$pidFile'")
            // stop the OOM re-pin loop and restore the core process's LMK priority
            appendLine("[ -f '$oomGuardPid' ] && kill \$(cat '$oomGuardPid') 2>/dev/null || true")
            appendLine("rm -f '$oomGuardPid'")
            appendLine("echo 0 > /proc/$corePid/oom_score_adj 2>/dev/null || true")
        }
    }
}
