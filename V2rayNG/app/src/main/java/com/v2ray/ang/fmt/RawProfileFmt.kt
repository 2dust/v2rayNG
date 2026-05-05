package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.engine.CoreSelector
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

data class RawProfileImport(
    val profile: ProfileItem,
    val rawConfig: String,
)

object RawProfileFmt {
    fun createProfile(
        name: String,
        server: String,
        serverPort: Int,
        password: String? = null,
        method: String? = null,
        username: String? = null,
        sni: String? = null,
        alpn: List<String> = emptyList(),
        fingerPrint: String? = null,
        insecure: Boolean? = null,
    ): ProfileItem {
        return ProfileItem.create(EConfigType.CUSTOM).apply {
            remarks = "${CoreSelector.SING_BOX_TEST_PREFIX} $name"
            this.server = server
            this.serverPort = serverPort.toString()
            this.password = password
            this.method = method
            this.username = username
            this.sni = sni
            this.alpn = alpn.joinToString(",").ifBlank { null }
            this.fingerPrint = fingerPrint
            this.insecure = insecure
        }
    }

    fun createRawConfig(outbound: Map<String, Any?>): String? {
        return JsonUtil.toJsonPretty(
            linkedMapOf(
                "log" to linkedMapOf(
                    "level" to "warn",
                    "timestamp" to true,
                ),
                "inbounds" to listOf(
                    linkedMapOf(
                        "type" to "mixed",
                        "tag" to "mixed-in",
                        "listen" to AppConfig.LOOPBACK,
                        "listen_port" to AppConfig.PORT_SOCKS.toInt(),
                        "set_system_proxy" to false,
                    )
                ),
                "outbounds" to listOf(
                    outbound,
                    linkedMapOf(
                        "type" to "direct",
                        "tag" to AppConfig.TAG_DIRECT,
                    )
                ),
                "route" to linkedMapOf(
                    "auto_detect_interface" to true,
                    "final" to AppConfig.TAG_PROXY,
                )
            )
        )
    }
}
