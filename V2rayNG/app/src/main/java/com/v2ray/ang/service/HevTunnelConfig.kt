package com.v2ray.ang.service

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

internal data class HevTunnelSettings(
    val tcpTimeoutSeconds: Int,
    val udpTimeoutSeconds: Int,
    val logLevel: String,
) {
    companion object {
        fun current(): HevTunnelSettings {
            val timeouts = parseTimeouts(
                MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT),
            )
            return HevTunnelSettings(
                tcpTimeoutSeconds = timeouts.first,
                udpTimeoutSeconds = timeouts.second,
                logLevel = MmkvManager.decodeSettingsString(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL)
                    ?: DEFAULT_LOG_LEVEL,
            )
        }

        internal fun parseTimeouts(value: String?): Pair<Int, Int> {
            val parts = (value ?: AppConfig.HEVTUN_RW_TIMEOUT).split(',').map { it.trim() }
            return Pair(
                parts.getOrNull(0)?.toIntOrNull() ?: DEFAULT_TCP_TIMEOUT_SECONDS,
                parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_UDP_TIMEOUT_SECONDS,
            )
        }

        private const val DEFAULT_TCP_TIMEOUT_SECONDS = 300
        private const val DEFAULT_UDP_TIMEOUT_SECONDS = 60
        private const val DEFAULT_LOG_LEVEL = "warn"
    }
}

internal data class HevTunnelParameters(
    val mtu: Int,
    val ipv4: String,
    val ipv6: String? = null,
    val socksAddress: String,
    val socksPort: Int,
    val socksUsername: String? = null,
    val socksPassword: String? = null,
    val settings: HevTunnelSettings,
)

internal object HevTunnelConfig {
    fun build(parameters: HevTunnelParameters): String = buildString {
        appendLine("tunnel:")
        appendLine("  mtu: ${parameters.mtu}")
        appendLine("  ipv4: '${parameters.ipv4.yamlSingleQuoted()}'")
        parameters.ipv6?.let { appendLine("  ipv6: '${it.yamlSingleQuoted()}'") }
        appendLine("socks5:")
        appendLine("  port: ${parameters.socksPort}")
        appendLine("  address: '${parameters.socksAddress.yamlSingleQuoted()}'")
        appendLine("  udp: 'udp'")
        if (parameters.socksUsername != null && parameters.socksPassword != null) {
            appendLine("  username: '${parameters.socksUsername.yamlSingleQuoted()}'")
            appendLine("  password: '${parameters.socksPassword.yamlSingleQuoted()}'")
        }
        appendLine("misc:")
        appendLine("  tcp-read-write-timeout: ${parameters.settings.tcpTimeoutSeconds * 1000}")
        appendLine("  udp-read-write-timeout: ${parameters.settings.udpTimeoutSeconds * 1000}")
        appendLine("  log-level: '${parameters.settings.logLevel.yamlSingleQuoted()}'")
    }

    private fun String.yamlSingleQuoted(): String = replace("'", "''")
}
