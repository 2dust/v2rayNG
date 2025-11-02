package com.v2ray.ang.dto

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils

data class V2rayConfig(
    var remarks: String? = null,
    var stats: Any? = null,
    val log: LogBean,
    var policy: PolicyBean? = null,
    val inbounds: ArrayList<InboundBean>,
    var outbounds: ArrayList<OutboundBean>,
    var dns: DnsBean? = null,
    val routing: RoutingBean,
    val api: Any? = null,
    val transport: Any? = null,
    val reverse: Any? = null,
    var fakedns: Any? = null,
    val browserForwarder: Any? = null,
    var observatory: Any? = null,
    var burstObservatory: Any? = null
) {

    data class LogBean(
        val access: String? = null,
        val error: String? = null,
        var loglevel: String? = null,
        val dnsLog: Boolean? = null
    )

    data class InboundBean(
        var tag: String,
        var port: Int,
        var protocol: String,
        var listen: String? = null,
        val settings: Any? = null,
        val sniffing: SniffingBean? = null,
        val streamSettings: Any? = null,
        val allocate: Any? = null
    ) {

        data class InSettingsBean(
            val auth: String? = null,
            val udp: Boolean? = null,
            val userLevel: Int? = null,
            val address: String? = null,
            val port: Int? = null,
            val network: String? = null
        )

        data class SniffingBean(
            var enabled: Boolean,
            val destOverride: ArrayList<String>,
            val metadataOnly: Boolean? = null,
            var routeOnly: Boolean? = null
        )
    }

    data class OutboundBean(
        var tag: String = "proxy",
        var protocol: String,
        var settings: OutSettingsBean? = null,
        var streamSettings: StreamSettingsBean? = null,
        val proxySettings: Any? = null,
        val sendThrough: String? = null,
        var mux: MuxBean? = MuxBean(false)
    ) {
        data class OutSettingsBean(
            var vnext: List<VnextBean>? = null,
            var fragment: FragmentBean? = null,
            var noises: List<NoiseBean>? = null,
            var servers: List<ServersBean>? = null,
            /*Blackhole*/
            var response: Response? = null,
            /*DNS*/
            val network: String? = null,
            var address: Any? = null,
            val port: Int? = null,
            /*Freedom*/
            var domainStrategy: String? = null,
            val redirect: String? = null,
            val userLevel: Int? = null,
            /*Loopback*/
            val inboundTag: String? = null,
            /*Wireguard*/
            var secretKey: String? = null,
            val peers: List<WireGuardBean>? = null,
            var reserved: List<Int>? = null,
            var mtu: Int? = null,
            var obfsPassword: String? = null,
        ) {

            data class VnextBean(
                var address: String = "",
                var port: Int = AppConfig.DEFAULT_PORT,
                var users: List<UsersBean>
            ) {

                data class UsersBean(
                    var id: String = "",
                    var alterId: Int? = null,
                    var security: String? = null,
                    var level: Int = AppConfig.DEFAULT_LEVEL,
                    var encryption: String? = null,
                    var flow: String? = null
                )
            }

            data class FragmentBean(
                var packets: String? = null,
                var length: String? = null,
                var interval: String? = null
            )

            data class NoiseBean(
                var type: String? = null,
                var packet: String? = null,
                var delay: String? = null
            )

            data class ServersBean(
                var address: String = "",
                var method: String? = null,
                var ota: Boolean = false,
                var password: String? = null,
                var port: Int = AppConfig.DEFAULT_PORT,
                var level: Int = AppConfig.DEFAULT_LEVEL,
                val email: String? = null,
                var flow: String? = null,
                val ivCheck: Boolean? = null,
                var users: List<SocksUsersBean>? = null
            ) {
                data class SocksUsersBean(
                    var user: String = "",
                    var pass: String = "",
                    var level: Int = AppConfig.DEFAULT_LEVEL
                )
            }

            data class Response(var type: String)

            data class WireGuardBean(
                var publicKey: String = "",
                var preSharedKey: String? = null,
                var endpoint: String = ""
            )
        }

        data class StreamSettingsBean(
            var network: String = AppConfig.DEFAULT_NETWORK,
            var security: String? = null,
            var tcpSettings: TcpSettingsBean? = null,
            var kcpSettings: KcpSettingsBean? = null,
            var wsSettings: WsSettingsBean? = null,
            var httpupgradeSettings: HttpupgradeSettingsBean? = null,
            var xhttpSettings: XhttpSettingsBean? = null,
            var httpSettings: HttpSettingsBean? = null,
            var tlsSettings: TlsSettingsBean? = null,
            var quicSettings: QuicSettingBean? = null,
            var realitySettings: TlsSettingsBean? = null,
            var grpcSettings: GrpcSettingsBean? = null,
            var hy2steriaSettings: Hy2steriaSettingsBean? = null,
            val dsSettings: Any? = null,
            var sockopt: SockoptBean? = null
        ) {

            data class TcpSettingsBean(
                var header: HeaderBean = HeaderBean(),
                val acceptProxyProtocol: Boolean? = null
            ) {
                data class HeaderBean(
                    var type: String = "none",
                    var request: RequestBean? = null,
                    var response: Any? = null
                ) {
                    data class RequestBean(
                        var path: List<String> = ArrayList(),
                        var headers: HeadersBean = HeadersBean(),
                        val version: String? = null,
                        val method: String? = null
                    ) {
                        data class HeadersBean(
                            var Host: List<String>? = ArrayList(),
                            @SerializedName("User-Agent")
                            val userAgent: List<String>? = null,
                            @SerializedName("Accept-Encoding")
                            val acceptEncoding: List<String>? = null,
                            val Connection: List<String>? = null,
                            val Pragma: String? = null
                        )
                    }
                }
            }

            data class KcpSettingsBean(
                var mtu: Int = 1350,
                var tti: Int = 50,
                var uplinkCapacity: Int = 12,
                var downlinkCapacity: Int = 100,
                var congestion: Boolean = false,
                var readBufferSize: Int = 1,
                var writeBufferSize: Int = 1,
                var header: HeaderBean = HeaderBean(),
                var seed: String? = null
            ) {
                data class HeaderBean(
                    var type: String = "none",
                    var domain: String? = null
                )
            }

            data class WsSettingsBean(
                var path: String? = null,
                var headers: HeadersBean = HeadersBean(),
                val maxEarlyData: Int? = null,
                val useBrowserForwarding: Boolean? = null,
                val acceptProxyProtocol: Boolean? = null
            ) {
                data class HeadersBean(var Host: String = "")
            }

            data class HttpupgradeSettingsBean(
                var path: String? = null,
                var host: String? = null,
                val acceptProxyProtocol: Boolean? = null
            )

            data class XhttpSettingsBean(
                var path: String? = null,
                var host: String? = null,
                var mode: String? = null,
                var extra: Any? = null,
            )

            data class HttpSettingsBean(
                var host: List<String> = ArrayList(),
                var path: String? = null
            )

            data class SockoptBean(
                var TcpNoDelay: Boolean? = null,
                var tcpKeepAliveIdle: Int? = null,
                var tcpFastOpen: Boolean? = null,
                var tproxy: String? = null,
                var mark: Int? = null,
                var dialerProxy: String? = null,
                var domainStrategy: String? = null,
                var happyEyeballs: happyEyeballsBean? = null,
                )
            data class happyEyeballsBean(
                var prioritizeIPv6: Boolean? = null,
                var maxConcurrentTry: Int? = 4,
                var tryDelayMs: Int? = 250, // ms
                var interleave: Int? = null,
            )

            data class TlsSettingsBean(
                var allowInsecure: Boolean = false,
                var serverName: String? = null,
                val alpn: List<String>? = null,
                val minVersion: String? = null,
                val maxVersion: String? = null,
                val preferServerCipherSuites: Boolean? = null,
                val cipherSuites: String? = null,
                val fingerprint: String? = null,
                val certificates: List<Any>? = null,
                val disableSystemRoot: Boolean? = null,
                val enableSessionResumption: Boolean? = null,
                // REALITY settings
                val show: Boolean = false,
                var publicKey: String? = null,
                var shortId: String? = null,
                var spiderX: String? = null,
                var mldsa65Verify: String? = null
            )

            data class QuicSettingBean(
                var security: String = "none",
                var key: String = "",
                var header: HeaderBean = HeaderBean()
            ) {
                data class HeaderBean(var type: String = "none")
            }

            data class GrpcSettingsBean(
                var serviceName: String = "",
                var authority: String? = null,
                var multiMode: Boolean? = null,
                var idle_timeout: Int? = null,
                var health_check_timeout: Int? = null
            )

            data class Hy2steriaSettingsBean(
                var password: String? = null,
                var use_udp_extension: Boolean? = true,
                var congestion: Hy2CongestionBean? = null
            ) {
                data class Hy2CongestionBean(
                    var type: String? = "bbr",
                    var up_mbps: Int? = null,
                    var down_mbps: Int? = null,
                )
            }

        }

        data class MuxBean(
            var enabled: Boolean,
            var concurrency: Int? = null,
            var xudpConcurrency: Int? = null,
            var xudpProxyUDP443: String? = null,
        )

        fun getServerAddress(): String? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                || protocol.equals(EConfigType.VLESS.name, true)
            ) {
                return settings?.vnext?.first()?.address
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
            ) {
                return settings?.servers?.first()?.address
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.peers?.first()?.endpoint?.substringBeforeLast(":")
            }
            return null
        }

        fun getServerPort(): Int? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                || protocol.equals(EConfigType.VLESS.name, true)
            ) {
                return settings?.vnext?.first()?.port
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
            ) {
                return settings?.servers?.first()?.port
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.peers?.first()?.endpoint?.substringAfterLast(":")?.toInt()
            }
            return null
        }

        fun getServerAddressAndPort(): String {
            val address = getServerAddress().orEmpty()
            val port = getServerPort()
            return Utils.getIpv6Address(address) + ":" + port
        }

        fun getPassword(): String? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                || protocol.equals(EConfigType.VLESS.name, true)
            ) {
                return settings?.vnext?.first()?.users?.first()?.id
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
            ) {
                return settings?.servers?.first()?.password
            } else if (protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
            ) {
                return settings?.servers?.first()?.users?.first()?.pass
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.secretKey
            }
            return null
        }

        fun getSecurityEncryption(): String? {
            return when {
                protocol.equals(EConfigType.VMESS.name, true) -> settings?.vnext?.first()?.users?.first()?.security
                protocol.equals(EConfigType.VLESS.name, true) -> settings?.vnext?.first()?.users?.first()?.encryption
                protocol.equals(EConfigType.SHADOWSOCKS.name, true) -> settings?.servers?.first()?.method
                else -> null
            }
        }

        fun getTransportSettingDetails(): List<String?>? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                || protocol.equals(EConfigType.VLESS.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.SHADOWSOCKS.name, true)
            ) {
                val transport = streamSettings?.network ?: return null
                return when (transport) {
                    NetworkType.TCP.type -> {
                        val tcpSetting = streamSettings?.tcpSettings ?: return null
                        listOf(
                            tcpSetting.header.type,
                            tcpSetting.header.request?.headers?.Host?.joinToString(",").orEmpty(),
                            tcpSetting.header.request?.path?.joinToString(",").orEmpty()
                        )
                    }

                    NetworkType.KCP.type -> {
                        val kcpSetting = streamSettings?.kcpSettings ?: return null
                        listOf(
                            kcpSetting.header.type,
                            "",
                            kcpSetting.seed.orEmpty()
                        )
                    }

                    NetworkType.WS.type -> {
                        val wsSetting = streamSettings?.wsSettings ?: return null
                        listOf(
                            "",
                            wsSetting.headers.Host,
                            wsSetting.path
                        )
                    }

                    NetworkType.HTTP_UPGRADE.type -> {
                        val httpupgradeSetting = streamSettings?.httpupgradeSettings ?: return null
                        listOf(
                            "",
                            httpupgradeSetting.host,
                            httpupgradeSetting.path
                        )
                    }

                    NetworkType.XHTTP.type -> {
                        val xhttpSettings = streamSettings?.xhttpSettings ?: return null
                        listOf(
                            "",
                            xhttpSettings.host,
                            xhttpSettings.path
                        )
                    }

                    NetworkType.H2.type -> {
                        val h2Setting = streamSettings?.httpSettings ?: return null
                        listOf(
                            "",
                            h2Setting.host.joinToString(","),
                            h2Setting.path
                        )
                    }

//                    "quic" -> {
//                        val quicSetting = streamSettings?.quicSettings ?: return null
//                        listOf(
//                            quicSetting.header.type,
//                            quicSetting.security,
//                            quicSetting.key
//                        )
//                    }

                    NetworkType.GRPC.type -> {
                        val grpcSetting = streamSettings?.grpcSettings ?: return null
                        listOf(
                            if (grpcSetting.multiMode == true) "multi" else "gun",
                            grpcSetting.authority.orEmpty(),
                            grpcSetting.serviceName
                        )
                    }

                    else -> null
                }
            }
            return null
        }

        fun ensureSockopt(): V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean {
            val stream = streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean().also {
                streamSettings = it
            }

            val sockopt = stream.sockopt ?: V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean().also {
                stream.sockopt = it
            }

            return sockopt
        }
    }

    data class DnsBean(
        var servers: ArrayList<Any>? = null,
        var hosts: Map<String, Any>? = null,
        val clientIp: String? = null,
        val disableCache: Boolean? = null,
        val queryStrategy: String? = null,
        val tag: String? = null
    ) {
        data class ServersBean(
            var address: String = "",
            var port: Int? = null,
            var domains: List<String>? = null,
            var expectIPs: List<String>? = null,
            val clientIp: String? = null,
            val skipFallback: Boolean? = null,
            val tag: String? = null,
        )
    }

    data class RoutingBean(
        var domainStrategy: String,
        var domainMatcher: String? = null,
        var rules: ArrayList<RulesBean>,
        var balancers: List<BalancerBean>? = null
    ) {

        data class RulesBean(
            var type: String = "field",
            var ip: ArrayList<String>? = null,
            var domain: ArrayList<String>? = null,
            var outboundTag: String? = null,
            var balancerTag: String? = null,
            var port: String? = null,
            val sourcePort: String? = null,
            val network: String? = null,
            val source: List<String>? = null,
            val user: List<String>? = null,
            var inboundTag: List<String>? = null,
            val protocol: List<String>? = null,
            val attrs: String? = null,
            val domainMatcher: String? = null
        )

        data class BalancerBean(
            val tag: String,
            val selector: List<String>,
            val fallbackTag: String? = null,
            val strategy: StrategyObject? = null
        )

        data class StrategyObject(
            val type: String = "random", // "random" | "roundRobin" | "leastPing" | "leastLoad"
            val settings: StrategySettingsObject? = null
        )

        data class StrategySettingsObject(
            val expected: Int? = null,
            val maxRTT: String? = null,
            val tolerance: Double? = null,
            val baselines: List<String>? = null,
            val costs: List<CostObject>? = null
        )

        data class CostObject(
            val regexp: Boolean = false,
            val match: String,
            val value: Double
        )
    }

    data class PolicyBean(
        var levels: Map<String, LevelBean>,
        var system: Any? = null
    ) {
        data class LevelBean(
            var handshake: Int? = null,
            var connIdle: Int? = null,
            var uplinkOnly: Int? = null,
            var downlinkOnly: Int? = null,
            val statsUserUplink: Boolean? = null,
            val statsUserDownlink: Boolean? = null,
            var bufferSize: Int? = null
        )
    }

    data class ObservatoryObject(
        val subjectSelector: List<String>,
        val probeUrl: String,
        val probeInterval: String,
        val enableConcurrency: Boolean = false
    )

    data class BurstObservatoryObject(
        val subjectSelector: List<String>,
        val pingConfig: PingConfigObject
    ) {
        data class PingConfigObject(
            val destination: String,
            val connectivity: String? = null,
            val interval: String,
            val sampling: Int,
            val timeout: String? = null
        )
    }

    data class FakednsBean(
        var ipPool: String = "198.18.0.0/15",
        var poolSize: Int = 10000
    ) // roughly 10 times smaller than total ip pool

    fun getProxyOutbound(): OutboundBean? {
        outbounds.forEach { outbound ->
            EConfigType.entries.forEach {
                if (outbound.protocol.equals(it.name, true)) {
                    return outbound
                }
            }
        }
        return null
    }

    fun getAllProxyOutbound(): List<OutboundBean> {
        return outbounds.filter { outbound ->
            EConfigType.entries.any { it.name.equals(outbound.protocol, ignoreCase = true) }
        }
    }
}
