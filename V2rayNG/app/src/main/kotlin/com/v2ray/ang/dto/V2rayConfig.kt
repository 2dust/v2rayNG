package com.v2ray.ang.dto

data class V2rayConfig(
        val stats: Any?=null,
        val log: LogBean,
        val policy: PolicyBean,
        val inbounds: ArrayList<InboundBean>,
        var outbounds: ArrayList<OutboundBean>,
        var dns: DnsBean,
        val routing: RoutingBean) {

    data class LogBean(val access: String,
                       val error: String,
                       val loglevel: String)

    data class InboundBean(
            var tag: String,
            var port: Int,
            var protocol: String,
            var listen: String?=null,
            val settings: InSettingsBean,
            val sniffing: SniffingBean?) {

        data class InSettingsBean(val auth: String? = null,
                                  val udp: Boolean? = null,
                                  val userLevel: Int? =null,
                                  val address: String? = null,
                                  val port: Int? = null,
                                  val network: String? = null)

        data class SniffingBean(var enabled: Boolean,
                                val destOverride: List<String>)
    }

    data class OutboundBean(val tag: String,
                            var protocol: String,
                            var settings: OutSettingsBean?,
                            var streamSettings: StreamSettingsBean?,
                            var mux: MuxBean?) {

        data class OutSettingsBean(var vnext: List<VnextBean>?,
                                   var servers: List<ServersBean>?,
                                   var response: Response) {

            data class VnextBean(var address: String,
                                 var port: Int,
                                 var users: List<UsersBean>) {

                data class UsersBean(var id: String,
                                     var alterId: Int,
                                     var security: String,
                                     var level: Int)
            }

            data class ServersBean(var address: String,
                                   var method: String,
                                   var ota: Boolean,
                                   var password: String,
                                   var port: Int,
                                   var level: Int)

            data class Response(var type: String)
        }

        data class StreamSettingsBean(var network: String,
                                      var security: String,
                                      var tcpSettings: TcpSettingsBean?,
                                      var kcpSettings: KcpSettingsBean?,
                                      var wsSettings: WsSettingsBean?,
                                      var httpSettings: HttpSettingsBean?,
                                      var tlsSettings: TlsSettingsBean?,
                                      var quicSettings: QuicSettingBean?
        ) {

            data class TcpSettingsBean(var header: HeaderBean = HeaderBean()) {
                data class HeaderBean(var type: String = "none",
                                      var request: Any? = null,
                                      var response: Any? = null)
            }

            data class KcpSettingsBean(var mtu: Int = 1350,
                                       var tti: Int = 20,
                                       var uplinkCapacity: Int = 12,
                                       var downlinkCapacity: Int = 100,
                                       var congestion: Boolean = false,
                                       var readBufferSize: Int = 1,
                                       var writeBufferSize: Int = 1,
                                       var header: HeaderBean = HeaderBean()) {
                data class HeaderBean(var type: String = "none")
            }

            data class WsSettingsBean(var path: String = "",
                                      var headers: HeadersBean = HeadersBean()) {
                data class HeadersBean(var Host: String = "")
            }

            data class HttpSettingsBean(var host: List<String> = ArrayList(),
                                        var path: String = "")

            data class TlsSettingsBean(var allowInsecure: Boolean = true,
                                       var serverName: String = "")

            data class QuicSettingBean(var security: String = "none",
                                       var key: String = "",
                                       var header: HeaderBean = HeaderBean()) {
                data class HeaderBean(var type: String = "none")
            }
        }

        data class MuxBean(var enabled: Boolean)

        fun getServerAddress(): String? {
            if (protocol.equals(EConfigType.VMESS.name.toLowerCase())) {
                return settings?.vnext?.get(0)?.address
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name.toLowerCase()) || protocol.equals(EConfigType.SOCKS.name.toLowerCase())) {
                return settings?.servers?.get(0)?.address
            }
            return null
        }

        fun getServerPort(): Int? {
            if (protocol.equals(EConfigType.VMESS.name.toLowerCase())) {
                return settings?.vnext?.get(0)?.port
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name.toLowerCase()) || protocol.equals(EConfigType.SOCKS.name.toLowerCase())) {
                return settings?.servers?.get(0)?.port
            }
            return null
        }
    }

    //data class DnsBean(var servers: List<String>)
    data class DnsBean(var servers: List<Any>?=null,
                       var hosts: Map<String, String>?=null
    ) {
        data class ServersBean(var address: String = "",
                               var port: Int = 0,
                               var domains: List<String>?)
    }

    data class RoutingBean(var domainStrategy: String,
                           var rules: ArrayList<RulesBean>) {

        data class RulesBean(var type: String = "",
                             var ip: ArrayList<String>? = null,
                             var domain: ArrayList<String>? = null,
                             var outboundTag: String = "",
                             var port: String? = null,
                             var inboundTag: ArrayList<String>? = null)
    }

    data class PolicyBean(var levels: Map<String, LevelBean>,
                            var system: Any?=null) {
        data class LevelBean(
                  var handshake: Int? = null,
                  var connIdle: Int? = null,
                  var uplinkOnly: Int? = null,
                  var downlinkOnly: Int? = null)
    }
}