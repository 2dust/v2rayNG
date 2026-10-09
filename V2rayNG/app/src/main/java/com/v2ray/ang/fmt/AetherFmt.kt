package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherCore
import com.v2ray.ang.core.AetherCoreManager
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
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.Utils
import java.net.URI
import java.util.Locale

object AetherFmt : FmtBase() {

    enum class Problem {
        INVALID_PEER,
        INVALID_HOP,
        SHARED_HOP,
        INVALID_FRAGMENT,
        INVALID_DNS,
        INVALID_EXIT_LOC,
        INVALID_ECH_DNS,
        INVALID_ECH_DOMAIN,
        LISTEN_PORT_TAKEN,
        PSIPHON_NEEDS_MASQUE,
        NEXT_PORT_TAKEN,
        TOR_NEEDS_MASQUE,
        TOR_PSIPHON_CONFLICT,
        TOR_BRIDGES_MISSING,
        INVALID_COMMAND,
    }

    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.AETHER)

        val uri = URI(Utils.fixIllegalUrl(str))
        val queryParam = if (uri.rawQuery.isNullOrEmpty()) emptyMap() else getQueryParam(uri)
        val protocol = AetherProtocol.fromString(queryParam["protocol"])

        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).ifEmpty { "Aether" }
        config.aetherProtocol = protocol.type
        config.aetherTransport = AetherTransport.fromString(queryParam["transport"]).type
        config.aetherScanMode = AetherScanMode.fromString(queryParam["scan"]).type
        config.aetherObfuscation = AetherObfuscation.fromString(queryParam["noize"]).type
        config.aetherIpVersion = AetherIpVersion.fromString(queryParam["ip"]).type
        config.aetherFragment = queryParam["fragment"] == "1"
        config.aetherFragmentSize = AetherRange.parse(queryParam["fragment_size"], AetherRange.FRAGMENT_SIZE)?.toString()
        config.aetherFragmentDelay = AetherRange.parse(queryParam["fragment_delay"], AetherRange.FRAGMENT_DELAY)?.toString()
        config.aetherEch = queryParam["ech"] == "1"
        // A value the core would not take is left out; the profile then follows the default.
        config.aetherEchDns = queryParam["ech_dns"]?.trim()?.takeIf { config.aetherEch == true && isEchDns(it) }
        config.aetherEchDomain = queryParam["ech_domain"]?.trim()?.takeIf { config.aetherEch == true && isEchDomain(it) }
        config.aetherFingerprint = queryParam["fingerprint"]?.let { AetherFingerprint.fromString(it).type }
        config.aetherDns = queryParam["dns"]
        config.aetherExitLoc = queryParam["exit_loc"]
        // A link from before the Aether listen port was one setting for every profile may name a port of its own, which counts no more.
        config.aetherPsiphon = AetherPsiphon.fromString(queryParam["psiphon"]).type.takeUnless { it == AetherPsiphon.OFF.type }
        config.aetherPsiphonMode = queryParam["psiphon_mode"]?.let { AetherPsiphonMode.fromString(it).type }
        config.aetherPsiphonCdnIps = queryParam["cdn_ips"]
        config.aetherPsiphonCdnSni = queryParam["cdn_sni"]
        config.aetherPsiphonCdnSets = queryParam["cdn_sets"]
        config.aetherPsiphonRegion = queryParam["region"]
        config.aetherPsiphonBundledList = if (queryParam["psiphon_bundled"] == "0") false else null
        config.aetherTor = AetherTor.fromString(queryParam["tor"]).type.takeUnless { it == AetherTor.OFF.type }
        config.aetherTorBridges = queryParam["tor_bridges"]?.let { AetherTorBridges.fromString(it).type }
        config.aetherTorBridgeLines = queryParam["bridges"]?.split(';')?.joinToString("\n")
        config.aetherTorRelays = queryParam["tor_relays"]?.let { AetherTorRelays.fromString(it).type }
        // The exit-node's, under the names the link of an ordinary profile gives its own outbound's.
        config.finalMask = queryParam["fm"]
        config.dialMode = queryParam["dialMode"]

        if (protocol.twoHops) {
            val outer = AetherEndpoint.parse(queryParam["outer"])
            val inner = AetherEndpoint.parse(queryParam["inner"])?.takeUnless { it.host == outer?.host }
            config.aetherWiwOuter = outer?.toString()
            config.aetherWiwInner = inner?.toString()
        } else {
            val endpoint = AetherEndpoint.of(uri.idnHost, uri.port.takeIf { it > 0 }?.toString())
            config.server = endpoint?.host
            config.serverPort = endpoint?.port?.toString()
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val protocol = AetherProtocol.fromString(config.aetherProtocol)
        val query = linkedMapOf(
            "protocol" to protocol.type,
            "scan" to AetherScanMode.fromString(config.aetherScanMode).type,
        )
        // Automatic obfuscation is the core's own choice per protocol, and MASQUE over HTTP/2 takes none; a link says nothing about either.
        val overHttp2 = AetherCoreManager.masqueOverHttp2(
            protocol,
            AetherTransport.fromString(config.aetherTransport),
            AetherTor.fromString(config.aetherTor),
            AetherPsiphon.fromString(config.aetherPsiphon),
        )
        AetherObfuscation.fromString(config.aetherObfuscation)
            .takeUnless { it == AetherObfuscation.AUTO || overHttp2 }
            ?.let { query["noize"] = it.type }
        query["ip"] = AetherIpVersion.fromString(config.aetherIpVersion).type
        config.aetherDns?.takeIf { it.isNotBlank() }?.let { query["dns"] = it }
        config.aetherExitLoc?.takeIf { it.isNotBlank() }?.let { query["exit_loc"] = it }
        if (protocol.overMasque) {
            query["transport"] = AetherTransport.fromString(config.aetherTransport).type
            if (config.aetherFragment == true) {
                query["fragment"] = "1"
                AetherRange.parse(config.aetherFragmentSize, AetherRange.FRAGMENT_SIZE)
                    ?.let { query["fragment_size"] = it.toString() }
                AetherRange.parse(config.aetherFragmentDelay, AetherRange.FRAGMENT_DELAY)
                    ?.let { query["fragment_delay"] = it.toString() }
            }
            // Only while ECH is in use, with a WARP tunnel, and only what the core would take: the resolver and the
            // domain are kept while ECH is off, as written, and a link carries what runs.
            val warpUsed = AetherPsiphon.fromString(config.aetherPsiphon) != AetherPsiphon.ONLY &&
                AetherTor.fromString(config.aetherTor) != AetherTor.ONLY
            if (config.aetherEch == true && warpUsed) {
                query["ech"] = "1"
                config.aetherEchDns?.trim()?.takeIf { it.isNotEmpty() && isEchDns(it) }?.let { query["ech_dns"] = it }
                config.aetherEchDomain?.trim()?.takeIf { it.isNotEmpty() && isEchDomain(it) }?.let { query["ech_domain"] = it }
            }
            // Chrome's is the default and needs no word.
            AetherFingerprint.fromString(config.aetherFingerprint).takeUnless { it == AetherFingerprint.CHROME }
                ?.let { query["fingerprint"] = it.type }
        }
        if (protocol.twoHops) {
            AetherEndpoint.parse(config.aetherWiwOuter)?.let { query["outer"] = it.toString() }
            AetherEndpoint.parse(config.aetherWiwInner)?.let { query["inner"] = it.toString() }
        }
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        if (psiphon != AetherPsiphon.OFF) {
            query["psiphon"] = psiphon.type
            query["psiphon_mode"] = AetherPsiphonMode.fromString(config.aetherPsiphonMode).type
            config.aetherPsiphonCdnIps?.takeIf { it.isNotBlank() }?.let { query["cdn_ips"] = it }
            config.aetherPsiphonCdnSni?.takeIf { it.isNotBlank() }?.let { query["cdn_sni"] = it }
            config.aetherPsiphonCdnSets?.takeIf { it.isNotBlank() }?.let { query["cdn_sets"] = it }
            config.aetherPsiphonRegion?.takeIf { it.isNotBlank() }?.let { query["region"] = it }
            if (config.aetherPsiphonBundledList == false) query["psiphon_bundled"] = "0"
        }
        val tor = AetherTor.fromString(config.aetherTor)
        if (tor != AetherTor.OFF) {
            query["tor"] = tor.type
            query["tor_bridges"] = AetherTorBridges.fromString(config.aetherTorBridges).type
            query["tor_relays"] = AetherTorRelays.fromString(config.aetherTorRelays).type
            // Bridge lines never hold a semicolon: the core itself separates them with one.
            bridgeLines(config.aetherTorBridgeLines).takeIf { it.isNotEmpty() }?.let { query["bridges"] = it.joinToString(";") }
        }
        config.finalMask?.takeIf { it.isNotBlank() }?.let { query["fm"] = it }
        config.dialMode?.takeIf { it.isNotBlank() }?.let { query["dialMode"] = it }
        val endpoint = AetherEndpoint.of(config.server, config.serverPort).takeUnless { protocol.twoHops }

        val queryText = query.entries.joinToString("&") { "${it.key}=${Utils.encodeURIComponent(it.value)}" }
        return "${endpoint ?: ""}?$queryText#${Utils.encodeURIComponent(config.remarks)}"
    }

    /**
     * [takenPorts] are loopback ports something else of the app listens on, the local proxy above
     * all; the core of the profile cannot listen there as well.
     */
    fun normalize(config: ProfileItem, takenPorts: Set<Int> = emptySet()): Problem? =
        normalizeFragment(config)
            ?: normalizeEndpoints(config)
            ?: normalizeDns(config)
            ?: normalizeExitLoc(config)
            ?: normalizeEch(config)
            ?: normalizePsiphon(config)
            ?: normalizeTor(config)
            ?: normalizeListenPort(config, takenPorts)
            ?: normalizeCommand(config, takenPorts)

    /**
     * The core of a profile built from its settings listens on the Aether listen port of the settings,
     * the one port of every such core, which the local proxy may have been moved onto. A command
     * written by hand names its own ports, which [normalizeCommand] checks.
     */
    private fun normalizeListenPort(config: ProfileItem, takenPorts: Set<Int>): Problem? {
        if (!config.aetherCommand.isNullOrBlank()) return null
        val listen = AetherCoreManager.socksPort
        if (listen in takenPorts) return Problem.LISTEN_PORT_TAKEN
        // Psiphon inside the tunnel, Tor inside it and Tor around it each take one more port after the
        // one the app dials, as AetherCoreManager.buildArguments hands them out.
        val tor = AetherTor.fromString(config.aetherTor)
        val more = listOf(
            AetherPsiphon.fromString(config.aetherPsiphon) == AetherPsiphon.CHAIN,
            tor == AetherTor.CHAIN,
            tor == AetherTor.REVERSE,
        ).count { it }
        if ((1..more).any { listen + it in takenPorts }) return Problem.NEXT_PORT_TAKEN
        return null
    }

    /** The resolvers, each an address with or without a port, as the core reads them; written back comma-separated. */
    private fun normalizeDns(config: ProfileItem): Problem? {
        val resolvers = config.aetherDns.orEmpty().split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
        if (resolvers.any { AetherEndpoint.parse(it) == null && AetherEndpoint.of(it, "53") == null }) return Problem.INVALID_DNS
        config.aetherDns = resolvers.joinToString(",").ifEmpty { null }
        return null
    }

    /** The exit rule as the core reads it: country codes to allow, or with a leading ! to refuse, kept in capitals. */
    private fun normalizeExitLoc(config: ProfileItem): Problem? {
        val rule = config.aetherExitLoc.orEmpty().filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
        if (rule.isEmpty()) {
            config.aetherExitLoc = null
            return null
        }
        if (!exitRule.matches(rule)) return Problem.INVALID_EXIT_LOC
        config.aetherExitLoc = rule
        return null
    }

    private val exitRule = Regex("!?[A-Z]{2}(,[A-Z]{2})*")

    /**
     * Where the ECH key comes from, as the core reads it: the resolver and the domain, kept as written whether ECH is
     * on or off, as the WARP keys page keeps its own, and left out when they are the defaults, so that a profile
     * follows the defaults. They are refused only while ECH is in use, over MASQUE with a WARP tunnel; one the core
     * would not take waits there, out of use, to be put right when ECH is next turned on. None reaches the core
     * then, nor a link, which carries them only while ECH is on.
     */
    private fun normalizeEch(config: ProfileItem): Problem? {
        val dns = config.aetherEchDns?.trim().orEmpty()
        val domain = config.aetherEchDomain?.trim().orEmpty()
        val ech = config.aetherEch == true
        val inUse = ech &&
            AetherProtocol.fromString(config.aetherProtocol).overMasque &&
            AetherPsiphon.fromString(config.aetherPsiphon) != AetherPsiphon.ONLY &&
            AetherTor.fromString(config.aetherTor) != AetherTor.ONLY
        if (inUse && dns.isNotEmpty() && !isEchDns(dns)) return Problem.INVALID_ECH_DNS
        if (inUse && domain.isNotEmpty() && !isEchDomain(domain)) return Problem.INVALID_ECH_DOMAIN
        config.aetherEchDns = dns.takeUnless { it.isEmpty() || it == AppConfig.AETHER_ECH_DNS }
        config.aetherEchDomain = domain.takeUnless { it.isEmpty() || it == AppConfig.AETHER_ECH_DOMAIN }
        return null
    }

    /**
     * Whether [value] names a resolver the core asks for the ECH key, as its --ech-dns takes it: udp:// or tcp:// and
     * an IP address, on port 53 unless one is given, or the https:// URL of a DNS-over-HTTPS server, on port 443
     * unless it names one, with @address= and @sni= after it if need be, see [isDohEndpoint].
     */
    internal fun isEchDns(value: String): Boolean {
        // Quotes would not come back from the command line the editor shows, which is split into words.
        if (value.any { it.isWhitespace() || it == '"' || it == '\'' }) return false
        val scheme = value.substringBefore("://", "").lowercase(Locale.ROOT)
        val rest = value.substringAfter("://", "")
        return when (scheme) {
            "https" -> isDohEndpoint(rest)
            "udp", "tcp" -> rest.trimEnd('/').let { address ->
                // As the core reads an address: ASCII digits only, and brackets around an IPv6 address alone.
                address.all { it.code < 0x80 } &&
                    !(address.startsWith('[') && ':' !in address.substringBefore(']')) &&
                    (AetherEndpoint.parse(address) ?: AetherEndpoint.of(address, "53")) != null
            }
            else -> false
        }
    }

    /**
     * Whether [rest], what follows https://, names a DNS-over-HTTPS server as the core reads one: a URL with a host,
     * then @address= an IP address or a domain name, where the connection goes on the URL's port, and @sni= a domain
     * name, which the ClientHello names, each at most once and in either order. Left out, the connection goes to the
     * URL's host and the ClientHello names it, or names nothing when it is an IP address.
     */
    private fun isDohEndpoint(rest: String): Boolean {
        val pieces = rest.split('@')
        if (pieces.first().takeWhile { it !in "/?#" }.isEmpty()) return false
        val named = mutableSetOf<String>()
        for (piece in pieces.drop(1)) {
            if ('=' !in piece) return false
            val name = piece.substringBefore('=').lowercase(Locale.ROOT)
            val setting = piece.substringAfter('=')
            val isAddress = AetherEndpoint.of(setting, "443") != null
            val fits = when (name) {
                "address" -> isAddress || isEchDomain(setting)
                "sni" -> !isAddress && isEchDomain(setting)
                else -> false
            }
            if (!fits || !named.add(name)) return false
        }
        return true
    }

    /**
     * Whether [value] is a domain whose HTTPS record can be asked for, as the core's --ech-domain takes it. A label
     * cannot start or end with '-' either, so that no value reads as an option of the core's command line.
     */
    internal fun isEchDomain(value: String): Boolean {
        val name = value.removeSuffix(".")
        return name.isNotEmpty() && name.length <= 253 && name.split('.').all { echDomainLabel.matches(it) }
    }

    private val echDomainLabel = Regex("[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?")

    private fun normalizePsiphon(config: ProfileItem): Problem? {
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        if (psiphon == AetherPsiphon.OFF) {
            config.aetherPsiphon = null
            config.aetherPsiphonMode = null
            config.aetherPsiphonCdnIps = null
            config.aetherPsiphonCdnSni = null
            config.aetherPsiphonCdnSets = null
            config.aetherPsiphonRegion = null
            config.aetherPsiphonBundledList = null
            return null
        }
        // Psiphon carries TCP alone and WARP's WireGuard endpoints answer on UDP; the core refuses the pair.
        if (psiphon == AetherPsiphon.REVERSE && !AetherProtocol.fromString(config.aetherProtocol).overMasque) {
            return Problem.PSIPHON_NEEDS_MASQUE
        }
        config.aetherPsiphon = psiphon.type
        config.aetherPsiphonMode = AetherPsiphonMode.fromString(config.aetherPsiphonMode).type
        config.aetherPsiphonCdnIps = commaList(config.aetherPsiphonCdnIps)
        config.aetherPsiphonCdnSni = commaList(config.aetherPsiphonCdnSni)
        config.aetherPsiphonCdnSets = AetherPsiphonCdnSet.join(AetherPsiphonCdnSet.parse(config.aetherPsiphonCdnSets))
        config.aetherPsiphonRegion = config.aetherPsiphonRegion?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null }
        // Stored only when it says no; yes is the default and needs no word.
        config.aetherPsiphonBundledList = config.aetherPsiphonBundledList?.takeUnless { it }
        return null
    }

    private fun normalizeTor(config: ProfileItem): Problem? {
        val tor = AetherTor.fromString(config.aetherTor)
        if (tor == AetherTor.OFF) {
            config.aetherTor = null
            config.aetherTorBridges = null
            config.aetherTorBridgeLines = null
            config.aetherTorRelays = null
            return null
        }
        // Tor carries TCP alone and WARP's WireGuard endpoints answer on UDP; the core refuses the pair.
        if (tor == AetherTor.REVERSE && !AetherProtocol.fromString(config.aetherProtocol).overMasque) {
            return Problem.TOR_NEEDS_MASQUE
        }
        // Tor and Psiphon go together only nested, one inside the tunnel and the other around it: two around
        // it the core refuses, two inside it or one alone leaves the app nothing to dial the other on.
        val psiphon = AetherPsiphon.fromString(config.aetherPsiphon)
        val nested = psiphon == AetherPsiphon.OFF ||
            (tor == AetherTor.CHAIN && psiphon == AetherPsiphon.REVERSE) ||
            (tor == AetherTor.REVERSE && psiphon == AetherPsiphon.CHAIN)
        if (!nested) return Problem.TOR_PSIPHON_CONFLICT
        val bridges = AetherTorBridges.fromString(config.aetherTorBridges)
        val lines = bridgeLines(config.aetherTorBridgeLines)
        if (bridges == AetherTorBridges.OWN && lines.isEmpty()) return Problem.TOR_BRIDGES_MISSING
        config.aetherTor = tor.type
        config.aetherTorBridges = bridges.type
        config.aetherTorBridgeLines = lines.takeIf { bridges == AetherTorBridges.OWN }?.joinToString("\n")
        config.aetherTorRelays = AetherTorRelays.fromString(config.aetherTorRelays).type
        return null
    }

    /**
     * The bridge lines in [text], one per line the way torrc writes them, read as the core reads a
     * bridge file: blank lines and comments dropped, a leading Bridge keyword taken off.
     */
    fun bridgeLines(text: String?): List<String> =
        text.orEmpty().lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.removePrefix("Bridge ").removePrefix("bridge ").trim() }
            .filter { it.isNotEmpty() }

    /** A list as the core reads it, entries separated by commas or spaces, written back with commas alone. */
    private fun commaList(text: String?): String? =
        text?.split(Regex("[,\\s]+"))?.filter { it.isNotEmpty() }?.joinToString(",")?.ifEmpty { null }

    /** A command written in place of the settings has to be one the app can run, on ports nothing else of the app holds. */
    private fun normalizeCommand(config: ProfileItem, takenPorts: Set<Int>): Problem? {
        val text = config.aetherCommand?.trim().orEmpty()
        config.aetherCommand = text.ifEmpty { null }
        if (text.isEmpty()) return null
        val core = AetherCore.ofCommand(text) ?: return Problem.INVALID_COMMAND
        return if (core.ports.any { it in takenPorts }) Problem.LISTEN_PORT_TAKEN else null
    }

    private fun normalizeFragment(config: ProfileItem): Problem? {
        val inUse = AetherProtocol.fromString(config.aetherProtocol).overMasque &&
            AetherTransport.fromString(config.aetherTransport) == AetherTransport.HTTP2 &&
            config.aetherFragment == true
        val sizeText = config.aetherFragmentSize?.trim().orEmpty()
        val delayText = config.aetherFragmentDelay?.trim().orEmpty()
        val size = AetherRange.parse(sizeText, AetherRange.FRAGMENT_SIZE)
        val delay = AetherRange.parse(delayText, AetherRange.FRAGMENT_DELAY)
        if (inUse && (sizeText.isNotEmpty() && size == null || delayText.isNotEmpty() && delay == null)) {
            return Problem.INVALID_FRAGMENT
        }
        config.aetherFragmentSize = size?.toString()
        config.aetherFragmentDelay = delay?.toString()
        return null
    }

    private fun normalizeEndpoints(config: ProfileItem): Problem? {
        if (AetherProtocol.fromString(config.aetherProtocol).twoHops) {
            val outerText = config.aetherWiwOuter?.trim().orEmpty()
            val innerText = config.aetherWiwInner?.trim().orEmpty()
            val outer = AetherEndpoint.parse(outerText)
            val inner = AetherEndpoint.parse(innerText)
            if (outerText.isNotEmpty() && outer == null || innerText.isNotEmpty() && inner == null) {
                return Problem.INVALID_HOP
            }
            if (outer != null && inner != null && outer.host == inner.host) {
                return Problem.SHARED_HOP
            }
            config.aetherWiwOuter = outer?.toString()
            config.aetherWiwInner = inner?.toString()
            config.server = null
            config.serverPort = null
            return null
        }

        val address = config.server?.trim().orEmpty()
        val endpoint = AetherEndpoint.of(address, config.serverPort)
        if (address.isNotEmpty() && endpoint == null) {
            return Problem.INVALID_PEER
        }
        config.server = endpoint?.host
        config.serverPort = endpoint?.port?.toString()
        config.aetherWiwOuter = null
        config.aetherWiwInner = null
        return null
    }
}
