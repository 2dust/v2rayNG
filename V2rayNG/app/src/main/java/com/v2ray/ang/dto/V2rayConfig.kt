package com.v2ray.ang.dto

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType

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
        var settings: InSettingsBean? = null,
        var sniffing: SniffingBean? = null,
        val streamSettings: Any? = null,
        val allocate: Any? = null
    ) {

        data class InSettingsBean(
            var auth: String? = null,
            var accounts: List<AccountBean>? = null,
            var udp: Boolean? = null,
            var accounts: List<AccountBean>? = null, var userLevel: Int? = null,
            var name: String? = null,
            @SerializedName("MTU")
            var mtu: Int? = null
        )

        data class AccountBean(
            var user: String = "",
            var pass: String = ""
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
            var servers: List<ServersBean>? = null,
            /*Blackhole*/
            var response: Response? = null,
            /*DNS*/
            val network: String? = null,
            var address: Any? = null,
            var port: Int? = null,
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
            var version: Int? = null,
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
            var hysteriaSettings: HysteriaSettingsBean? = null,
            var finalmask: Any? = null,
            var dsSettings: Any? = null,
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
                var writeBufferSize: Int = 1
            )

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
                var happyEyeballs: HappyEyeballsBean? = null,
            )

            data class HappyEyeballsBean(
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
                var echConfigList: String? = null,
                var echForceQuery: String? = null,
                var pinnedPeerCertSha256: String? = null,
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

            data class HysteriaSettingsBean(
                var version: Int,
                var auth: String? = null,
                var opaquepPaddings: Boolean? = null,
                var key: String? = null,
                var value: String? = null,
                var obfs: ObfsBean? = null,
                var up_mbps: Int? = null,
                var down_mbps: Int? = null,
                var up: String? = null,
                var down: String? = null,
            ) {
                data class ObfsBean(
                    var type: String? = null,
                    var password: String? = null,
                )
            }

            data class FinalMaskBean(
                var quicParams: QuicParamsBean? = null,
                var udp: List<MaskBean>? = null,
            ) {
                data class QuicParamsBean(
                    var udpHop: UdpHopBean? = null
                ) {
                    data class UdpHopBean(
                        var ports: String? = null,
                        var interval: String? = null
                    )
                }

                data class MaskBean(
                    var type: String? = null,
                    var settings: MaskSettingsBean? = null
                )

                data class MaskSettingsBean(
                    val password: String? = null,
                )
            }
        }

        data class MuxBean(
            var enabled: Boolean = false,
            var concurrency: Int = 8,
            var xudpConcurrency: Int = 16,
            var xudpProxyUDP443: String = "reject"
        )
    }

    data class DnsBean(
        var hosts: Map<String, Any>? = null,
        var servers: List<Any>? = null,
        var tag: String? = null,
        var clientIp: String? = null,
        var queryStrategy: String? = null,
        var disableCache: Boolean? = null,
        var disableFallback: Boolean? = null,
        var disableFallbackIfSmart: Boolean? = null
    )

    data class RoutingBean(
        var domainStrategy: String? = null,
        var domainMatcher: String? = null,
        var rules: ArrayList<RulesBean>
    ) {
        data class RulesBean(
            var type: String = "field",
            var port: String? = null,
            var inboundTag: List<String>? = null,
            var outboundTag: String? = null,
            var ip: List<String>? = null,
            var domain: List<String>? = null,
            var protocol: List<String>? = null,
            var attrs: String? = null,
            var user: List<String>? = null,
            var balancerTag: String? = null
        )
    }

    data class PolicyBean(
        var levels: Map<String, LevelBean>? = null,
        var system: SystemBean? = null
    ) {
        data class LevelBean(
            var handshake: Int? = null,
            var connIdle: Int? = null,
            var uplinkOnly: Int? = null,
            var downlinkOnly: Int? = null,
            var statsUserUplink: Boolean? = null,
            var statsUserDownlink: Boolean? = null,
            var bufferSize: Int? = null
        )

        data class SystemBean(
            var statsInboundUplink: Boolean? = null,
            var statsInboundDownlink: Boolean? = null,
            var statsOutboundUplink: Boolean? = null,
            var statsOutboundDownlink: Boolean? = null
        )
    }

    data class ConfigResult(
        var status: Boolean,
        var content: String = "",
        var guid: String = ""
    )
}
