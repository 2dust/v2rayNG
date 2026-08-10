package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import java.net.InetAddress

/**
 * Mirrors the insecure-outbound validation in the Xray-core revision pinned by
 * AndroidLibXrayLite (5ca6f4b7d4dc).
 */
internal object XrayOutboundCompatibility {
    private val tlsProfileTypes = setOf(
        EConfigType.VMESS,
        EConfigType.VLESS,
        EConfigType.SHADOWSOCKS,
        EConfigType.TROJAN,
        EConfigType.HYSTERIA2,
    )

    private val privateIpPrefixes = listOf(
        IpPrefix("0.0.0.0", 8),
        IpPrefix("10.0.0.0", 8),
        IpPrefix("100.64.0.0", 10),
        IpPrefix("127.0.0.0", 8),
        IpPrefix("169.254.0.0", 16),
        IpPrefix("172.16.0.0", 12),
        IpPrefix("192.0.0.0", 24),
        IpPrefix("192.0.2.0", 24),
        IpPrefix("192.88.99.0", 24),
        IpPrefix("192.168.0.0", 16),
        IpPrefix("198.18.0.0", 15),
        IpPrefix("198.51.100.0", 24),
        IpPrefix("203.0.113.0", 24),
        IpPrefix("224.0.0.0", 3),
        IpPrefix("::", 127),
        IpPrefix("fc00::", 7),
        IpPrefix("fe80::", 10),
        IpPrefix("ff00::", 8),
    )

    private val privateDomainSuffixes = setOf(
        "lan",
        "localdomain",
        "example",
        "invalid",
        "localhost",
        "test",
        "local",
        "home.arpa",
        "internal",
    )

    private val dotlessPrivateDomain = Regex("^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$")

    fun isDeprecated(profile: ProfileItem): Boolean {
        if (usesRemovedAllowInsecure(profile)) {
            return true
        }

        if (hasTransportSecurity(profile) || !requiresTransportSecurity(profile.server)) {
            return false
        }

        return when (profile.configType) {
            EConfigType.VLESS -> profile.method.isNullOrEmpty() || profile.method == "none"
            EConfigType.TROJAN -> true
            else -> false
        }
    }

    /** Mirrors the value emitted by CoreOutboundBuilder.populateTlsSettings. */
    private fun usesRemovedAllowInsecure(profile: ProfileItem): Boolean =
        profile.configType in tlsProfileTypes &&
            profile.security == AppConfig.TLS &&
            profile.insecure == true &&
            profile.pinnedCA256.isNullOrEmpty()

    private fun hasTransportSecurity(profile: ProfileItem): Boolean =
        profile.security.equals(AppConfig.TLS, ignoreCase = true) ||
            profile.security.equals(AppConfig.REALITY, ignoreCase = true)

    private fun requiresTransportSecurity(server: String?): Boolean {
        if (server.isNullOrEmpty()) return false
        val address = normalizeAddress(server)

        parseIpLiteral(address)?.let { ip ->
            return privateIpPrefixes.none { it.matches(ip) }
        }

        val domain = address.lowercase().removeSuffix(".")
        val isPrivateDomain = dotlessPrivateDomain.matches(domain) ||
            privateDomainSuffixes.any { suffix ->
                domain == suffix || domain.endsWith(".$suffix")
            }
        return !isPrivateDomain
    }

    /** Mirrors Xray's bracket removal and conditional whitespace trimming. */
    private fun normalizeAddress(server: String): String {
        var address = server
        if (address.startsWith('[') && address.endsWith(']')) {
            address = address.substring(1, address.length - 1)
        }
        if (address.isNotEmpty() && (!address.first().isAsciiAlphaNumeric() || !address.last().isAsciiAlphaNumeric())) {
            address = address.trim()
        }
        return address
    }

    private fun Char.isAsciiAlphaNumeric(): Boolean =
        this in '0'..'9' || this in 'a'..'z' || this in 'A'..'Z'

    private fun parseIpLiteral(address: String): ByteArray? {
        if (':' in address) {
            return runCatching { InetAddress.getByName(address).address }.getOrNull()
        }

        val octets = address.split('.')
        if (octets.size != 4) return null
        val bytes = ByteArray(4)
        octets.forEachIndexed { index, octet ->
            if (octet.isEmpty() || octet.any { !it.isDigit() }) return null
            if (octet.length > 1 && octet.startsWith('0')) return null
            val value = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            bytes[index] = value.toByte()
        }
        return bytes
    }

    private class IpPrefix(address: String, private val prefixLength: Int) {
        private val addressBytes = checkNotNull(parseIpLiteral(address))

        fun matches(candidate: ByteArray): Boolean {
            if (candidate.size != addressBytes.size) return false

            val fullBytes = prefixLength / Byte.SIZE_BITS
            for (index in 0 until fullBytes) {
                if (candidate[index] != addressBytes[index]) return false
            }

            val remainingBits = prefixLength % Byte.SIZE_BITS
            if (remainingBits == 0) return true
            val mask = (0xff shl (Byte.SIZE_BITS - remainingBits)) and 0xff
            return (candidate[fullBytes].toInt() and mask) ==
                (addressBytes[fullBytes].toInt() and mask)
        }
    }
}
