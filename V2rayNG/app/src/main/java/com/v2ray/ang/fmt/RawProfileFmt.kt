package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.engine.CoreSelector
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

data class RawProfileImport(
    val profile: ProfileItem,
    val rawConfig: String,
)

object RawProfileFmt {
    private const val TAG_HOSTS_DNS = "hosts_dns"
    private const val TAG_DIRECT_DNS = "direct_dns"
    private const val TAG_REMOTE_DNS = "remote_dns"

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
        val outboundServer = outbound["server"] as? String
        val outboundDomain = outboundServer?.takeIf { it.isNotBlank() && !Utils.isPureIpAddress(it) }
        val directDnsDomains = listOf(
            "alidns.com",
            "doh.pub",
            "dot.pub",
            "onedns.net",
            "dns.alidns.com",
        )
        val dnsRules = mutableListOf<Map<String, Any?>>().apply {
            if (outboundDomain != null) {
                add(
                    linkedMapOf(
                        "action" to "route",
                        "server" to TAG_DIRECT_DNS,
                        "domain" to listOf(outboundDomain),
                    )
                )
            }
            add(
                linkedMapOf(
                    "action" to "route",
                    "server" to TAG_DIRECT_DNS,
                    "domain_suffix" to directDnsDomains,
                )
            )
        }
        val routeRules = mutableListOf<Map<String, Any?>>(
            linkedMapOf(
                "action" to "sniff",
            ),
            linkedMapOf(
                "protocol" to listOf("dns"),
                "action" to "hijack-dns",
            ),
            linkedMapOf(
                "ip_is_private" to true,
                "action" to "route",
                "outbound" to AppConfig.TAG_DIRECT,
            ),
            linkedMapOf(
                "domain_suffix" to directDnsDomains,
                "action" to "route",
                "outbound" to AppConfig.TAG_DIRECT,
            ),
        )

        return JsonUtil.toJsonPretty(
            linkedMapOf(
                "log" to linkedMapOf(
                    "level" to "warn",
                    "timestamp" to true,
                ),
                "dns" to linkedMapOf(
                    "servers" to listOf(
                        linkedMapOf(
                            "type" to "hosts",
                            "tag" to TAG_HOSTS_DNS,
                            "predefined" to linkedMapOf(
                                "cloudflare-dns.com" to listOf("104.16.249.249", "104.16.248.249"),
                                "one.one.one.one" to listOf("1.1.1.1", "1.0.0.1"),
                                "dns.google" to listOf("8.8.8.8", "8.8.4.4"),
                                "dns.alidns.com" to listOf("223.5.5.5", "223.6.6.6"),
                            ),
                        ),
                        linkedMapOf(
                            "type" to "udp",
                            "tag" to TAG_DIRECT_DNS,
                            "server" to AppConfig.DNS_DIRECT,
                        ),
                        linkedMapOf(
                            "type" to "https",
                            "tag" to TAG_REMOTE_DNS,
                            "server" to "cloudflare-dns.com",
                            "path" to "/dns-query",
                            "domain_resolver" to TAG_HOSTS_DNS,
                            "detour" to AppConfig.TAG_PROXY,
                        ),
                    ),
                    "rules" to dnsRules,
                    "final" to TAG_REMOTE_DNS,
                    "independent_cache" to true,
                    "strategy" to "prefer_ipv4",
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
                    "default_domain_resolver" to linkedMapOf(
                        "server" to TAG_DIRECT_DNS,
                        "strategy" to "prefer_ipv4",
                    ),
                    "rules" to routeRules,
                    "final" to AppConfig.TAG_PROXY,
                )
            )
        )
    }
}
