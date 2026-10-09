package com.v2ray.ang.core

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.AetherEndpoint
import com.v2ray.ang.dto.AetherRange
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherIpVersion
import com.v2ray.ang.enums.AetherObfuscation
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherPsiphonCdnSet
import com.v2ray.ang.enums.AetherPsiphonMode
import com.v2ray.ang.enums.AetherScanMode
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.AetherTorBridges
import com.v2ray.ang.enums.AetherTorRelays
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.fmt.AetherFmt
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Owns the Aether core process of the daemon: one live session at a time, an exit callback the
 * service reacts to, output relayed into the app log, readiness probing, and cleanup of leftover
 * processes. `service/ProcessService` is a fire-and-forget wrapper with none of that lifecycle,
 * which is why this is a separate owner rather than an extension of it.
 */
object AetherCoreManager {

    private const val BINARY_NAME = "libaether.so"

    /** The Psiphon client the core runs for a profile with Psiphon, shipped beside the core as a library. */
    private const val PSIPHON_BINARY_NAME = "libpsiphon-tunnel-core.so"

    /** The option that names Psiphon's own listener. */
    internal const val PSIPHON_BIND = "--psiphon-bind"

    /** The option that names Tor's own listener. */
    internal const val TOR_BIND = "--tor-bind"

    /** The option that names the proxy the core dials out through. */
    internal const val UPSTREAM = "--upstream"

    /**
     * The pluggable transport Tor's bridges run through, shipped beside the core as a library. It is
     * lyrebird, which speaks every transport the core asks bridges for; the core is told so by name,
     * since it recognises the program by a file name a library cannot have.
     */
    private const val TRANSPORT_BINARY_NAME = "liblyrebird.so"
    private const val PROBE_TIMEOUT_MS = 1000
    private const val READY_POLL_MS = 500L
    private const val DEFAULT_LOG_LEVEL = "info"

    /**
     * Environment variable naming the app process that spawned a core process. Rust ignores
     * SIGPIPE and the core has no parent-death handling, so a core whose owner was killed keeps
     * running until something else kills it; [reapStale] recognises such orphans by this value.
     */
    internal const val OWNER_ENV = "PATTNG_AETHER_OWNER"

    /**
     * Environment variable set on the daemon's session core and on no other. A profile and a custom
     * configuration choose the port their core listens on, so the port does not tell the session
     * from a scan or a test core; this does, for the processes that have to leave the session's key
     * alone.
     */
    internal const val SESSION_ENV = "PATTNG_AETHER_SESSION"

    /**
     * Environment variable on the daemon's session core that tells its exit-node apart, see
     * [AetherExit.key]. The same tunnel can dial out through a plain exit-node or through a hop of a
     * proxy chain, so the arguments do not tell whether a latency test measures what the session runs.
     */
    internal const val EXIT_ENV = "PATTNG_AETHER_EXIT"

    /** Environment variable that tells the core where the Psiphon client is; it looks for it under other names otherwise. */
    internal const val PSIPHON_BIN_ENV = "AETHER_PSIPHON_BIN"

    /** Environment variable that names the pluggable transports and their program for the core, as protocol=path entries. */
    internal const val TOR_PT_ENV = "AETHER_TOR_PT"

    /**
     * Environment variable a Go program reads the system's root certificates from, as directories
     * separated by colons. The Psiphon client and the pluggable transport are Go programs built for
     * Linux, which look at Linux paths; Android keeps the roots in the Conscrypt module since
     * Android 14 and on the system image before that. Without this the Psiphon client cannot verify
     * any certificate, its server list download first of all. Remove when the programs are built for
     * Android itself, whose Go runtime knows these places.
     */
    internal const val CERT_DIR_ENV = "SSL_CERT_DIR"
    private val androidCertificateDirectories = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")

    /**
     * Environment variable naming a file of settings the core lays over its built-in Psiphon
     * configuration. Android has no resolver configuration a Linux-built program could read, so
     * Psiphon is given resolvers of its own for the names it looks up itself, alone or around the
     * tunnel; inside the tunnel the names go to the core's proxy and are resolved there.
     */
    internal const val PSIPHON_CONFIG_ENV = "AETHER_PSIPHON_CONFIG"

    /** The core's flag naming the file of server entries the Psiphon client starts with; see [PsiphonServerList]. */
    internal const val PSIPHON_SERVER_ENTRIES = "--psiphon-server-entries"

    /** The word a command names the app's own list by; the real file takes its place when the core starts. */
    internal const val SHIPPED_LIST = "shipped-list"

    /** Environment variable naming the directory the Psiphon client keeps its datastore in; see [psiphonStateDir]. */
    internal const val PSIPHON_DIR_ENV = "AETHER_PSIPHON_DIR"
    private const val PSIPHON_STATE_DIR = "psiphon"
    private const val PSIPHON_PROBE_DIR = "psiphon-probe"

    /** How long a session start waits for the cores of cancelled tests to be gone, and how often it looks. */
    private const val PROBE_EXIT_WAIT_MS = 3_000L
    private const val PROBE_POLL_MS = 100L

    /** How long a stop waits for a core, and then for the programs it started, to be gone before force is used. */
    private const val EXIT_WAIT_MS = 2_000L
    private const val EXIT_POLL_MS = 50L
    private const val SIGTERM = 15
    private const val PSIPHON_OVERLAY_FILE = "psiphon-overlay.json"
    internal const val PSIPHON_OVERLAY = """{"DNSResolverAlternateServers": ["1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4"]}"""

    /** The transports lyrebird speaks, as the core names them: the list the core itself assumes for a lyrebird it finds by name. */
    private val torTransports = listOf("obfs4", "snowflake", "webtunnel", "meek_lite", "obfs3", "scramblesuit")

    /** The SOCKS5 greeting, no authentication offered, and the version byte a server answers it with. */
    private val socksGreeting = byteArrayOf(5, 1, 0)
    private const val SOCKS_VERSION = 5

    /**
     * The core's word that Psiphon has a tunnel: "psiphon is ready". Psiphon's listener answers the
     * greeting as soon as it is bound and closes every connection until then, so the greeting alone
     * says nothing where the app dials Psiphon.
     */
    private val psiphonReady = Regex("""psiphon is ready""")

    /**
     * The core's word that it did not start the session for want of an ECH key: ECH is on, and it had no key it
     * could offer, so it stopped rather than send the server name in the clear.
     */
    private val noEchKey = Regex("""ECH is on but there is no ECH key to offer""")

    /** The levels at which the core writes its info lines, the ready word among them. */
    private val infoLevels = setOf("info", "debug", "trace")

    private val logLevels = setOf("ERROR", "WARN", "INFO", "DEBUG", "TRACE")
    private val procDir = File("/proc")

    private val lifecycle = Executors.newSingleThreadExecutor { task ->
        Thread(task, "aether-core").apply { isDaemon = true }
    }

    /**
     * Where the loopback port every Aether core listens on comes from: the setting, see
     * SettingsManager.getAetherListenPort, which the application points this at as each of its
     * processes starts. Until then, as in a JVM test, the default port.
     */
    @Volatile
    var listenPortSource: () -> Int = { AppConfig.PORT_AETHER_SOCKS.toInt() }

    /**
     * The loopback port every Aether core listens on, whatever its profile, and every Aether outbound
     * dials, unless a command written by hand names another. The app runs one core at a time on it:
     * a session's, or a latency test's while no session runs on Aether.
     */
    val socksPort: Int get() = listenPortSource()

    /**
     * The port of the secondary-socks inbound, which the core of a session dials out through: three
     * above [socksPort], past the two after it that Psiphon and Tor may take.
     */
    val secondarySocksPort: Int get() = socksPort + SECONDARY_SOCKS_OFFSET
    private const val SECONDARY_SOCKS_OFFSET = 3

    /** Ports below this one are the system's; an app cannot listen there. */
    private const val FIRST_LISTEN_PORT = 1024

    /** The highest Aether listen port: the three ports after it are the core's as well. */
    private const val LAST_LISTEN_PORT = 65535 - SECONDARY_SOCKS_OFFSET

    /**
     * The Aether listen port the setting [text] names: a port an app can listen on, with the three
     * ports after it ports as well; the default port for anything else.
     */
    internal fun listenPortOf(text: String?): Int =
        text?.trim()?.toIntOrNull()?.takeIf { it in FIRST_LISTEN_PORT..LAST_LISTEN_PORT } ?: AppConfig.PORT_AETHER_SOCKS.toInt()

    /** Why [text] cannot be the Aether listen port beside a local proxy on [localPorts]; null when it can. */
    internal fun listenPortProblem(text: String, localPorts: Set<Int>): ListenPortProblem? {
        val port = text.trim().toIntOrNull()?.takeIf { it in FIRST_LISTEN_PORT..LAST_LISTEN_PORT } ?: return ListenPortProblem.NOT_A_PORT
        return ListenPortProblem.LOCAL_PROXY.takeIf { (port..port + SECONDARY_SOCKS_OFFSET).any { it in localPorts } }
    }

    internal enum class ListenPortProblem {
        /** No port an app can listen on, with the three ports after it ports as well. */
        NOT_A_PORT,

        /** The port, or one of the three after it, which the core takes as well, is the local proxy's. */
        LOCAL_PROXY,
    }

    /**
     * The port a scan of [profile] binds: none, since nothing dials it, unless Tor around the tunnel
     * comes along, whose own listener follows the tunnel's and needs a real port, because the core
     * dials the address Tor was told to listen on. Opens a socket to find one.
     */
    fun scanPort(profile: ProfileItem): Int =
        if (AetherTor.fromString(profile.aetherTor) == AetherTor.REVERSE) Utils.findRandomFreePort() else 0

    /**
     * Whether a tunnel over MASQUE runs over HTTP/2: chosen so, or dialled through Tor or Psiphon around it, which carry
     * TCP alone, so that the core takes HTTP/2 whatever [transport] says.
     */
    fun masqueOverHttp2(protocol: AetherProtocol, transport: AetherTransport, tor: AetherTor, psiphon: AetherPsiphon): Boolean =
        protocol.overMasque && (transport == AetherTransport.HTTP2 || tor == AetherTor.REVERSE || psiphon == AetherPsiphon.REVERSE)

    /** True when a core on [arguments] reaches WARP through Tor or Psiphon, which then has to come up before anything else can. */
    fun reachesWarpThroughCarrier(arguments: List<String>): Boolean =
        torModeOf(arguments) == AetherTor.REVERSE || psiphonModeOf(arguments) == AetherPsiphon.REVERSE

    @Volatile
    private var session: Session? = null

    /** Whether the session core that ended on its own last stopped for want of an ECH key; see [stoppedMessage]. */
    @Volatile
    private var stoppedForEchKey = false

    val isRunning: Boolean get() = session != null

    fun isSupported(context: Context): Boolean = binary(context).canExecute()

    /** Whether this build ships the Psiphon client; without it a profile with Psiphon cannot connect. */
    fun isPsiphonSupported(context: Context): Boolean = psiphonBinary(context).canExecute()

    /** Whether this build ships the pluggable transport; without it Tor has no bridges where it is blocked. */
    fun isTorTransportsSupported(context: Context): Boolean = transportBinary(context).canExecute()

    /**
     * The text of a setting as the value of an option of the core: trimmed, and null when it is blank or
     * starts with '-'. No value of these settings starts so, and one that did would read as an option of its
     * own wherever the app looks options up word by word: a link carrying dns=--upstream would leave the core
     * without the upstream the app gives it, so that it dialled out around Xray.
     */
    private fun settingValue(value: String?): String? = value?.trim()?.takeUnless { it.isEmpty() || it.startsWith('-') }

    fun buildArguments(
        profile: ProfileItem,
        port: Int,
        scan: Boolean = false,
        logLevel: String = DEFAULT_LOG_LEVEL,
    ): List<String> {
        val protocol = AetherProtocol.fromString(profile.aetherProtocol)
        // A scan looks for WARP endpoints from where the session will look: a carrier around the tunnel
        // stays, since the session reaches WARP from its exit, while one inside the tunnel has no part in it.
        val tor = AetherTor.fromString(profile.aetherTor).takeUnless { scan && it != AetherTor.REVERSE } ?: AetherTor.OFF
        val psiphon = AetherPsiphon.fromString(profile.aetherPsiphon).takeUnless { scan && it != AetherPsiphon.REVERSE } ?: AetherPsiphon.OFF
        // The listener the app dials takes [port]: Psiphon's or Tor's when one of them runs inside the
        // tunnel and is what the app reaches, the tunnel's own otherwise. Every other listener takes the
        // ports after it, in this order: the tunnel's own, then Tor's, then Psiphon's. Psiphon around the
        // tunnel is the exception: nothing of the app dials its listener and the core takes the port
        // Psiphon reports, so an ephemeral port keeps a scan core from colliding with the session's.
        // Tor around the tunnel gets a real port, since the core dials the address Tor was told to
        // listen on.
        val dialsPsiphon = psiphon == AetherPsiphon.CHAIN
        val dialsTor = tor == AetherTor.CHAIN && !dialsPsiphon
        var next = port + 1
        val own = if (dialsPsiphon || dialsTor) next++ else port
        val torBind = when (tor) {
            AetherTor.CHAIN -> if (dialsTor) port else next++
            AetherTor.REVERSE -> next++
            AetherTor.OFF, AetherTor.ONLY -> null
        }
        val psiphonBind = when (psiphon) {
            AetherPsiphon.CHAIN -> if (dialsPsiphon) port else next++
            AetherPsiphon.REVERSE -> 0
            AetherPsiphon.OFF, AetherPsiphon.ONLY -> null
        }
        return buildList {
            addAll(listOf("--bind", "${AppConfig.LOOPBACK}:$own"))
            if (psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY) {
                addAll(listOf("--protocol", protocol.type))
                addAll(listOf("--scan", AetherScanMode.fromString(profile.aetherScanMode).type))
                // Automatic obfuscation is the core's own choice per protocol, so nothing is said about it; MASQUE over
                // HTTP/2 takes none at all, since obfuscation shapes the UDP of WireGuard and HTTP/3 alone.
                val transport = AetherTransport.fromString(profile.aetherTransport)
                AetherObfuscation.fromString(profile.aetherObfuscation)
                    .takeUnless { it == AetherObfuscation.AUTO || masqueOverHttp2(protocol, transport, tor, psiphon) }
                    ?.let { addAll(listOf("--noize", it.type)) }
                addAll(listOf("--ip", AetherIpVersion.fromString(profile.aetherIpVersion).type))
                settingValue(profile.aetherDns)?.let { addAll(listOf("--dns", it)) }
                // A scan keeps the exit rule as well, so that it ends on an endpoint the session will accept.
                settingValue(profile.aetherExitLoc)?.let { addAll(listOf("--exit-loc", it)) }

                if (protocol.overMasque && transport == AetherTransport.HTTP2) {
                    add("--h2")
                    if (profile.aetherFragment == true) {
                        add("--fragment")
                        AetherRange.parse(profile.aetherFragmentSize, AetherRange.FRAGMENT_SIZE)
                            ?.let { addAll(listOf("--fragment-size", it.toString())) }
                        AetherRange.parse(profile.aetherFragmentDelay, AetherRange.FRAGMENT_DELAY)
                            ?.let { addAll(listOf("--fragment-delay", it.toString())) }
                    }
                }
                // Encrypted Client Hello hides the server name of the MASQUE handshake, on either carrier and both hops,
                // with the key of the HTTPS record of the ECH domain, asked of the ECH resolver.
                if (protocol.overMasque && profile.aetherEch == true) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", settingValue(profile.aetherEchDns) ?: AppConfig.AETHER_ECH_DNS))
                    addAll(listOf("--ech-domain", settingValue(profile.aetherEchDomain) ?: AppConfig.AETHER_ECH_DOMAIN))
                }
                // The ClientHello of the MASQUE handshakes, and of the WARP API calls and the ECH key lookup the core makes:
                // over HTTP/3, which carries TLS 1.3 alone, only its GREASE shows.
                if (protocol.overMasque) addAll(AetherFingerprint.fromString(profile.aetherFingerprint).arguments)

                if (protocol.twoHops) {
                    val hop = if (protocol == AetherProtocol.MIM) "--mim" else "--wiw"
                    val outer = AetherEndpoint.parse(profile.aetherWiwOuter).takeUnless { scan }
                    val inner = AetherEndpoint.parse(profile.aetherWiwInner).takeUnless { scan }
                    outer?.let { addAll(listOf("$hop-outer", it.toString())) }
                    inner?.let { addAll(listOf("$hop-inner", it.toString())) }
                    if (outer == null && inner == null) add("$hop-scan")
                } else if (!scan) {
                    AetherEndpoint.of(profile.server, profile.serverPort)?.let { addAll(listOf("--peer", it.toString())) }
                }

                add(if (scan) "--no-quick-reconnect" else "--quick-reconnect")
            }

            when (tor) {
                AetherTor.OFF -> Unit
                AetherTor.CHAIN -> add("--tor")
                AetherTor.REVERSE -> add("--tor-reverse")
                AetherTor.ONLY -> add("--tor-only")
            }
            torBind?.let { addAll(listOf(TOR_BIND, "${AppConfig.LOOPBACK}:$it")) }
            if (tor != AetherTor.OFF) {
                // Told nothing, the core tries Tor plainly and turns to fetched bridges where Tor is blocked.
                val bridges = AetherTorBridges.fromString(profile.aetherTorBridges)
                when (bridges) {
                    AetherTorBridges.AUTO -> Unit
                    AetherTorBridges.FIRST -> add("--tor-bridges")
                    AetherTorBridges.NEVER -> add("--no-tor-bridges")
                    AetherTorBridges.OWN -> AetherFmt.bridgeLines(profile.aetherTorBridgeLines)
                        .mapNotNull { settingValue(it) }
                        .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                // Where fetched bridges come from; with the profile's own lines, or none at all, nothing is fetched.
                if (bridges == AetherTorBridges.AUTO || bridges == AetherTorBridges.FIRST) {
                    AetherTorRelays.fromString(profile.aetherTorRelays).takeUnless { it == AetherTorRelays.AUTO }
                        ?.let { addAll(listOf("--tor-relays", it.type)) }
                }
            }

            when (psiphon) {
                AetherPsiphon.OFF -> Unit
                AetherPsiphon.CHAIN -> add("--psiphon")
                AetherPsiphon.REVERSE -> add("--psiphon-reverse")
                AetherPsiphon.ONLY -> add("--psiphon-only")
            }
            psiphonBind?.let { addAll(listOf(PSIPHON_BIND, "${AppConfig.LOOPBACK}:$it")) }
            if (psiphon != AetherPsiphon.OFF) {
                val shape = AetherPsiphonMode.fromString(profile.aetherPsiphonMode)
                addAll(listOf("--psiphon-mode", shape.type))
                // The CDN lists feed the fronted transports alone, which the direct shape never uses; the
                // server names count only beside an IP list of one's own, since the built-in list comes whole.
                val cdnIps = settingValue(profile.aetherPsiphonCdnIps)?.takeIf { shape != AetherPsiphonMode.DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                if (cdnIps != null) settingValue(profile.aetherPsiphonCdnSni)?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                // Which of the edge lists built into Psiphon the fronting scan tries; beside addresses of one's own, after them.
                if (shape != AetherPsiphonMode.DIRECT) {
                    AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(profile.aetherPsiphonCdnSets))?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                settingValue(profile.aetherPsiphonRegion)?.let { addAll(listOf("--psiphon-region", it)) }
                // The bundled list, unless the profile wants Psiphon to fetch a fresh one before it dials anything.
                if (profile.aetherPsiphonBundledList != false) addAll(listOf(PSIPHON_SERVER_ENTRIES, SHIPPED_LIST))
            }
            addAll(listOf("--log-level", logLevel))
        }
    }

    /**
     * Maps the app's core log level setting onto the levels the core accepts. Only the session
     * follows the setting: scans and key renewals keep the default because they read info lines.
     */
    internal fun coreLogLevel(appLevel: String?): String = when (appLevel?.lowercase(Locale.US)) {
        "debug" -> "debug"
        "info" -> "info"
        "warning", "warn" -> "warn"
        "error", "none" -> "error"
        else -> DEFAULT_LOG_LEVEL
    }

    /** [arguments] at [logLevel], unless they name a level of their own, as a hand-written command may. */
    internal fun withLogLevel(arguments: List<String>, logLevel: String): List<String> =
        if ("--log-level" in arguments || "--verbose" in arguments) arguments else arguments + listOf("--log-level", logLevel)

    /**
     * [arguments] at a level that writes the core's info lines, which a run that waits for one of
     * them needs: as they are, unless they name a quieter level, which gives way to the default.
     */
    internal fun withInfoLines(arguments: List<String>): List<String> =
        if (showsInfo(arguments)) arguments else withoutOption(arguments, "--log-level") + listOf("--log-level", DEFAULT_LOG_LEVEL)

    /**
     * Where the Psiphon client keeps its datastore: the servers it was given, fetched and discovered.
     * That state is no part of the WARP identity, so it lives beside the identity directory rather
     * than inside it, which a renewal of the identity replaces. Left to itself the core derives the
     * place from the identity file, inside that directory; a datastore still there moves out once.
     */
    internal fun psiphonStateDir(filesDir: File, workDir: File): File {
        val dir = File(filesDir, PSIPHON_STATE_DIR)
        val inside = File(workDir, "${AetherIdentityManager.BASE_FILE}-psiphon")
        if (!dir.exists() && inside.isDirectory && !inside.renameTo(dir)) {
            LogUtil.w(AppConfig.TAG, "AetherCoreManager: the Psiphon datastore could not leave the identity directory; the client starts over")
        }
        return dir
    }

    /**
     * Where the Psiphon client of a test, a scan or a renewal keeps its datastore: apart from the
     * session's, since the client holds its datastore under a lock and a second client on the same
     * one gives up after a second, which is how a session started beside a test came down at once.
     */
    internal fun psiphonProbeDir(filesDir: File): File = File(filesDir, PSIPHON_PROBE_DIR)

    /**
     * Forgets what the Psiphon client has learned: the session's datastore beside the identity
     * directory, one still inside it, and the one the probes keep. Its next start begins from the
     * bundled list again, or from a fresh download. For when no core runs; the caller makes sure of
     * that. True when all are gone.
     */
    internal fun clearPsiphonState(filesDir: File, workDir: File): Boolean {
        val dirs = listOf(
            File(filesDir, PSIPHON_STATE_DIR),
            File(workDir, "${AetherIdentityManager.BASE_FILE}-psiphon"),
            psiphonProbeDir(filesDir),
        )
        dirs.forEach { it.deleteRecursively() }
        return dirs.none { it.exists() }
    }

    /**
     * True for a core that a test, a scan or a renewal of a living app process runs: owned by a
     * process that is not known to be dead, and not marked as the session. A core whose
     * environment could not be read is not counted; it cannot be told apart.
     */
    internal fun isProbe(ownerAlive: Boolean?, sessionMarked: Boolean?): Boolean =
        ownerAlive != false && sessionMarked == false

    /**
     * Waits until [done] holds, looking every [pollMs], for at most [timeoutMs]; true when it held
     * in time. A bounded wait at a start, not a watch: it ends with the condition or the deadline.
     */
    internal fun awaitUntil(timeoutMs: Long, pollMs: Long, done: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (!done()) {
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(pollMs)
        }
        return true
    }

    /**
     * Waits, briefly, until no probe core is left, see [isProbe]. The daemon has just asked the test
     * service to cancel its workers; the session's core must not come up beside one of theirs, on
     * the same key or, with Psiphon, on the same datastore. True when none is left in time.
     */
    private fun awaitProbeCores(context: Context): Boolean =
        awaitUntil(PROBE_EXIT_WAIT_MS, PROBE_POLL_MS) { coreProcesses(context).none { isProbe(it.ownerAlive, it.sessionMarked) } }

    /**
     * [arguments] as the core is started with them: the word [SHIPPED_LIST] after [PSIPHON_SERVER_ENTRIES]
     * gives way to [entries], the app's unpacked list, or the flag goes when there is no such file. A
     * file of the command's own is left as written.
     */
    internal fun withShippedList(arguments: List<String>, entries: File?): List<String> {
        val at = arguments.indexOf(PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != SHIPPED_LIST) return arguments
        return if (entries == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = entries.absolutePath }
        }
    }

    /**
     * Starts a core on [arguments]. It uses, or registers where there are none, the keys in the
     * identity folder, or in [keysDir] instead, where a renewal gathers new keys apart from the keys
     * in use.
     */
    internal fun startProcess(
        context: Context,
        arguments: List<String>,
        markSession: Boolean = false,
        keysDir: File? = null,
        exitKey: String? = null,
    ): Process {
        val workDir = AetherIdentityManager.workDir(context).apply { mkdirs() }
        // A renewal stopped while it moved its new keys into place is finished before a core reads them.
        if (!AetherIdentityManager.settle(context)) {
            LogUtil.w(AppConfig.TAG, "AetherCore: the renewed keys could not all be moved into place; the keys not moved yet stay in use")
        }
        val keys = keysDir ?: workDir
        val shippedList = if (PSIPHON_SERVER_ENTRIES in arguments) {
            PsiphonServerList.entriesFile(File(Utils.userAssetPath(context)), workDir) { problem ->
                LogUtil.w(AppConfig.TAG, "AetherCore: ${AppConfig.PSIPHON_SERVERS_DAT} is not a usable Psiphon list; the entries kept from before stay", problem)
            }
        } else {
            null
        }
        val builder = ProcessBuilder(listOf(binary(context).absolutePath) + withShippedList(arguments, shippedList))
            .directory(workDir)
            .redirectErrorStream(true)
        builder.environment().apply {
            put(OWNER_ENV, android.os.Process.myPid().toString())
            if (markSession) put(SESSION_ENV, "1")
            exitKey?.let { put(EXIT_ENV, it) }
            psiphonBinary(context).takeIf { it.canExecute() }?.let { put(PSIPHON_BIN_ENV, it.absolutePath) }
            transportBinary(context).takeIf { it.canExecute() }?.let { transport ->
                put(TOR_PT_ENV, torTransports.joinToString(";") { "$it=${transport.absolutePath}" })
            }
            certificateDirectories(File::isDirectory)?.let { put(CERT_DIR_ENV, it) }
            psiphonOverlay(workDir)?.let { put(PSIPHON_CONFIG_ENV, it.absolutePath) }
            put(PSIPHON_DIR_ENV, (if (markSession) psiphonStateDir(context.filesDir, workDir) else psiphonProbeDir(context.filesDir)).absolutePath)
            put("HOME", workDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_CONFIG", File(workDir, AetherIdentityManager.BASE_FILE).absolutePath)
            put("AETHER_MASQUE_CONFIG", File(keys, AetherIdentityManager.MASQUE_FILE).absolutePath)
            put("AETHER_WG_CONFIG", File(keys, AetherIdentityManager.WIREGUARD_FILE).absolutePath)
        }
        return builder.start()
    }

    /**
     * Runs a core of its own, one that serves no session: a scan, a key renewal, a latency test. It
     * dials out through the exit of this process's Xray, whose traffic leaves by the exit-node [exit],
     * as the session's core dials out through the session's, see [CoreNativeManager.openExit]. For a
     * core that dials out through a hop of a proxy chain, [configuration] is the configuration under
     * test, whose exit-node that hop is; see [exitConfiguration]. The exit takes one core at a time, so
     * the cores of a process take turns; it opens before the core starts and closes once the core has
     * ended.
     */
    internal suspend fun <T> withProcess(
        context: Context,
        arguments: List<String>,
        exit: AetherExit,
        source: String,
        onOutput: (String) -> Unit,
        keysDir: File? = null,
        configuration: String? = null,
        block: suspend (output: ReceiveChannel<String>) -> T?,
    ): T? = throughExit(
        turns = exitTurns,
        open = {
            val logLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: DEFAULT_XRAY_LOG_LEVEL
            openExit(context, exitConfiguration(exit, configuration, logLevel), source)
        },
        close = { CoreNativeManager.closeExit() },
    ) { exitPort ->
        coroutineScope { runCore(context, standaloneArguments(arguments, exitPort), source, onOutput, keysDir, block) }
    }

    /** One core of its own at a time in this process, as the exit of its Xray takes; see [withProcess]. */
    private val exitTurns = Mutex()

    /**
     * [run] with the port of the exit that [open] opens, null when it does not open, in which case
     * nothing runs. The exit is taken in turns by [turns], and [close] closes it once [run] is over,
     * however it ends: with a result, with none, with an error, or cancelled.
     */
    internal suspend fun <T> throughExit(
        turns: Mutex,
        open: () -> Int?,
        close: () -> Unit,
        run: suspend (exitPort: Int) -> T?,
    ): T? = turns.withLock {
        // A cancellation can land while the exit opens or while its port is on the way back to this
        // coroutine; whether it opened is kept aside, so that it is closed on that path as well.
        val opened = AtomicBoolean(false)
        try {
            val exitPort = withContext(Dispatchers.IO) { open()?.also { opened.set(true) } } ?: return@withLock null
            run(exitPort)
        } finally {
            if (opened.getAndSet(false)) withContext(NonCancellable + Dispatchers.IO) { close() }
        }
    }

    /** A core of its own on [arguments], ended once [block] is over; null when it does not start. See [withProcess]. */
    private suspend fun <T> CoroutineScope.runCore(
        context: Context,
        arguments: List<String>,
        source: String,
        onOutput: (String) -> Unit,
        keysDir: File?,
        block: suspend (output: ReceiveChannel<String>) -> T?,
    ): T? {
        // A cancellation can land while the spawn runs or while its result is on the way back to this
        // coroutine; either way the core would keep running with nobody holding its handle, so the
        // handle is kept aside and the core is ended on that path as on any other, before the exit
        // closes and the next core may take the port.
        val spawned = AtomicReference<Process?>()
        val process = try {
            withContext(Dispatchers.IO) {
                try {
                    reapStale(context, null)
                    startProcess(context, arguments, keysDir = keysDir).also(spawned::set)
                } catch (e: IOException) {
                    LogUtil.e(AppConfig.TAG, "AetherCore: failed to launch $source", e)
                    null
                }
            }
        } catch (e: CancellationException) {
            spawned.get()?.let { core -> withContext(NonCancellable + Dispatchers.IO) { end(core, context) } }
            throw e
        } ?: return null

        val output = Channel<String>(Channel.UNLIMITED)
        launch(Dispatchers.IO) { forward(process, source, onOutput, output) }
        try {
            ensureActive()
            return block(output)
        } finally {
            // The ending waits for the core and its helpers to be gone; a scan or a renewal calls from the main thread.
            withContext(NonCancellable + Dispatchers.IO) { end(process, context) }
        }
    }

    /**
     * The arguments of a core of its own, see [withProcess]: [arguments] dialling out through the exit
     * on [exitPort]. An upstream they name, as a configuration exported from a session names the
     * session's Xray, gives way to it.
     */
    internal fun standaloneArguments(arguments: List<String>, exitPort: Int): List<String> =
        AetherCore(withoutOption(arguments, UPSTREAM)).through(exitPort).arguments

    /**
     * The port of the exit of this process's Xray, opened for [source] with the exit-node of
     * [configuration], see [CoreNativeManager.openExit]; null, with the reason in the log, when it
     * does not open.
     */
    private fun openExit(context: Context, configuration: String, source: String): Int? = try {
        CoreNativeManager.openExit(context, configuration)
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "AetherCore: the Xray $source dials out through did not open", e)
        null
    }

    /**
     * The configuration the exit of a core of its own opens with, whose outbound tagged exit-node the
     * core dials out by: [configuration], the configuration under test, when it has such an outbound,
     * as that of a core dialling out through a hop of its proxy chain has, or a custom configuration
     * exported from a session; otherwise one with the plain exit-node of [exit] alone, which logs at
     * [logLevel], the Xray log level of the app, should it start the shared Xray of the process.
     */
    internal fun exitConfiguration(exit: AetherExit, configuration: String?, logLevel: String): String =
        configuration?.takeIf(::hasExitNode) ?: JsonObject().apply {
            add("log", JsonObject().apply { addProperty("loglevel", logLevel) })
            add("outbounds", JsonArray().apply { add(JsonParser.parseString(JsonUtil.toJson(CoreOutboundBuilder.toOutboundAetherExit(exit)))) })
        }.toString()

    /** Whether the configuration [content] has an outbound tagged exit-node. */
    private fun hasExitNode(content: String): Boolean = try {
        JsonParser.parseString(content).takeIf { it.isJsonObject }?.asJsonObject
            ?.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.any { outbound ->
                val tag = outbound.takeIf { it.isJsonObject }?.asJsonObject?.get("tag")
                tag != null && tag.isJsonPrimitive && tag.asString == AppConfig.TAG_EXIT_NODE
            } == true
    } catch (_: RuntimeException) {
        false
    }

    /** The Xray log level the app uses unless the settings name another. */
    private const val DEFAULT_XRAY_LOG_LEVEL = "warning"

    internal suspend fun <T : Any> runUntil(
        context: Context,
        arguments: List<String>,
        exit: AetherExit,
        timeoutMs: Long,
        source: String,
        onOutput: (String) -> Unit,
        keysDir: File? = null,
        match: (String) -> T?,
    ): T? = withProcess(context, arguments, exit, source, onOutput, keysDir) { output ->
        withTimeoutOrNull(timeoutMs) { output.receiveAsFlow().mapNotNull(match).firstOrNull() }
    }

    /**
     * Starts the session core [core], at the log level of the app setting unless its arguments name
     * one. With [afterProbes], the start first waits for the cores of tests and scans to be gone,
     * for the caller has just told the test service to cancel them.
     */
    @Synchronized
    fun start(context: Context, core: AetherCore, afterProbes: Boolean = false, onExit: () -> Unit) {
        stop()
        val appContext = context.applicationContext
        var logLevel = coreLogLevel(MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL))
        // The ready word is an info line; a quieter setting must not leave a Psiphon session waiting for it.
        if (readyNeedsWord(core.arguments) && logLevel !in infoLevels) logLevel = DEFAULT_LOG_LEVEL
        val arguments = withLogLevel(core.arguments, logLevel)
        val next = Session(core.port, needsWord = readyNeedsWord(arguments) && showsInfo(arguments), context = appContext, onExit = onExit)
        session = next
        stoppedForEchKey = false
        lifecycle.execute { open(next, appContext, arguments, afterProbes, core.exit.key) }
    }

    /**
     * True when the listener the app dials is Psiphon's, inside the tunnel or alone: it answers the
     * greeting before it carries anything, so readiness needs the core's word as well.
     */
    internal fun readyNeedsWord(arguments: List<String>): Boolean =
        psiphonModeOf(arguments).let { it == AetherPsiphon.CHAIN || it == AetherPsiphon.ONLY }

    /** True when [line] is the core's word that the listener the app dials carries traffic now. */
    internal fun isReadyWord(line: String): Boolean = psiphonReady.containsMatchIn(line)

    /** True when [line] is the core's word that it stopped for want of an ECH key; see [noEchKey]. */
    internal fun isNoEchKeyWord(line: String): Boolean = noEchKey.containsMatchIn(line)

    /** What to tell the user of the session core that ended on its own last, in the words it ended with. */
    @StringRes
    fun stoppedMessage(): Int = stoppedMessage(stoppedForEchKey)

    /** What to tell the user of a session core that ended on its own, with [noEchKey] when it ended for want of an ECH key. */
    @StringRes
    internal fun stoppedMessage(noEchKey: Boolean): Int =
        if (noEchKey) R.string.aether_core_stopped_no_ech_key else R.string.aether_core_stopped

    /** True when a core started with [arguments] writes its info lines, at the level named or at the default. */
    internal fun showsInfo(arguments: List<String>): Boolean =
        "--verbose" in arguments || (valueAfter(arguments, "--log-level") ?: DEFAULT_LOG_LEVEL) in infoLevels

    /**
     * Stops the session core and whatever it started. The work runs on the core's executor, so a
     * start that follows queues behind it and comes up only once the old core and its helpers are
     * gone, with their listeners and Psiphon's datastore lock.
     */
    @Synchronized
    fun stop() {
        val current = session ?: return
        session = null
        lifecycle.execute { current.process?.let { end(it, current.context) } }
    }

    suspend fun awaitListening(timeoutMs: Long): Boolean =
        awaitReady(timeoutMs, READY_POLL_MS, { isRunning }, { session?.let { it.wordSeen && answersSocks(it.port) } == true })

    internal suspend fun awaitReady(
        timeoutMs: Long,
        pollMs: Long,
        running: () -> Boolean,
        listening: () -> Boolean,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        while (running()) {
            if (withContext(Dispatchers.IO) { listening() }) return@withTimeoutOrNull true
            delay(pollMs)
        }
        false
    } ?: false

    /** What the daemon's warm-up wait ends with once it stops polling. */
    internal enum class WarmUpOutcome {
        /** The listener accepts connections; the profile can carry traffic. */
        LISTENING,

        /** The core exited before its listener came up while the service still runs. */
        CORE_EXITED,

        /** The wait was cancelled or the service stopped meanwhile; nothing is left to report. */
        ABANDONED,
    }

    /**
     * Decides what the warm-up wait reports. A core that died before its listener came up is reported
     * here as well as by its exit callback, and the service stops on whichever comes first.
     */
    internal fun warmUpOutcome(listening: Boolean, active: Boolean, serviceRunning: Boolean): WarmUpOutcome = when {
        !active || !serviceRunning -> WarmUpOutcome.ABANDONED
        listening -> WarmUpOutcome.LISTENING
        else -> WarmUpOutcome.CORE_EXITED
    }

    /**
     * True when a SOCKS server answers on the loopback [port]: the connection is taken and the
     * greeting gets its reply. A listener bound before anything serves it, as Tor's is bound before
     * Tor has bootstrapped, takes the connection into its backlog and says nothing, so it does not
     * count until it does.
     */
    internal fun answersSocks(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(AppConfig.LOOPBACK, port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            socket.getOutputStream().write(socksGreeting)
            socket.getInputStream().read() == SOCKS_VERSION
        }
    } catch (_: IOException) {
        false
    }

    internal fun relay(line: String, source: String) {
        val text = line.trim()
        if (text.isEmpty()) return
        // Psiphon's report of the countries it can leave from is kept for the editor's list.
        PsiphonServerList.regionsOf(text)?.let(PsiphonServerList::remember)
        val message = "[$source] $text"
        when (outputPriority(text)) {
            Log.ERROR -> LogUtil.e(AppConfig.TAG, message)
            Log.WARN -> LogUtil.w(AppConfig.TAG, message)
            Log.DEBUG -> LogUtil.d(AppConfig.TAG, message)
            else -> LogUtil.i(AppConfig.TAG, message)
        }
    }

    internal fun outputPriority(line: String): Int {
        if (line.startsWith("Error:")) return Log.ERROR
        return when (logHeader(line)?.get(1)) {
            "ERROR" -> Log.ERROR
            "WARN" -> Log.WARN
            "DEBUG", "TRACE" -> Log.DEBUG
            else -> Log.INFO
        }
    }

    internal fun outputMessage(line: String): String {
        val text = line.trim()
        return if (logHeader(text) != null) text.substringAfter(']').trim() else text
    }

    private fun logHeader(line: String): List<String>? {
        if (!line.startsWith('[')) return null
        val headerEnd = line.indexOf(']').takeIf { it > 0 } ?: return null
        return line.substring(1, headerEnd)
            .split(' ')
            .filter(String::isNotEmpty)
            .takeIf { it.size >= 3 && it[1] in logLevels }
    }

    /**
     * Kills leftover core processes of this app: any whose owning app process is gone and, when
     * [bindAddress] is given, any still holding that listener address. Android can kill the
     * daemon, the editor or the test service without their child processes following, and a
     * survivor on the session port would otherwise make every later start fail until a reboot.
     */
    internal fun reapStale(context: Context, bindAddress: String?) {
        val killed = mutableSetOf<Int>()
        for (core in coreProcesses(context)) {
            if (!isStale(core.argv, core.ownerAlive, bindAddress)) continue
            LogUtil.w(
                AppConfig.TAG,
                "AetherCore: killing a leftover core process, pid=${core.pid} bind=${bindAddressOf(core.argv)} ownerAlive=${core.ownerAlive}"
            )
            android.os.Process.killProcess(core.pid)
            killed += core.pid
        }
        reapOrphans(context, killed)
    }

    /**
     * Ends [process], a core, and then whatever it started. The core has no signal handling, so
     * the term that ends it takes nothing with it: Psiphon's client and the pluggable transport
     * would live on, holding their listeners and, Psiphon, its datastore lock, until they noticed
     * their pipes gone, and a core started meanwhile would fail on them.
     */
    private fun end(process: Process, context: Context) {
        if (!endProcess(process)) {
            LogUtil.w(AppConfig.TAG, "AetherCore: the core did not end on request and was killed; what it started is ended regardless")
        }
        reapOrphans(context)
    }

    /**
     * Asks [process] to end and, when it has not ended within [waitMs], kills it, which it cannot
     * refuse; the kill is waited for as long again. True when it ended on request.
     */
    internal fun endProcess(process: Process, waitMs: Long = EXIT_WAIT_MS): Boolean {
        process.destroy()
        if (awaitUntil(waitMs, EXIT_POLL_MS) { !isAlive(process) }) return true
        process.destroyForcibly()
        awaitUntil(waitMs, EXIT_POLL_MS) { !isAlive(process) }
        return false
    }

    private fun isAlive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    /**
     * Ends the helper processes of the app, Psiphon's client and the pluggable transport, that no
     * living core has as a parent, [gone] cores not counting as living: a term first, for Psiphon
     * to close its tunnel and datastore, and a kill for one that has not gone in time.
     */
    internal fun reapOrphans(context: Context, gone: Set<Int> = emptySet()) {
        val live = coreProcesses(context).map { it.pid }.toSet() - gone
        val orphans = orphanedHelpers(helperProcesses(context), live)
        if (orphans.isEmpty()) return
        LogUtil.w(AppConfig.TAG, "AetherCore: ending ${orphans.size} helper process(es) left behind by a core, pids=$orphans")
        orphans.forEach { android.os.Process.sendSignal(it, SIGTERM) }
        awaitUntil(EXIT_WAIT_MS, EXIT_POLL_MS) { orphans.none { File(procDir, it.toString()).isDirectory } }
        orphans.filter { File(procDir, it.toString()).isDirectory }.forEach { android.os.Process.killProcess(it) }
    }

    /** The pids among [helpers], each with its parent pid, whose parent is none of [liveCores]; a parent that could not be read counts as none. */
    internal fun orphanedHelpers(helpers: List<Pair<Int, Int?>>, liveCores: Set<Int>): List<Int> =
        helpers.filter { (_, parent) -> parent == null || parent !in liveCores }.map { it.first }

    /** The parent pid in [stat], the text of /proc/<pid>/stat: the second field after the command name in parentheses. */
    internal fun parentPidOf(stat: String): Int? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        return stat.substring(close + 1).trim().split(' ').getOrNull(1)?.toIntOrNull()
    }

    /** The helper processes of the app found in /proc, each with its parent pid when it could be read. */
    private fun helperProcesses(context: Context): List<Pair<Int, Int?>> {
        val helpers = setOf(psiphonBinary(context).absolutePath, transportBinary(context).absolutePath)
        val entries = procDir.listFiles() ?: return emptyList()
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            val argv = readNulSeparated(File(entry, "cmdline")) ?: return@mapNotNull null
            if (argv.firstOrNull() !in helpers) return@mapNotNull null
            pid to runCatching { File(entry, "stat").readText() }.getOrNull()?.let(::parentPidOf)
        }
    }

    /**
     * The arguments of the daemon's live session core, without the binary, or null when no core
     * owned by a living app process is the session. They are read from /proc, so they are
     * available during the scanning phase before the listener exists, which is exactly when the
     * shared key files must not be replaced and no second tunnel must be opened on the same key.
     */
    fun sessionArguments(context: Context): List<String>? = sessionProcess(context)?.argv?.drop(1)

    /**
     * Whether this process can list the processes in /proc, where the session's core is told apart from
     * the cores of tests and scans. Where it cannot, only a listener on the Aether port can stand in for
     * a session, and a core of a test or a scan listens on that port as well.
     */
    internal fun canListProcesses(): Boolean = procDir.listFiles() != null

    /** The daemon's live session core as /proc shows it, or null without one; see [sessionArguments]. */
    internal fun sessionProcess(context: Context): CoreProcess? =
        coreProcesses(context).firstOrNull { isSession(it.argv, it.ownerAlive, it.sessionMarked, sessionAddress) }

    /** The protocol of the daemon's live session, or null without one; see [sessionArguments]. */
    fun sessionProtocol(context: Context): AetherProtocol? = sessionArguments(context)?.let(::protocolOf)

    /**
     * True when [arguments] are those the daemon starts [profile] with, apart from the log level,
     * which follows a setting that can change while the session runs, and from the listener: the
     * same tunnel behind another port, as another profile or a custom configuration may run it,
     * serves the profile just as well. This is how another process tells the running profile from
     * a merely selected one.
     */
    fun runsProfile(arguments: List<String>, profile: ProfileItem): Boolean = AetherCore.of(profile).runsAs(arguments)

    /**
     * [arguments] without the listeners, the log level and the upstream proxy: what tells one tunnel
     * from another. The session's core dials out through Xray and its profile does not say so.
     */
    internal fun tunnelArguments(arguments: List<String>): List<String> =
        listOf("--log-level", "--bind", TOR_BIND, PSIPHON_BIND, UPSTREAM).fold(arguments, ::withoutOption)

    /** [arguments] without every [flag] and the value after it. */
    internal fun withoutOption(arguments: List<String>, flag: String): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            if (arguments[index] == flag) index += 2 else kept.add(arguments[index++])
        }
        return kept
    }

    /** [arguments] with the listener [flag] names on the loopback port [port], in place of whatever they named for it. */
    internal fun withListener(arguments: List<String>, flag: String, port: Int): List<String> =
        withoutOption(arguments, flag) + listOf(flag, "${AppConfig.LOOPBACK}:$port")

    /**
     * The option naming the listener the app dials: Psiphon's when Psiphon runs inside the tunnel,
     * Tor's when Tor does, the core's own otherwise. Both inside at once is refused by the editor; a
     * command written that way is dialled on Psiphon's.
     */
    internal fun listenerFlagOf(arguments: List<String>): String = when {
        psiphonModeOf(arguments) == AetherPsiphon.CHAIN -> PSIPHON_BIND
        torModeOf(arguments) == AetherTor.CHAIN -> TOR_BIND
        else -> "--bind"
    }

    /** Where Tor stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    internal fun torModeOf(argv: List<String>): AetherTor = argv.fold(AetherTor.OFF) { mode, word ->
        when (word) {
            "--tor" -> AetherTor.CHAIN
            "--tor-reverse" -> AetherTor.REVERSE
            "--tor-only" -> AetherTor.ONLY
            else -> mode
        }
    }

    /** Where Psiphon stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    internal fun psiphonModeOf(argv: List<String>): AetherPsiphon = argv.fold(AetherPsiphon.OFF) { mode, word ->
        when (word) {
            "--psiphon" -> AetherPsiphon.CHAIN
            "--psiphon-reverse" -> AetherPsiphon.REVERSE
            "--psiphon-only" -> AetherPsiphon.ONLY
            else -> mode
        }
    }

    /**
     * The tunnel [argv] runs, as the names of its parts from the outside in: a carrier around the
     * tunnel, the WARP protocol, a carrier inside it. A carrier alone is the whole tunnel, and Tor
     * alone comes before Psiphon alone, as the core runs it before it looks at Psiphon.
     */
    internal fun pathOf(argv: List<String>): List<String> {
        val tor = torModeOf(argv)
        val psiphon = psiphonModeOf(argv)
        if (tor == AetherTor.ONLY) return listOf(TOR_NAME)
        if (psiphon == AetherPsiphon.ONLY) return listOf(PSIPHON_NAME)
        return buildList {
            if (tor == AetherTor.REVERSE) add(TOR_NAME)
            if (psiphon == AetherPsiphon.REVERSE) add(PSIPHON_NAME)
            add(protocolOf(argv).name)
            if (psiphon == AetherPsiphon.CHAIN) add(PSIPHON_NAME)
            if (tor == AetherTor.CHAIN) add(TOR_NAME)
        }
    }

    /** The carriers as [pathOf] names them, beside the names of [AetherProtocol]. */
    private const val TOR_NAME = "TOR"
    private const val PSIPHON_NAME = "PSIPHON"

    /** A core process is stale when its owner is known to be dead or it holds the address we are about to bind. */
    internal fun isStale(argv: List<String>, ownerAlive: Boolean?, bindAddress: String?): Boolean =
        ownerAlive == false || (bindAddress != null && listenerAddressOf(argv) == bindAddress)

    /**
     * A core process counts as the session while its owner is not known to be dead and it carries
     * the session mark. When its environment could not be read, [sessionMarked] is null and the
     * address a session holds by default stands in for the mark.
     */
    internal fun isSession(argv: List<String>, ownerAlive: Boolean?, sessionMarked: Boolean?, sessionAddress: String): Boolean =
        ownerAlive != false && (sessionMarked ?: (listenerAddressOf(argv) == sessionAddress))

    private val sessionAddress: String get() = "${AppConfig.LOOPBACK}:$socksPort"

    /**
     * A core process of this app found in /proc; [ownerAlive] is null when its owner could not be
     * read, [sessionMarked] when its environment could not. [exit] is the key of the exit-node of a
     * session core, see [EXIT_ENV]; null when its environment could not be read or names none.
     */
    internal class CoreProcess(
        val pid: Int,
        val argv: List<String>,
        val ownerAlive: Boolean?,
        val sessionMarked: Boolean?,
        val exit: String? = null,
    )

    private fun coreProcesses(context: Context): List<CoreProcess> {
        val binary = binary(context).absolutePath
        val entries = procDir.listFiles() ?: return emptyList()
        return entries.mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            val argv = readNulSeparated(File(entry, "cmdline")) ?: return@mapNotNull null
            if (argv.firstOrNull() != binary) return@mapNotNull null
            val environ = readNulSeparated(File(entry, "environ"))
            val ownerAlive = ownerPid(environ)?.let { File(procDir, it.toString()).isDirectory }
            CoreProcess(pid, argv, ownerAlive, environ?.let(::isSessionMarked), environ?.let(::exitKeyOf))
        }
    }

    internal fun bindAddressOf(argv: List<String>): String? = valueAfter(argv, "--bind")

    /** The port of the core's own listener, null when [argv] names none. */
    internal fun bindPortOf(argv: List<String>): Int? = portAfter(argv, "--bind")

    /** The address of the listener the app dials, see [listenerFlagOf]; null when [argv] names none. */
    internal fun listenerAddressOf(argv: List<String>): String? = valueAfter(argv, listenerFlagOf(argv))

    /** The port of the listener the app dials, null when [argv] names none. */
    internal fun listenerPortOf(argv: List<String>): Int? =
        listenerAddressOf(argv)?.substringAfterLast(':', "")?.toIntOrNull()

    /** The port of the address after [flag], null when there is none or it cannot be read. */
    internal fun portAfter(argv: List<String>, flag: String): Int? =
        valueAfter(argv, flag)?.substringAfterLast(':', "")?.toIntOrNull()

    /**
     * The protocol [argv] selects, read the way the core reads it: the last of --protocol and the
     * protocol flags wins, a hop named without any of them selects the two-hop protocol it belongs
     * to, warp-in-warp before masque-in-masque, and nothing at all is masque.
     */
    internal fun protocolOf(argv: List<String>): AetherProtocol {
        var chosen: AetherProtocol? = null
        var wiwHopNamed = false
        var mimHopNamed = false
        for ((index, word) in argv.withIndex()) {
            when (word) {
                "--protocol" -> chosen = argv.getOrNull(index + 1)?.let(::protocolNamed) ?: chosen
                "--masque" -> chosen = AetherProtocol.MASQUE
                "--wg", "--wireguard", "--warp" -> chosen = AetherProtocol.WIREGUARD
                "--gool", "--wiw" -> chosen = AetherProtocol.GOOL
                "--mim", "--masque-in-masque" -> chosen = AetherProtocol.MIM
                "--wiw-outer", "--gool-outer", "--outer-peer", "--wiw-inner", "--gool-inner", "--inner-peer" -> wiwHopNamed = true
                "--wiw-peers", "--gool-peers" -> if (namesHops(argv.getOrNull(index + 1))) wiwHopNamed = true
                "--mim-outer", "--mim-inner" -> mimHopNamed = true
                "--mim-peers" -> if (namesHops(argv.getOrNull(index + 1))) mimHopNamed = true
            }
        }
        return chosen ?: when {
            wiwHopNamed -> AetherProtocol.GOOL
            mimHopNamed -> AetherProtocol.MIM
            else -> AetherProtocol.MASQUE
        }
    }

    /** The protocol the core selects for [name] after --protocol, under any of the names it accepts. */
    private fun protocolNamed(name: String): AetherProtocol = when (name.trim().lowercase(Locale.US)) {
        "wg", "wireguard" -> AetherProtocol.WIREGUARD
        "gool", "wiw", "warp-in-warp", "warpinwarp" -> AetherProtocol.GOOL
        "mim", "m2", "masque-in-masque", "masqueinmasque" -> AetherProtocol.MIM
        else -> AetherProtocol.MASQUE
    }

    /** Whether a value of --wiw-peers or --mim-peers names hops rather than asking for a scan, as the core reads it. */
    private fun namesHops(value: String?): Boolean = value != null && value.lowercase(Locale.US) !in scanKeywords

    /** The values of --wiw-peers and --mim-peers that ask for a scan instead of naming hops, as the core reads them. */
    private val scanKeywords = setOf("auto", "scan", "none", "off", "0")

    /** The value after the last [flag] in [argv]; the last one is the one the core keeps. */
    private fun valueAfter(argv: List<String>, flag: String): String? =
        argv.lastIndexOf(flag).takeIf { it >= 0 }?.let { argv.getOrNull(it + 1) }

    internal fun ownerPid(environ: List<String>?): Int? =
        environ?.firstOrNull { it.startsWith("$OWNER_ENV=") }?.substringAfter('=')?.toIntOrNull()

    internal fun isSessionMarked(environ: List<String>): Boolean = environ.any { it.startsWith("$SESSION_ENV=") }

    internal fun exitKeyOf(environ: List<String>): String? =
        environ.firstOrNull { it.startsWith("$EXIT_ENV=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }

    private fun readNulSeparated(file: File): List<String>? = try {
        file.readBytes().toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    private fun psiphonBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, PSIPHON_BINARY_NAME)

    private fun transportBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, TRANSPORT_BINARY_NAME)

    /** The certificate directories of this device that [exists], joined the way Go reads them; null when there is none. */
    internal fun certificateDirectories(exists: (File) -> Boolean): String? =
        androidCertificateDirectories.filter { exists(File(it)) }.takeIf { it.isNotEmpty() }?.joinToString(":")

    /** The Psiphon overlay in [workDir], written when it is missing or says something else; null when it cannot be written. */
    private fun psiphonOverlay(workDir: File): File? = try {
        File(workDir, PSIPHON_OVERLAY_FILE).apply { if (!isFile || readText() != PSIPHON_OVERLAY) writeText(PSIPHON_OVERLAY) }
    } catch (e: IOException) {
        LogUtil.w(AppConfig.TAG, "AetherCore: the Psiphon overlay could not be written", e)
        null
    }

    private fun open(target: Session, context: Context, arguments: List<String>, afterProbes: Boolean, exitKey: String) {
        if (session !== target) return
        if (afterProbes && !awaitProbeCores(context)) {
            LogUtil.w(AppConfig.TAG, "AetherCore: a core of a test or scan is still up; the session starts beside it")
        }
        if (session !== target) return
        reapStale(context, listenerAddressOf(arguments))
        val process = try {
            startProcess(context, arguments, markSession = true, exitKey = exitKey)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "AetherCore: failed to launch the core", e)
            if (release(target)) target.onExit()
            return
        }
        target.process = process
        thread(name = "aether-core-output", isDaemon = true) { watch(target, process) }
    }

    private fun forward(process: Process, source: String, onOutput: (String) -> Unit, output: Channel<String>) {
        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                relay(line, source)
                onOutput(line)
                output.trySend(line)
            }
        } catch (e: IOException) {
            LogUtil.d(AppConfig.TAG, "AetherCore: $source output closed: ${e.message}")
        } finally {
            output.close()
        }
    }

    private fun watch(target: Session, process: Process) {
        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                relay(line, "aether")
                if (!target.wordSeen && isReadyWord(line)) target.wordSeen = true
                if (isNoEchKeyWord(line)) target.noEchKey = true
            }
        } catch (e: IOException) {
            LogUtil.d(AppConfig.TAG, "AetherCore: output closed: ${e.message}")
        }
        val exitCode = process.waitFor()
        if (!release(target)) return
        reapOrphans(target.context)
        LogUtil.e(AppConfig.TAG, "AetherCore: the core exited on its own with code $exitCode")
        target.onExit()
    }

    @Synchronized
    private fun release(target: Session): Boolean {
        if (session !== target) return false
        session = null
        // Set before the exit is reported, which reads it; the core has written its last line by now.
        stoppedForEchKey = target.noEchKey
        return true
    }

    /** [wordSeen] starts true where no word is needed, so the listener alone decides there. */
    private class Session(val port: Int, needsWord: Boolean, val context: Context, val onExit: () -> Unit) {
        var process: Process? = null

        @Volatile
        var wordSeen: Boolean = !needsWord

        /** Whether the core said it stopped for want of an ECH key. */
        @Volatile
        var noEchKey: Boolean = false
    }
}
