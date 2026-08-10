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

    /**
     * Which kernel matches are actually available. Android kernels differ in what netfilter
     * modules they ship, so this is probed on-device rather than assumed — a missing module
     * makes the corresponding rule a silent no-op, which for this engine means traffic
     * egressing in the clear or a routing loop.
     */
    private data class Caps(
        val tproxy4: Boolean,
        val tproxy6: Boolean,
        val ctdir4: Boolean,
        val ctdir6: Boolean,
        val addrType4: Boolean,
        val addrType6: Boolean,
    ) {
        fun tproxy(v6: Boolean) = if (v6) tproxy6 else tproxy4
        fun ctdir(v6: Boolean) = if (v6) ctdir6 else ctdir4
        fun addrType(v6: Boolean) = if (v6) addrType6 else addrType4

        /**
         * OUTPUT guard against hev's spoofed replies, which carry a non-local SOURCE.
         * Source-keyed matches are safe here because OUTPUT never sees inbound traffic.
         */
        fun antiSpoof(v6: Boolean): String? = when {
            ctdir(v6) -> "-m conntrack --ctdir REPLY"
            addrType(v6) -> "-m addrtype ! --src-type LOCAL"
            else -> null
        }

        /**
         * PREROUTING guard for traffic coming back to this device. hev's reply to a proxied
         * connection is addressed to the device's own IP, so the kernel delivers it locally —
         * which sends it out through `lo` and straight back into the `-i lo` TPROXY rules,
         * where it would be handed to hev again and never reach the app that was waiting.
         * A public device address makes this certain, since the private-range bypasses miss it.
         *
         * Keyed on connection direction (the reply of an ORIGINAL we already proxied) or, failing
         * that, on the destination being one of ours. NOT source-keyed: a reply's source is the
         * remote peer, which is exactly what the OUTPUT guard above looks for.
         */
        fun replyGuard(v6: Boolean): String? = when {
            ctdir(v6) -> "-m conntrack --ctdir REPLY"
            addrType(v6) -> "-m addrtype --dst-type LOCAL"
            else -> null
        }
    }

    fun start(context: Context): Boolean {
        teardown(context)
        val caps = probe(context)
        if (caps == null || !caps.tproxy4 || caps.antiSpoof(false) == null) {
            LogUtil.w(
                AppConfig.TAG,
                "RootTproxyManager: kernel lacks TPROXY support (tproxy=${caps?.tproxy4}, " +
                    "anti-spoof match=${caps?.antiSpoof(false)}), cannot use this engine"
            )
            return false
        }
        val script = buildSetup(context, caps) ?: return false
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

    // ------------------------------------------------------------------- probe

    /**
     * Test each required match by actually installing it into a throwaway chain — that both
     * loads the module and proves the running kernel accepts the exact rule this engine emits.
     * A `--help`-style check would pass on the userspace extension alone, even with no kernel
     * module behind it.
     *
     * `conntrack --ctdir REPLY` is preferred over `addrtype ! --src-type LOCAL` because it
     * identifies hev's spoofed replies by connection direction rather than by address, so it
     * still holds when a proxied destination happens to be a local address.
     */
    private fun probe(context: Context): Caps? {
        val chain = "CORE_TP_PROBE"
        val script = buildString {
            for (cmd in listOf("iptables", "ip6tables")) {
                val fam = if (cmd == "ip6tables") "6" else "4"
                appendLine("$cmd -t mangle -N $chain 2>/dev/null || true")
                appendLine("$cmd -t mangle -F $chain 2>/dev/null || true")
                appendLine("$cmd -t mangle -A $chain -p tcp -j TPROXY --on-port $PORT --tproxy-mark $MARK 2>/dev/null && echo TPROXY$fam=1 || echo TPROXY$fam=0")
                appendLine("$cmd -t mangle -F $chain 2>/dev/null || true")
                appendLine("$cmd -t mangle -A $chain -m conntrack --ctdir REPLY -j RETURN 2>/dev/null && echo CTDIR$fam=1 || echo CTDIR$fam=0")
                appendLine("$cmd -t mangle -F $chain 2>/dev/null || true")
                appendLine("$cmd -t mangle -A $chain -m addrtype ! --dst-type LOCAL -j RETURN 2>/dev/null && echo ADDRTYPE$fam=1 || echo ADDRTYPE$fam=0")
                appendLine("$cmd -t mangle -F $chain 2>/dev/null || true")
                appendLine("$cmd -t mangle -X $chain 2>/dev/null || true")
            }
        }
        val result = RootShell.runScript(context, "probe_tproxy.sh", script)
        if (!result.success) {
            LogUtil.e(AppConfig.TAG, "RootTproxyManager: capability probe failed:\n${result.output}")
            return null
        }
        val out = result.output
        fun has(key: String) = out.contains("$key=1")
        return Caps(
            tproxy4 = has("TPROXY4"),
            tproxy6 = has("TPROXY6"),
            ctdir4 = has("CTDIR4"),
            ctdir6 = has("CTDIR6"),
            addrType4 = has("ADDRTYPE4"),
            addrType6 = has("ADDRTYPE6"),
        )
    }


    // ------------------------------------------------------------------- setup

    private fun buildSetup(context: Context, caps: Caps): String? {
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
        // IPv6 through the tunnel needs its own TPROXY target and anti-spoof match. When the
        // kernel is missing either, fall back to blackholing v6 rather than aborting the
        // working IPv4 setup — v6 stays unproxied either way, and blackholing keeps it from
        // leaking natively.
        val ipv6Wanted = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED)
        val ipv6 = ipv6Wanted && caps.tproxy6 && caps.antiSpoof(true) != null && caps.replyGuard(true) != null
        if (ipv6Wanted && !ipv6) {
            LogUtil.w(AppConfig.TAG, "RootTproxyManager: kernel cannot TPROXY IPv6, blackholing it instead")
        }
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
            // which is why the OUTPUT chain needs the anti-spoof RETURN below.
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

            append(buildOutputMarking("iptables", caps.antiSpoof(false)!!, appUid, perAppEnabled, bypassApps, selectedUids))
            append(buildTproxyChain("iptables", lanShare, RootProxyManager.bypassCidrs, caps.replyGuard(false)!!, caps.addrType4))

            appendLine("set +e")
            if (ipv6) {
                appendLine("ip -6 route replace local default dev lo table $TABLE 2>/dev/null || true")
                appendLine("ip -6 rule add fwmark $MARK table $TABLE priority $PRIORITY 2>/dev/null || true")
                append(buildOutputMarking("ip6tables", caps.antiSpoof(true)!!, appUid, perAppEnabled, bypassApps, selectedUids))
                append(buildTproxyChain("ip6tables", lanShare, RootProxyManager.bypassCidrsV6, caps.replyGuard(true)!!, caps.addrType6))
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
                append(buildLanShareV6Guard())
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
            // No `mark:` on purpose. Android's netd encodes the netId in the low bits of a
            // socket's fwmark, so an arbitrary SO_MARK can steer the socket into a network
            // that does not exist. hev's only upstream socket targets loopback, which the
            // 127.0.0.0/8 RETURN already exempts, so the mark buys nothing. The tun2socks
            // engine omits it for the same reason.
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
     * one RETURN that engine does not need.
     *
     * The extra RETURN stops a routing loop unique to TPROXY: hev's accepted transparent socket
     * is bound to the connection's ORIGINAL DESTINATION, so its reply packets leave through
     * OUTPUT with a spoofed, non-local source. Marking those would loop them straight back into
     * the TPROXY target. Genuine locally-generated traffic has a local source and is in the
     * conntrack ORIGINAL direction, so [antiSpoof] — whichever match the kernel supports, as
     * decided by [probe] — exempts exactly the spoofed replies.
     */
    private fun buildOutputMarking(
        cmd: String,
        antiSpoof: String,
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
            appendLine("$cmd -t mangle -A $OUT_CHAIN $antiSpoof -j RETURN")
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
     * Two populations arrive here. The device's own traffic was already filtered and marked by
     * [buildOutputMarking] and loops back in through `lo`, so every per-app and bypass decision
     * was made in OUTPUT and anything reaching here on `lo` is meant to be proxied. Tethered
     * clients arrive unmarked on a real interface and get the bypass CIDRs applied here instead.
     *
     * Forwarded traffic is defined by exclusion rather than by guessing tethering interface names,
     * which vary per ROM: once everything addressed to this device has been returned, whatever is
     * left arriving on a real interface is by definition passing *through* us. `xt_addrtype`
     * expresses that directly; without it the same set is materialized by enumerating the device's
     * own addresses when the rules are installed.
     *
     * Deliberately does NOT key on the fwmark. Whether the mark set in OUTPUT survives the trip
     * through the `local ... dev lo` route is an assumption this engine does not need to make,
     * and if it fails to hold the chain matches nothing and all proxied traffic stops.
     *
     * The loopback RETURN must stay first: genuine loopback traffic arrives on `lo` too, and
     * TPROXYing the core's own SOCKS connections would loop the proxy into itself.
     */
    private fun buildTproxyChain(
        cmd: String,
        lanShare: Boolean,
        cidrs: List<String>,
        replyGuard: String,
        hasAddrType: Boolean,
    ): String {
        val tproxy = "-j TPROXY --on-port $PORT --tproxy-mark $MARK"
        val v6 = cmd == "ip6tables"
        return buildString {
            val loopback = if (v6) "::1/128" else "127.0.0.0/8"
            appendLine("$cmd -t mangle -N $PRE_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -F $PRE_CHAIN")
            // MUST be first. Genuine loopback traffic also arrives on lo — including the
            // core's own connections to its SOCKS inbound — and TPROXYing that would loop
            // the proxy into itself.
            appendLine("$cmd -t mangle -A $PRE_CHAIN -d $loopback -j RETURN")
            // Second, and just as load-bearing: let the proxied connections' replies through.
            // hev answers on the connection's original destination but addresses the reply to
            // this device, so the kernel delivers it locally via lo, right back into the rules
            // below. Without this the reply is handed to hev again instead of to the waiting
            // app, and the connection dies retransmitting.
            appendLine("$cmd -t mangle -A $PRE_CHAIN $replyGuard -j RETURN")
            // Everything addressed to this device goes to this device. This is what separates a
            // tethered client's forwarded packet from an ordinary inbound one, and it also covers
            // a proxied app that happened to dial one of our own addresses.
            if (hasAddrType) {
                appendLine("$cmd -t mangle -A $PRE_CHAIN -m addrtype --dst-type LOCAL -j RETURN")
            } else {
                // No xt_addrtype: materialize the same set from the addresses the device holds
                // when the rules go in. Evaluated by the shell at install time, not when the
                // script was generated, so it reflects the interfaces that are actually up.
                val ipCmd = if (v6) "ip -6" else "ip -4"
                appendLine("for A in \$($ipCmd -o addr show 2>/dev/null | awk '{print \$4}' | cut -d/ -f1); do")
                appendLine("  $cmd -t mangle -A $PRE_CHAIN -d \"\$A\" -j RETURN 2>/dev/null || true")
                appendLine("done")
                if (!v6) appendLine("$cmd -t mangle -A $PRE_CHAIN -d 255.255.255.255 -j RETURN")
            }
            // DNS before the LAN bypass below, so a query aimed at a router/LAN resolver is
            // still hijacked instead of being answered by the local network's resolver.
            appendLine("$cmd -t mangle -A $PRE_CHAIN -i lo -p udp --dport 53 $tproxy")
            appendLine("$cmd -t mangle -A $PRE_CHAIN -i lo -p tcp --dport 53 $tproxy")
            if (lanShare) {
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -p udp --dport 53 $tproxy")
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -p tcp --dport 53 $tproxy")
            }
            cidrs.forEach { appendLine("$cmd -t mangle -A $PRE_CHAIN -d $it -j RETURN") }
            // The device's own traffic, looped back in by the `local ... dev lo` route. Keyed
            // on the arrival interface rather than on the fwmark set in OUTPUT: relying on the
            // mark surviving the loop is an assumption this engine does not need to make, and
            // if it does not hold every rule here silently stops matching.
            appendLine("$cmd -t mangle -A $PRE_CHAIN -i lo -p tcp $tproxy")
            appendLine("$cmd -t mangle -A $PRE_CHAIN -i lo -p udp $tproxy")
            if (lanShare) {
                // Whatever is still here arrived on a real interface and is not addressed to us:
                // a hotspot / USB-tethered client's traffic passing through.
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -p tcp $tproxy")
                appendLine("$cmd -t mangle -A $PRE_CHAIN ! -i lo -p udp $tproxy")
            }
            appendLine("$cmd -t mangle -D PREROUTING -j $PRE_CHAIN 2>/dev/null || true")
            appendLine("$cmd -t mangle -A PREROUTING -j $PRE_CHAIN")
        }
    }

    /**
     * ip6tables FORWARD guard for LAN sharing — the one piece of tethering the TPROXY chains
     * cannot express on their own.
     *
     * Hotspot / USB clients get a native, RA-assigned **global** IPv6 out of the upstream's
     * delegated prefix, and netd turns on v6 forwarding for them. That traffic is forwarded, so
     * it never enters mangle OUTPUT and is therefore untouched by [RootProxyManager.buildV6Blackhole],
     * which only hooks OUTPUT and only covers this device's own packets. Without this chain a
     * tethered client reaches every v6-capable destination directly while its v4 goes through the
     * proxy — the client looks unproxied to any site that offers AAAA records.
     *
     * Correct in both v6 modes, which is why it is installed unconditionally:
     *  - v6 not tunneled: nothing else touches forwarded v6, so this is the only thing stopping
     *    the leak. Rejecting makes v6-capable clients fall back to v4-through-the-proxy, exactly
     *    as they do behind a v4-only VpnService.
     *  - v6 tunneled: the `! -i lo` TPROXY rules in [buildTproxyChain] already consumed the
     *    client's traffic and delivered it locally, so it never reached FORWARD. Anything still
     *    arriving here escaped the TPROXY hand-off and would leak, so rejecting it is fail-closed.
     *
     * Link-local, ULA and multicast are RETURNed first so NDP/RA and client-to-client LAN traffic
     * keep working; only routable global v6 is rejected. `icmp6-no-route` is an instant failure,
     * so happy-eyeballs falls back to v4 without waiting out a timeout.
     */
    private fun buildLanShareV6Guard(): String {
        val chain = AppConfig.ROOT_V6_FWD_CHAIN
        return buildString {
            appendLine("ip6tables -N $chain 2>/dev/null || true")
            appendLine("ip6tables -F $chain 2>/dev/null || true")
            RootProxyManager.bypassCidrsV6.forEach {
                appendLine("ip6tables -A $chain -d $it -j RETURN 2>/dev/null || true")
            }
            appendLine("ip6tables -A $chain -j REJECT --reject-with icmp6-no-route 2>/dev/null || true")
            appendLine("ip6tables -D FORWARD -j $chain 2>/dev/null || true")
            appendLine("ip6tables -I FORWARD -j $chain 2>/dev/null || true")
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
            // LAN-sharing IPv6 FORWARD guard. Must go, and go on every stop: leaving it behind
            // would keep rejecting tethered clients' IPv6 long after the proxy is gone.
            appendLine("ip6tables -D FORWARD -j ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -F ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
            appendLine("ip6tables -X ${AppConfig.ROOT_V6_FWD_CHAIN} 2>/dev/null || true")
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
