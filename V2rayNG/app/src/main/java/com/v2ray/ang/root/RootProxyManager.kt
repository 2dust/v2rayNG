package com.v2ray.ang.root

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.root.RootProxyManager.TABLE
import com.v2ray.ang.root.RootProxyManager.TUN
import com.v2ray.ang.root.RootProxyManager.teardown
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.PackageUidResolver
import com.v2ray.ang.util.Utils
import java.io.File

/**
 * Installs and removes the iptables / ip-rule routing that pushes system-wide traffic
 *
 * A bundled `hev-socks5-tunnel` binary (run as root) creates a tun device and forwards it to
 * the in-process core's SOCKS inbound; a mangle MARK chain plus a dedicated routing table /
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

    // IPv6 equivalents (loopback, link-local, ULA/private, multicast). Feeding the v4 list
    // above to ip6tables silently fails, so the v6 chain needs its own.
    private val bypassCidrsV6 = listOf(
        "::1/128", "fe80::/10", "fc00::/7", "ff00::/8"
    )

    fun start(context: Context): Boolean {
        teardown(context)
        val script = buildTun2socksSetup(context) ?: return false
        val result = RootShell.runScript(context, "setup_rules.sh", script)
        if (!result.success) {
            LogUtil.e(AppConfig.TAG, "RootProxyManager: setup failed, rolling back:\n${result.output}")
            teardown(context)
            return false
        }
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
            LogUtil.e(AppConfig.TAG, "RootProxyManager: hev-socks5-tunnel binary missing at ${bin.absolutePath}")
            return null
        }
        val appUid = context.applicationInfo.uid
        val port = SettingsManager.getSocksPort()
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val logFile = File(runDir, "tun2socks.log").absolutePath
        val cfgFile = File(runDir, "tun2socks.yml").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val lanShare = forceLanShare || MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)
        val corePid = Process.myPid()

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
            // hev-socks5-tunnel config: it creates the tun ($TUN) itself and forwards it to the
            // in-process core's SOCKS inbound on loopback. MTU comes from the existing VPN MTU
            // setting. No fwmark on hev's sockets: its only upstream connection is to 127.0.0.1
            // (loopback, already RETURNed by the 127.0.0.0/8 bypass) and the core's real outbound
            // runs as the app uid (RETURNed by the uid-owner rule), so traffic can't loop.
            appendLine("cat > '$cfgFile' <<'HEVCFG'")
            append(buildHevConfig(port, ipv6))
            appendLine("HEVCFG")
            appendLine("nohup \"\$BIN\" '$cfgFile' >'$logFile' 2>&1 &")
            appendLine("T2S_PID=\$!")
            appendLine("echo \$T2S_PID > '$pidFile'")
            appendLine("echo ${AppConfig.ROOT_OOM_SCORE} > /proc/\$T2S_PID/oom_score_adj 2>/dev/null || true")
            // wait for the interface hev creates to appear
            appendLine("i=0; while [ \$i -lt 20 ]; do ip link show $TUN >/dev/null 2>&1 && break; sleep 0.3; i=\$((i+1)); done")
            appendLine("ip link show $TUN >/dev/null 2>&1 || { echo 'tun device did not come up'; cat '$logFile' 2>/dev/null; exit 1; }")
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
                append(buildLanShareSetup(captureDeviceTraffic, ipv6))
            }
            if (captureDeviceTraffic) {
                // IPv6 is best-effort: never fail the (working) IPv4 setup over it.
                appendLine("set +e")
                if (ipv6) {
                    // route the device's v6 into the tun, same as v4
                    appendLine("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $TUN 2>/dev/null || true")
                    appendLine("ip -6 route replace default dev $TUN table $TABLE 2>/dev/null || true")
                    appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                    append(buildMangleMarking("ip6tables", appUid, perAppEnabled, bypassApps, selectedUids))
                } else {
                    // v6 disabled: blackhole native v6 egress for the captured apps so they
                    // fall back to v4-through-proxy, matching what a v4-only VpnService does.
                    append(buildV6Blackhole(appUid, perAppEnabled, bypassApps, selectedUids))
                }
            }
        }
    }

    /**
     * hev-socks5-tunnel YAML config. hev creates the tun device named [TUN] itself, assigns it
     * the tun addresses, and forwards everything it receives to the core's SOCKS inbound on
     * loopback (TCP + UDP). MTU is taken from the existing VPN MTU setting. v6 is only given a
     * tun address when IPv6 is enabled; whether v6 actually flows in is decided separately by
     * the v6 route into [TABLE].
     */
    private fun buildHevConfig(socksPort: Int, ipv6: Boolean): String {
        val v4 = AppConfig.ROOT_TUN_ADDR_V4.substringBefore("/")
        val v6 = AppConfig.ROOT_TUN_ADDR_V6.substringBefore("/")
        return buildString {
            appendLine("tunnel:")
            appendLine("  name: '$TUN'")
            appendLine("  mtu: ${SettingsManager.getVpnMtu()}")
            appendLine("  multi-queue: true")
            appendLine("  ipv4: '$v4'")
            if (ipv6) appendLine("  ipv6: '$v6'")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: '${AppConfig.LOOPBACK}'")
            appendLine("  udp: 'udp'")
            appendLine("  tcp-fastopen: true")
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
        val allowMode = perAppEnabled && !bypassApps
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        return buildString {
            appendLine("$cmd -t mangle -N $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $CHAIN")
            // the app's own core traffic (the real outbound) must not loop back into the tun.
            // The $FWMARK RETURN is kept defensively (hev itself only talks to loopback, which
            // the 127.0.0.0/8 bypass below already RETURNs).
            appendLine("$cmd -t mangle -A $CHAIN -m mark --mark $FWMARK -j RETURN")
            appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $appUid -j RETURN")
            // bypass mode: selected apps go fully direct (incl their DNS)
            if (bypassSelected) {
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j RETURN") }
            }
            // Route DNS through the core for ALL modes, with no uid filter. On Android the
            // DNS query is sent by netd (a shared system uid) on behalf of the app, not under
            // the app's own uid, so it can't be attributed to a selected uid via owner-match.
            // This MUST also run before the LAN-bypass RETURNs below, otherwise a query to a
            // LAN/router resolver (192.168.x / 10.x) would be returned direct and resolved by
            // the local ISP resolver (DNS leak + CDN mis-resolution, e.g. Instagram media).
            // The MARK survives a later RETURN, so the marked query still routes into the tun.
            appendLine("$cmd -t mangle -A $CHAIN -p udp --dport 53 -j MARK --set-xmark $MARK")
            appendLine("$cmd -t mangle -A $CHAIN -p tcp --dport 53 -j MARK --set-xmark $MARK")
            // keep LAN / private destinations direct (per-family CIDR list)
            val cidrs = if (cmd == "ip6tables") bypassCidrsV6 else bypassCidrs
            cidrs.forEach { appendLine("$cmd -t mangle -A $CHAIN -d $it -j RETURN") }
            if (allowMode) {
                // Proxy ONLY the explicitly selected apps. If nothing resolved (e.g. the
                // selected packages failed to resolve to uids at early boot), mark nothing
                // instead of falling through to the catch-all below: a fail-open here would
                // tunnel every unselected app — both a privacy leak and the "per-app proxies
                // everything after a reboot" bug.
                selectedUids.forEach { appendLine("$cmd -t mangle -A $CHAIN -m owner --uid-owner $it -j MARK --set-xmark $MARK") }
            } else {
                // all-apps mode (per-app off) or bypass mode: capture EVERY remaining uid
                // (incl uid 0 + system uids)
                appendLine("$cmd -t mangle -A $CHAIN -j MARK --set-xmark $MARK")
            }
            appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A OUTPUT -j $CHAIN")
        }
    }

    /**
     * Blackhole native IPv6 egress for the captured app population when IPv6 is NOT routed
     * into the tun. A v4-only VpnService has no v6 route, so the kernel rejects apps' v6 and
     * they fall back to IPv4; Root mode has to reproduce that explicitly, otherwise v6-capable
     * apps reach destinations natively, bypassing the proxy / leaking. REJECT (not DROP) gives
     * an instant failure so happy-eyeballs falls back to v4 without a timeout.
     *
     * Exemptions mirror the v4 chain: the tun2socks helper (fwmark), the app's own core (uid),
     * loopback, link-local / multicast (NDP/RA/MLD) and ULA/LAN destinations. Per-app selection
     * is honored: in bypass mode the bypassed apps keep native v6; in proxy mode only the
     * selected apps lose v6 (everything else stays fully direct).
     */
    private fun buildV6Blackhole(
        appUid: Int,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        selectedUids: List<String>,
    ): String {
        val chain = AppConfig.ROOT_V6_CHAIN
        val allowMode = perAppEnabled && !bypassApps
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        val reject = "-j REJECT --reject-with icmp6-adm-prohibited"
        return buildString {
            appendLine("ip6tables -t filter -N $chain 2>/dev/null || true")
            appendLine("ip6tables -t filter -F $chain")
            // never touch the helper, the core, loopback, NDP/link-local/multicast or LAN
            appendLine("ip6tables -t filter -A $chain -m mark --mark $FWMARK -j RETURN")
            appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $appUid -j RETURN")
            appendLine("ip6tables -t filter -A $chain -o lo -j RETURN")
            bypassCidrsV6.forEach { appendLine("ip6tables -t filter -A $chain -d $it -j RETURN") }
            // bypass mode: bypassed apps keep their native v6
            if (bypassSelected) {
                selectedUids.forEach { appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $it -j RETURN") }
            }
            if (allowMode) {
                // proxy mode: only the selected apps lose v6 (so they fall back to v4-via-proxy).
                // None resolved -> reject nothing, mirroring the v4 chain's fail-closed handling.
                selectedUids.forEach { appendLine("ip6tables -t filter -A $chain -m owner --uid-owner $it $reject") }
            } else {
                // all-apps / bypass: reject everyone left
                appendLine("ip6tables -t filter -A $chain $reject")
            }
            appendLine("ip6tables -t filter -D OUTPUT -j $chain 2>/dev/null || true")
            appendLine("ip6tables -t filter -A OUTPUT -j $chain")
        }
    }

    // -------------------------------------------------- LAN / tethering sharing

    /**
     * Route Wi-Fi-hotspot / USB-tethered clients through the tun as well (ipv4).
     * Best-effort: wrapped in `set +e` so a failure here never breaks the working proxy.
     * Mirrors Magic_V2Ray's hotspot rules (FORWARD accept, DNS DNAT, source-based policy
     * routing for private client ranges, MSS clamp).
     */
    private fun buildLanShareSetup(captureDeviceTraffic: Boolean, ipv6: Boolean): String {
        val fwd = AppConfig.ROOT_FWD_CHAIN
        val dnsChain = AppConfig.ROOT_DNS_CHAIN
        val v6fwd = AppConfig.ROOT_V6_FWD_CHAIN
        val v6pre = AppConfig.ROOT_V6_PRE_CHAIN
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

            // ---------------------------------------------------------- IPv6 clients
            // Tethered/hotspot clients get a native (RA-assigned) global IPv6. The IPv4 rules
            // above don't touch it, so it egresses the upstream interface directly, bypassing
            // the proxy = IPv6 leak. Handle it explicitly (mirrors vincentng295/Magic_V2Ray
            // cae4f7f): route it through the tun when v6 is enabled, reject it when it isn't.
            appendLine("ip6tables -N $v6fwd 2>/dev/null || true")
            appendLine("ip6tables -F $v6fwd")
            appendLine("ip6tables -D FORWARD -j $v6fwd 2>/dev/null || true")
            appendLine("ip6tables -I FORWARD -j $v6fwd")
            if (ipv6) {
                // When the device itself isn't capturing v6 (VPN-mode sharing) the tun table
                // has no v6 default and the tun has no v6 address — add them so marked client
                // v6 has somewhere to go. In Root mode the device-capture block already did.
                if (!captureDeviceTraffic) {
                    appendLine("ip -6 addr add ${AppConfig.ROOT_TUN_ADDR_V6} dev $TUN 2>/dev/null || true")
                    appendLine("ip -6 route replace default dev $TUN table $TABLE 2>/dev/null || true")
                    appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                }
                // allow forwarding to/from the tun
                appendLine("ip6tables -A $v6fwd -i $TUN -j ACCEPT")
                appendLine("ip6tables -A $v6fwd -o $TUN -j ACCEPT")
                // mark forwarded (non-locally-sourced) client v6 into the tun table. DNS first
                // so a query to a LAN/router resolver is still tunneled (MARK survives RETURN);
                // keep loopback, link-local (NDP/RA) and ULA/multicast direct.
                appendLine("ip6tables -t mangle -N $v6pre 2>/dev/null || true")
                appendLine("ip6tables -t mangle -F $v6pre")
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -p udp --dport 53 -j MARK --set-xmark $MARK")
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -p tcp --dport 53 -j MARK --set-xmark $MARK")
                bypassCidrsV6.forEach { appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -d $it -j RETURN") }
                appendLine("ip6tables -t mangle -A $v6pre ! -i $TUN -j MARK --set-xmark $MARK")
                appendLine("ip6tables -t mangle -D PREROUTING -j $v6pre 2>/dev/null || true")
                appendLine("ip6tables -t mangle -A PREROUTING -j $v6pre")
                // fail closed: any forwarded v6 that wasn't marked into the tun (e.g. the
                // addrtype match is unavailable, or marking failed) is rejected rather than
                // leaked straight out the upstream interface.
                appendLine("ip6tables -A $v6fwd -j REJECT --reject-with icmp6-no-route")
            } else {
                // v6 disabled: reject forwarded clients' native v6 so it can't leak past the
                // proxy (the device's own v6 is blackholed separately in OUTPUT).
                appendLine("ip6tables -A $v6fwd -j REJECT --reject-with icmp6-no-route")
            }
        }
    }

    // ---------------------------------------------------------------- teardown

    private fun buildTeardown(context: Context): String {
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        val pidFile = File(runDir, "tun2socks.pid").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val corePid = Process.myPid()
        return buildString {
            // mangle (TUN2SOCKS), both families
            for (cmd in listOf("iptables", "ip6tables")) {
                appendLine("$cmd -t mangle -D OUTPUT -j $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $CHAIN 2>/dev/null || true")
            }
            // IPv6 blackhole chain (only set up when v6 is disabled; harmless if absent)
            appendLine("ip6tables -t filter -D OUTPUT -j ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -F ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -X ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
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
            // IPv6 LAN-sharing chains (forward accept/reject + forwarded-client marking)
            appendLine("ip6tables -D FORWARD -j ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -F ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -X ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -D PREROUTING -j ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -F ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t mangle -X ${AppConfig.ROOT_V6_PRE_CHAIN} 2>/dev/null || true")
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
