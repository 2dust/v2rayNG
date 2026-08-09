package com.v2ray.ang.root

import android.content.Context
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.PackageUidResolver
import java.io.File

/**
 * Kernel TPROXY engine for the root (system-wide) run mode — the alternative to
 * [RootProxyManager]'s tun2socks engine.
 *
 * No tun device and no userspace TCP/IP stack. A mangle OUTPUT chain marks the device's own
 * packets, an `ip rule` sends the mark to a private table whose default is `local ... dev lo`,
 * which loops those packets back through PREROUTING where the `TPROXY` target hands them to an
 * `IP_TRANSPARENT` socket held by the bundled `hev-socks5-tproxy` helper. The helper relays the
 * accepted connection to the in-process core's SOCKS inbound on loopback.
 *
 * The helper exists only because `IP_TRANSPARENT` needs CAP_NET_ADMIN, which the app-uid
 * in-process core cannot have. The kernel — not lwip — owns the TCP state, so this drops the
 * per-packet tun read/write syscalls and the userspace retransmit timers that the tun2socks
 * engine pays for, and it creates no interface for apps to find via `NetworkInterface`.
 *
 * Differs from tun2socks in one safety-relevant way: this engine is inherently **fail-closed**.
 * The routing table's `local default dev lo` entry does not disappear when the helper dies (`lo`
 * is always up), so packets keep being delivered locally and the `TPROXY` target simply finds no
 * socket and drops them. Proxied apps lose connectivity rather than leaking in the clear.
 *
 * All rules live in dedicated chains plus the shared dedicated routing table, so [teardown] is a
 * clean, bounded flush. Teardown runs before every setup and on every stop path.
 */
object RootTproxyManager {

    private const val OUT_CHAIN = AppConfig.ROOT_TP_OUT_CHAIN
    private const val PRE_CHAIN = AppConfig.ROOT_TP_PRE_CHAIN
    private const val TABLE = AppConfig.ROOT_ROUTE_TABLE
    private const val PRIORITY = AppConfig.ROOT_RULE_PRIORITY
    private const val FWMARK = AppConfig.ROOT_FWMARK
    private const val MARK = AppConfig.ROOT_MARK_ROUTE
    private const val PORT = AppConfig.ROOT_TPROXY_PORT

    fun start(context: Context): Boolean {
        teardown(context)
        val script = buildSetup(context) ?: return false
        val result = RootShell.runScript(context, "setup_tproxy.sh", script)
        if (!result.success) {
            LogUtil.e(AppConfig.TAG, "RootTproxyManager: setup failed, rolling back:\n${result.output}")
            teardown(context)
            return false
        }
        return true
    }

    /** Remove all rules and stop the helper. Safe to call repeatedly. */
    fun stop(context: Context) {
        teardown(context)
        LogUtil.i(AppConfig.TAG, "RootTproxyManager: rules removed")
    }

    private fun teardown(context: Context) {
        RootShell.runScript(context, "teardown_tproxy.sh", buildTeardown(context))
    }

    // ------------------------------------------------------------------- setup

    private fun buildSetup(context: Context): String? {
        val bin = File(context.applicationInfo.nativeLibraryDir, AppConfig.ROOT_TPROXY_BIN)
        if (!bin.exists()) {
            LogUtil.e(AppConfig.TAG, "RootTproxyManager: hev-socks5-tproxy binary missing at ${bin.absolutePath}")
            return null
        }
        val appUid = context.applicationInfo.uid
        val port = SettingsManager.getSocksPort()
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val pidFile = File(runDir, "tproxy.pid").absolutePath
        val logFile = File(runDir, "tproxy.log").absolutePath
        val cfgFile = File(runDir, "tproxy.yml").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val lanShare = MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)
        val corePid = Process.myPid()
        // /proc/net/tcp* renders the listening port as uppercase hex.
        val hexPort = "%04X".format(PORT)

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
            // The TPROXY target and the transparent-socket lookup live in separate kernel
            // modules. Without them every rule below is a no-op and traffic would egress in
            // the clear, so refuse to continue rather than silently run unprotected.
            appendLine("iptables -t mangle -j TPROXY --help >/dev/null 2>&1 || { echo 'kernel has no TPROXY target'; exit 1; }")

            // Protect the core from the low-memory killer (system_server keeps recomputing
            // oom_score_adj for app processes, so re-pin it from a root loop).
            appendLine("nohup sh -c 'while true; do echo ${AppConfig.ROOT_OOM_SCORE} > /proc/$corePid/oom_score_adj 2>/dev/null; sleep 5; done' >/dev/null 2>&1 &")
            appendLine("echo \$! > '$oomGuardPid'")

            // Locally-looped packets arrive on lo carrying a foreign destination; reverse-path
            // filtering would drop them. route_localnet lets the loopback route accept them.
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter 2>/dev/null || true")
            appendLine("echo 0 > /proc/sys/net/ipv4/conf/lo/rp_filter 2>/dev/null || true")
            appendLine("echo 1 > /proc/sys/net/ipv4/conf/all/route_localnet 2>/dev/null || true")
            appendLine("echo 1 > /proc/sys/net/ipv4/conf/lo/route_localnet 2>/dev/null || true")

            // hev-socks5-tproxy holds the IP_TRANSPARENT listener and forwards to the core's
            // SOCKS inbound on loopback. `mark` is applied to its UPSTREAM socket only (see
            // hev_socks5_session_tcp_bind) — the accepted transparent socket is NOT marked,
            // which is why the OUTPUT chain needs the anti-spoof RETURNs below.
            appendLine("cat > '$cfgFile' <<'HEVCFG'")
            append(buildHevConfig(port))
            appendLine("HEVCFG")
            appendLine("nohup \"\$BIN\" '$cfgFile' >'$logFile' 2>&1 &")
            appendLine("TP_PID=\$!")
            appendLine("echo \$TP_PID > '$pidFile'")
            appendLine("echo ${AppConfig.ROOT_OOM_SCORE} > /proc/\$TP_PID/oom_score_adj 2>/dev/null || true")
            // Wait for the transparent listener before installing any rule that points at it.
            appendLine("i=0; while [ \$i -lt 20 ]; do")
            appendLine("  grep -qi ':$hexPort ' /proc/net/tcp6 /proc/net/tcp 2>/dev/null && break")
            appendLine("  kill -0 \$TP_PID 2>/dev/null || { echo 'hev-socks5-tproxy exited'; cat '$logFile' 2>/dev/null; exit 1; }")
            appendLine("  sleep 0.3; i=\$((i+1))")
            appendLine("done")
            appendLine("grep -qi ':$hexPort ' /proc/net/tcp6 /proc/net/tcp 2>/dev/null || { echo 'transparent listener did not come up'; cat '$logFile' 2>/dev/null; exit 1; }")

            // Policy routing: marked packets hit a private table whose default delivers
            // locally via lo, which loops them back through PREROUTING for the TPROXY target.
            appendLine("ip route replace local default dev lo table $TABLE")
            appendLine("ip rule add fwmark $MARK table $TABLE priority $PRIORITY")

            append(buildOutputMarking("iptables", appUid, perAppEnabled, bypassApps, selectedUids))
            append(buildTproxyChain("iptables", lanShare, RootProxyManager.bypassCidrs))

            appendLine("set +e")
            if (ipv6) {
                appendLine("ip -6 route replace local default dev lo table $TABLE 2>/dev/null || true")
                appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                append(buildOutputMarking("ip6tables", appUid, perAppEnabled, bypassApps, selectedUids))
                append(buildTproxyChain("ip6tables", lanShare, RootProxyManager.bypassCidrsV6))
            } else {
                // v6 disabled: blackhole native v6 egress so v6-capable apps fall back to
                // v4-through-proxy instead of reaching destinations natively. Reuses the
                // tun2socks engine's chain — it is transport-independent.
                append(RootProxyManager.buildV6Blackhole(appUid, perAppEnabled, bypassApps, selectedUids))
            }
            if (lanShare) {
                // Forwarded clients are consumed by the PREROUTING TPROXY rule before they
                // ever reach FORWARD, so unlike the tun2socks engine this needs no FORWARD
                // accepts, no DNS DNAT and no MSS clamp — only IP forwarding itself.
                appendLine("echo 1 > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true")
                if (ipv6) appendLine("echo 1 > /proc/sys/net/ipv6/conf/all/forwarding 2>/dev/null || true")
            }
        }
    }

    /**
     * hev-socks5-tproxy YAML. One port serves both the TCP and UDP transparent listeners
     * (different protocols, no conflict). Bound to `::` so a dual-stack socket catches both
     * families. No `dns:` section: DNS is hijacked by marking port 53 in [buildOutputMarking],
     * so it arrives as ordinary UDP traffic and the core's own DNS routing handles it.
     */
    private fun buildHevConfig(socksPort: Int): String {
        val user = SettingsManager.getSocksUsername()
        val pass = SettingsManager.getSocksPassword()
        return buildString {
            appendLine("main:")
            appendLine("  workers: 1")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: '${AppConfig.LOOPBACK}'")
            appendLine("  udp: 'udp'")
            appendLine("  mark: $FWMARK")
            if (user != null && pass != null) {
                appendLine("  username: '$user'")
                appendLine("  password: '$pass'")
            }
            appendLine("tcp:")
            appendLine("  port: $PORT")
            appendLine("  address: '::'")
            appendLine("udp:")
            appendLine("  port: $PORT")
            appendLine("  address: '::'")
        }
    }

    /**
     * mangle OUTPUT chain. Same per-app semantics as the tun2socks engine's marking chain, plus
     * two RETURNs that engine does not need.
     *
     * The extra RETURNs stop a routing loop unique to TPROXY: hev's accepted transparent socket
     * is bound to the connection's ORIGINAL DESTINATION, so its reply packets leave through
     * OUTPUT with a spoofed, non-local source. Marking those would loop them straight back into
     * the TPROXY target. Genuine locally-generated traffic always has a local source and is in
     * the conntrack ORIGINAL direction, so both matches exempt exactly the spoofed replies.
     * They are tried independently because `xt_addrtype` is missing on some Android kernels and
     * `xt_conntrack --ctdir` on others; the chain aborts if neither is available, because
     * without one of them this engine would melt the routing table.
     */
    private fun buildOutputMarking(
        cmd: String,
        appUid: Int,
        perAppEnabled: Boolean,
        bypassApps: Boolean,
        selectedUids: List<String>,
    ): String {
        val allowMode = perAppEnabled && !bypassApps
        val bypassSelected = perAppEnabled && bypassApps && selectedUids.isNotEmpty()
        val cidrs = if (cmd == "ip6tables") RootProxyManager.bypassCidrsV6 else RootProxyManager.bypassCidrs
        return buildString {
            appendLine("$cmd -t mangle -N $OUT_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $OUT_CHAIN")
            // anti-loop: hev's spoofed transparent replies must never be marked
            appendLine("ANTISPOOF=0")
            appendLine("$cmd -t mangle -A $OUT_CHAIN -m conntrack --ctdir REPLY -j RETURN 2>/dev/null && ANTISPOOF=1 || true")
            appendLine("$cmd -t mangle -A $OUT_CHAIN -m addrtype ! --src-type LOCAL -j RETURN 2>/dev/null && ANTISPOOF=1 || true")
            appendLine("[ \"\$ANTISPOOF\" = 1 ] || { echo 'no conntrack/addrtype match available for TPROXY anti-loop'; exit 1; }")
            // hev's upstream socket carries this mark; its destination is loopback anyway
            appendLine("$cmd -t mangle -A $OUT_CHAIN -m mark --mark $FWMARK -j RETURN")
            // the app's own core traffic (the real outbound) must not be captured
            appendLine("$cmd -t mangle -A $OUT_CHAIN -m owner --uid-owner $appUid -j RETURN")
            if (bypassSelected) {
                selectedUids.forEach { appendLine("$cmd -t mangle -A $OUT_CHAIN -m owner --uid-owner $it -j RETURN") }
            }
            // DNS for ALL modes, uid-agnostic: on Android the query is sent by netd under a
            // shared uid, so owner-match cannot attribute it to the app. Must precede the LAN
            // RETURNs, or a query aimed at a router/LAN resolver would go direct and be
            // answered by the local ISP resolver (leak + CDN mis-resolution).
            appendLine("$cmd -t mangle -A $OUT_CHAIN -p udp --dport 53 -j MARK --set-xmark $MARK")
            appendLine("$cmd -t mangle -A $OUT_CHAIN -p tcp --dport 53 -j MARK --set-xmark $MARK")
            cidrs.forEach { appendLine("$cmd -t mangle -A $OUT_CHAIN -d $it -j RETURN") }
            if (allowMode) {
                // Proxy ONLY the selected apps. If nothing resolved, mark nothing rather than
                // falling through to the catch-all — a fail-open here would tunnel every app.
                selectedUids.forEach { appendLine("$cmd -t mangle -A $OUT_CHAIN -m owner --uid-owner $it -j MARK --set-xmark $MARK") }
            } else {
                // all-apps or bypass mode: capture every remaining uid (incl uid 0 + system)
                appendLine("$cmd -t mangle -A $OUT_CHAIN -j MARK --set-xmark $MARK")
            }
            appendLine("$cmd -t mangle -D OUTPUT -j $OUT_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A OUTPUT -j $OUT_CHAIN")
        }
    }

    /**
     * mangle PREROUTING chain: the actual TPROXY hand-off.
     *
     * Two populations arrive here. The device's own traffic has already been filtered and marked
     * by [buildOutputMarking] and loops in via the `local ... dev lo` route, so matching the mark
     * is enough — all per-app and bypass decisions were made in OUTPUT. Tethered clients arrive
     * natively and unmarked, and are recognised by a non-local source address; they get the
     * bypass CIDRs applied here instead.
     */
    private fun buildTproxyChain(cmd: String, lanShare: Boolean, cidrs: List<String>): String {
        val tproxy = "-j TPROXY --on-port $PORT --tproxy-mark $MARK"
        return buildString {
            appendLine("$cmd -t mangle -N $PRE_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $PRE_CHAIN")
            // the device's own looped traffic
            appendLine("$cmd -t mangle -A $PRE_CHAIN -m mark --mark $MARK -p tcp $tproxy")
            appendLine("$cmd -t mangle -A $PRE_CHAIN -m mark --mark $MARK -p udp $tproxy")
            if (lanShare) {
                // hotspot / USB-tethered clients: keep their LAN-local traffic direct, tunnel
                // the rest. DNS first, so a query to the router's resolver is still hijacked.
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -m addrtype ! --src-type LOCAL -p udp --dport 53 $tproxy 2>/dev/null || true")
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -m addrtype ! --src-type LOCAL -p tcp --dport 53 $tproxy 2>/dev/null || true")
                cidrs.forEach { appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -d $it -j RETURN") }
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -m addrtype ! --src-type LOCAL -p tcp $tproxy 2>/dev/null || true")
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -m addrtype ! --src-type LOCAL -p udp $tproxy 2>/dev/null || true")
            }
            appendLine("$cmd -t mangle -D PREROUTING -j $PRE_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A PREROUTING -j $PRE_CHAIN")
        }
    }

    // ---------------------------------------------------------------- teardown

    private fun buildTeardown(context: Context): String {
        val runDir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR)
        val pidFile = File(runDir, "tproxy.pid").absolutePath
        val oomGuardPid = File(runDir, "oomguard.pid").absolutePath
        val corePid = Process.myPid()
        return buildString {
            for (cmd in listOf("iptables", "ip6tables")) {
                appendLine("$cmd -t mangle -D OUTPUT -j $OUT_CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $OUT_CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $OUT_CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -D PREROUTING -j $PRE_CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $PRE_CHAIN 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $PRE_CHAIN 2>/dev/null || true")
            }
            // IPv6 blackhole chain (shared with the tun2socks engine; harmless if absent)
            appendLine("ip6tables -t filter -D OUTPUT -j ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -F ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -t filter -X ${AppConfig.ROOT_V6_CHAIN} 2>/dev/null || true")
            // routing rule + table
            appendLine("ip rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip -6 rule del fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
            appendLine("ip route flush table $TABLE 2>/dev/null || true")
            appendLine("ip -6 route flush table $TABLE 2>/dev/null || true")
            // helper process
            appendLine("[ -f '$pidFile' ] && kill \$(cat '$pidFile') 2>/dev/null || true")
            appendLine("rm -f '$pidFile'")
            // stop the OOM re-pin loop and restore the core process's LMK priority
            appendLine("[ -f '$oomGuardPid' ] && kill \$(cat '$oomGuardPid') 2>/dev/null || true")
            appendLine("rm -f '$oomGuardPid'")
            appendLine("echo 0 > /proc/$corePid/oom_score_adj 2>/dev/null || true")
        }
    }
}
