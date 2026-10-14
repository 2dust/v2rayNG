package com.v2ray.ang.dto.entities

import java.net.URI

/** User settings only; the native core owns its WARP identity in private storage. */
data class AetherSettings(
    val protocol: String = "masque",
    val carrier: String = "h2",
    val scanMode: String = "balanced",
    val ipVersion: String = "v4",
    val noise: String = "firewall",
    val endpoint: String = "",
    val dns: String = "1.1.1.1,1.0.0.1",
    val fragment: Boolean = true,
    val fragmentSize: String = "16-32",
    val fragmentDelay: String = "2-10",
    // Retained for existing profile JSON and aether:// links; runtime port selection ignores it.
    val socksPort: String = "1819",
) {
    fun isValid(): Boolean =
        protocol in listOf("masque", "wg") &&
            carrier in listOf("h2", "h3") &&
            scanMode in listOf("turbo", "balanced", "thorough", "verified", "ironclad") &&
            ipVersion in listOf("v4", "v6", "both") &&
            noise in listOf("off", "light", "firewall", "balanced", "gfw", "aggressive") &&
            (endpoint.isBlank() || validEndpoint(endpoint)) &&
            dns.split(',').let { it.isNotEmpty() && it.all { ip -> validIp(ip.trim()) } } &&
            validRange(fragmentSize, 1, 16384) && validRange(fragmentDelay, 0, 1000)

    fun arguments(identityPath: String, bindPort: Int): List<String> {
        require(isValid()) { "Invalid Aether settings" }
        require(bindPort in 1024..65535) { "Invalid runtime proxy port" }
        return buildList {
            addAll(listOf("--protocol", protocol, "--scan", scanMode, "--ip", ipVersion,
                "--noize", noise, "--dns", dns, "--bind", "127.0.0.1:$bindPort",
                "--config", identityPath))
            add(if (carrier == "h2") "--h2" else "--h3")
            add(if (fragment) "--fragment" else "--no-fragment")
            addAll(listOf("--fragment-size", fragmentSize, "--fragment-delay", fragmentDelay))
            if (endpoint.isNotBlank()) addAll(listOf("--peer", endpoint.trim()))
        }
    }

    private fun validEndpoint(text: String): Boolean = runCatching {
        val uri = URI("tcp://$text")
        uri.port in 1..65535 && uri.rawUserInfo == null && uri.rawQuery == null &&
            uri.rawFragment == null && uri.rawPath.orEmpty().isEmpty() &&
            validIp(uri.host.orEmpty().removeSurrounding("[", "]"))
    }.getOrDefault(false)

    private fun validIp(text: String): Boolean {
        if (text.contains(':')) return runCatching {
            URI("tcp://[$text]:53").host != null
        }.getOrDefault(false)
        val parts = text.split('.')
        return parts.size == 4 && parts.all {
            it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull()?.let { n -> n in 0..255 } == true)
        }
    }

    private fun validRange(text: String, min: Int, max: Int): Boolean {
        val parts = text.split('-')
        if (parts.size !in 1..2) return false
        val first = parts.first().toIntOrNull() ?: return false
        val last = parts.last().toIntOrNull() ?: return false
        return first in min..max && last in first..max
    }
}
