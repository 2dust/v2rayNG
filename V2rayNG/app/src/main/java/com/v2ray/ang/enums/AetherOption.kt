package com.v2ray.ang.enums

import java.util.Locale

enum class AetherProtocol(val type: String) {
    MASQUE("masque"),
    WIREGUARD("wg"),
    GOOL("gool"),
    MIM("mim");

    /** Whether MASQUE carries the tunnel, which then uses the MASQUE transport, fragmentation and key. */
    val overMasque: Boolean get() = this == MASQUE || this == MIM

    /** Whether the tunnel is two hops, an outer and an inner one, in place of one endpoint. */
    val twoHops: Boolean get() = this == GOOL || this == MIM

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: WIREGUARD
    }
}

enum class AetherTransport(val type: String) {
    HTTP3("h3"),
    HTTP2("h2");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: HTTP3
    }
}

enum class AetherScanMode(val type: String) {
    TURBO("turbo"),
    BALANCED("balanced"),
    THOROUGH("thorough"),
    VERIFIED("verified"),
    IRONCLAD("ironclad");

    companion object {
        /** The name the verified mode had before aether 2.1; profiles, links and configurations from then still carry it. */
        const val STEALTH = "stealth"

        fun fromString(type: String?) = entries.find { it.type == type } ?: if (type == STEALTH) VERIFIED else BALANCED
    }
}

/**
 * The obfuscation profile, named the way the core names it. [AUTO] leaves the choice to the core,
 * which takes firewall for MASQUE and balanced for WireGuard and gool.
 */
enum class AetherObfuscation(val type: String) {
    AUTO("auto"),
    OFF("off"),
    LIGHT("light"),
    FIREWALL("firewall"),
    BALANCED("balanced"),
    GFW("gfw"),
    AGGRESSIVE("aggressive");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/**
 * The IP versions the core scans and connects over; IPv4 unless a profile says otherwise, so that a
 * profile connects on an IPv4-only network and on a dual-stack one alike.
 */
enum class AetherIpVersion(val type: String) {
    V4("v4"),
    V6("v6"),
    DUAL("both");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: V4
    }
}

/** Where Psiphon stands in the tunnel of a profile, named the way the core names it. */
enum class AetherPsiphon(val type: String) {
    OFF("off"),

    /** The tunnel carries Psiphon: the app dials Psiphon, which leaves through WARP. */
    CHAIN("chain"),

    /** Psiphon carries the tunnel: WARP is reached through Psiphon, and the app dials WARP. */
    REVERSE("reverse"),

    /** No WARP at all: the app dials Psiphon itself. */
    ONLY("only");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: OFF
    }
}

/**
 * The lists of CDN edges built into Psiphon's client, named the way its config names them and in
 * the order the app hands them over, which is the order the scan tries them.
 */
enum class AetherPsiphonCdnSet(val type: String) {
    CLOUDFLARE("cloudflare"),
    FASTLY("fastly"),
    CLOUDFRONT("cloudfront"),
    AKAMAI("psiphon-akamai"),
    BUNNY("psiphon-bunny"),
    VERCEL("vercel"),
    GITHUB("github"),
    CURATED("curated-fronting"),
    LEGACY("legacy-android-overrides");

    companion object {
        /** The sets named in [text], a comma or space separated list: in the order above, once each, strangers left out. */
        fun parse(text: String?): List<AetherPsiphonCdnSet> {
            val named = text.orEmpty().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.toSet()
            return entries.filter { it.type in named }
        }

        /** [sets] as the core and the profile take them, comma separated; null for none. */
        fun join(sets: Collection<AetherPsiphonCdnSet>): String? =
            entries.filter { it in sets }.joinToString(",") { it.type }.ifEmpty { null }
    }
}

/** How Psiphon reaches its servers. */
enum class AetherPsiphonMode(val type: String) {
    AUTO("auto"),
    CDN("cdn"),
    DIRECT("direct");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/** Where Tor stands in the tunnel of a profile, named the way the core names it: the same three places as [AetherPsiphon]. */
enum class AetherTor(val type: String) {
    OFF("off"),

    /** The tunnel carries Tor: the app dials Tor, which leaves through WARP, so the network never sees Tor. */
    CHAIN("chain"),

    /** Tor carries the tunnel: WARP is reached from a Tor exit, and the app dials WARP. */
    REVERSE("reverse"),

    /** No WARP at all: the app dials Tor itself. */
    ONLY("only");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: OFF
    }
}

/** When Tor turns to bridges. */
enum class AetherTorBridges(val type: String) {
    /** Tor is tried plainly first, and bridges are fetched when that gets nowhere. */
    AUTO("auto"),

    /** Bridges from the start, without trying Tor plainly. */
    FIRST("first"),

    /** Never, however blocked the network looks. */
    NEVER("never"),

    /** The bridge lines of the profile, and no other. */
    OWN("own");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/** Where Tor's fetched bridges come from: bridgedb, the public relays onionoo lists used as plain bridges, or both. */
enum class AetherTorRelays(val type: String) {
    /** bridgedb and the relays together, the core's own choice. */
    AUTO("auto"),

    /** The relays alone; bridgedb hands out few bridges, and they are blocked early. */
    ONLY("only"),

    /** bridgedb alone. */
    OFF("off");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: AUTO
    }
}

/**
 * Which WARP keys a registration gets, named the way the core's --register names them: every key, or the keys of one
 * protocol, both hops' keys for a two-hop one.
 */
enum class AetherKeyKind(val type: String) {
    ALL("all"),
    WIREGUARD("wg"),
    MASQUE("masque"),
    GOOL("gool"),
    MIM("mim");

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: ALL

        /** The kind the core reads [word] after --register as, under any of the names it accepts; null for none. */
        fun ofRegister(word: String?): AetherKeyKind? = when (word?.trim()?.lowercase(Locale.ROOT)) {
            "all" -> ALL
            "wg", "wireguard", "warp" -> WIREGUARD
            "masque" -> MASQUE
            "gool", "wiw", "warp-in-warp" -> GOOL
            "mim", "masque-in-masque" -> MIM
            else -> null
        }
    }
}

/**
 * The cipher suites the TLS handshakes of the core offer, after a client whose ClientHello was captured: its TLS 1.2
 * suites as BoringSSL names them, in its order, or Chrome's own rule, and whether it sends GREASE values. BoringSSL
 * writes its TLS 1.3 suites first, in an order of its own, so only the TLS 1.2 part follows the client.
 */
enum class AetherFingerprint(val type: String, val ciphers: String, val grease: Boolean) {
    /**
     * Chrome's own rule rather than a list, which BoringSSL orders as it does for Chrome, by the phone's AES
     * instructions as the TLS 1.3 suites: c02b c02f c02c c030 cca9 cca8 c013 c014 009c 009d 002f 0035 with them,
     * ChaCha20 first without. A fixed list would keep AES-GCM first there while the TLS 1.3 suites go ChaCha20 first, a
     * ClientHello no Chrome sends. Named, though it is the core's default as well, so that it stays Chrome's whatever
     * the core's default becomes. After a GREASE value.
     */
    CHROME(
        "chrome",
        "ALL:!aPSK:!ECDSA+SHA1:!3DES",
        true,
    ),

    /** c02b c02f cca9 cca8 c02c c030 c013 c014 009c 009d 002f 0035. */
    FIREFOX(
        "firefox",
        "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:" +
            "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:" +
            "AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA:AES256-SHA",
        false,
    ),

    /**
     * c02c c030 c02b c02f cca9 cca8 c00a c014 c023 c027: Python's first ten TLS 1.2 suites, with c00a and c014
     * (AES-256-CBC with SHA-1) in place of c024 and c028 (with SHA-384), the nearest ones BoringSSL has; Python's
     * four DHE suites after them are left out, as BoringSSL has none. Hence semi-python: not Python's list as it is,
     * but every suite of it a core on boring 5.2 or newer takes; an older one refuses c023.
     */
    SEMI_PYTHON(
        "semi-python",
        "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:" +
            "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA:" +
            "ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA256",
        false,
    ),

    /** c02b c02f c02c c030 cca9 cca8 c009 c013 c00a c014; Go lists its TLS 1.3 suites after them. */
    GO(
        "go",
        "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:" +
            "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES128-SHA:" +
            "ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA",
        false,
    );

    /** The core's options for this fingerprint: its TLS 1.2 suites, and GREASE left out where it sends none. */
    val arguments: List<String>
        get() = listOf("--tls-ciphers", ciphers) + if (grease) emptyList() else listOf("--disable-grease")

    companion object {
        fun fromString(type: String?) = entries.find { it.type == type } ?: CHROME
    }
}
