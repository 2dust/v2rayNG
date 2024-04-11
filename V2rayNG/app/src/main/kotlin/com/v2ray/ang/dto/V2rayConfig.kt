package com.v2ray.ang.dto

import android.text.TextUtils
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

data class V2rayConfig(
        var remarks: String? = null,
        var stats: Any? = null,
        val log: LogBean,
        var policy: PolicyBean?,
        val inbounds: ArrayList<InboundBean>,
        var outbounds: ArrayList<OutboundBean>,
        var dns: DnsBean,
        val routing: RoutingBean,
        val api: Any? = null,
        val transport: Any? = null,
        val reverse: Any? = null,
        var fakedns: Any? = null,
        val browserForwarder: Any? = null,
        var observatory: Any? = null,
        var burstObservatory: Any? = null) {
    companion object {
        const val DEFAULT_PORT = 443
        const val DEFAULT_SECURITY = "auto"
        const val DEFAULT_LEVEL = 8
        const val DEFAULT_NETWORK = "tcp"

        const val TLS = "tls"
        const val REALITY = "reality"
        const val HTTP = "http"
    }

    data class LogBean(val access: String,
                       val error: String,
                       var loglevel: String?,
                       val dnsLog: Boolean? = null)

    data class InboundBean(
            var tag: String,
            var port: Int,
            var protocol: String,
            var listen: String? = null,
            val settings: Any? = null,
            val sniffing: SniffingBean?,
            val streamSettings: Any? = null,
            val allocate: Any? = null) {

        data class InSettingsBean(val auth: String? = null,
                                  val udp: Boolean? = null,
                                  val userLevel: Int? = null,
                                  val address: String? = null,
                                  val port: Int? = null,
                                  val network: String? = null)

        data class SniffingBean(var enabled: Boolean,
                                val destOverride: ArrayList<String>,
                                val metadataOnly: Boolean? = null)
    }

    data class OutboundBean(var tag: String = "proxy",
                            var protocol: String,
                            var settings: OutSettingsBean? = null,
                            var streamSettings: StreamSettingsBean? = null,
                            val proxySettings: Any? = null,
                            val sendThrough: String? = null,
                            var mux: MuxBean? = MuxBean(false)) {

        data class OutSettingsBean(var vnext: List<VnextBean>? = null,
                                   var fragment: FragmentBean? = null,
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
                                   var mtu :Int? = null
        ) {

            data class VnextBean(var address: String = "",
                                 var port: Int = DEFAULT_PORT,
                                 var users: List<UsersBean>) {

                data class UsersBean(var id: String = "",
                                     var alterId: Int? = null,
                                     var security: String = DEFAULT_SECURITY,
                                     var level: Int = DEFAULT_LEVEL,
                                     var encryption: String = "",
                                     var flow: String = "")
            }

            data class FragmentBean(var packets: String? = null,
                                 var length: String? = null,
                                 var interval: String? = null)

            data class ServersBean(var address: String = "",
                                   var method: String = "chacha20-poly1305",
                                   var ota: Boolean = false,
                                   var password: String = "",
                                   var port: Int = DEFAULT_PORT,
                                   var level: Int = DEFAULT_LEVEL,
                                   val email: String? = null,
                                   var flow: String? = null,
                                   val ivCheck: Boolean? = null,
                                   var users: List<SocksUsersBean>? = null) {


                data class SocksUsersBean(var user: String = "",
                                          var pass: String = "",
                                          var level: Int = DEFAULT_LEVEL)
            }

            data class Response(var type: String)

            data class WireGuardBean(var publicKey: String = "",
                                     var endpoint: String = "")
        }

        data class StreamSettingsBean(var network: String = DEFAULT_NETWORK,
                                      var security: String = "",
                                      var tcpSettings: TcpSettingsBean? = null,
                                      var kcpSettings: KcpSettingsBean? = null,
                                      var wsSettings: WsSettingsBean? = null,
                                      var httpupgradeSettings: HttpupgradeSettingsBean? = null,
                                      var httpSettings: HttpSettingsBean? = null,
                                      var tlsSettings: TlsSettingsBean? = null,
                                      var quicSettings: QuicSettingBean? = null,
                                      var realitySettings: TlsSettingsBean? = null,
                                      var grpcSettings: GrpcSettingsBean? = null,
                                      val dsSettings: Any? = null,
                                      var sockopt: SockoptBean? = null
        ) {

            data class TcpSettingsBean(var header: HeaderBean = HeaderBean(),
                                       val acceptProxyProtocol: Boolean? = null) {
                data class HeaderBean(var type: String = "none",
                                      var request: RequestBean? = null,
                                      var response: Any? = null) {
                    data class RequestBean(var path: List<String> = ArrayList(),
                                           var headers: HeadersBean = HeadersBean(),
                                           val version: String? = null,
                                           val method: String? = null) {
                        data class HeadersBean(var Host: List<String> = ArrayList(),
                                               @SerializedName("User-Agent")
                                               val userAgent: List<String>? = null,
                                               @SerializedName("Accept-Encoding")
                                               val acceptEncoding: List<String>? = null,
                                               val Connection: List<String>? = null,
                                               val Pragma: String? = null)
                    }
                }
            }

            data class KcpSettingsBean(var mtu: Int = 1350,
                                       var tti: Int = 50,
                                       var uplinkCapacity: Int = 12,
                                       var downlinkCapacity: Int = 100,
                                       var congestion: Boolean = false,
                                       var readBufferSize: Int = 1,
                                       var writeBufferSize: Int = 1,
                                       var header: HeaderBean = HeaderBean(),
                                       var seed: String? = null) {
                data class HeaderBean(var type: String = "none")
            }

            data class WsSettingsBean(var path: String = "",
                                      var headers: HeadersBean = HeadersBean(),
                                      val maxEarlyData: Int? = null,
                                      val useBrowserForwarding: Boolean? = null,
                                      val acceptProxyProtocol: Boolean? = null) {
                data class HeadersBean(var Host: String = "")
            }

            data class HttpupgradeSettingsBean(var path: String = "",
                                               var host: String = "",
                                               val acceptProxyProtocol: Boolean? = null)

            data class HttpSettingsBean(var host: List<String> = ArrayList(),
                                        var path: String = "")

            data class SockoptBean(var TcpNoDelay: Boolean? = null,
                                   var tcpKeepAliveIdle: Int? = null,
                                   var tcpFastOpen: Boolean? = null,
                                   var tproxy: String? = null,
                                   var mark: Int? = null,
                                   var dialerProxy: String? = null)

            data class TlsSettingsBean(var allowInsecure: Boolean = false,
                                       var serverName: String = "",
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
                                       var spiderX: String? = null)

            data class QuicSettingBean(var security: String = "none",
                                       var key: String = "",
                                       var header: HeaderBean = HeaderBean()) {
                data class HeaderBean(var type: String = "none")
            }

            data class GrpcSettingsBean(var serviceName: String = "",
                                        var authority: String? = null,
                                        var multiMode: Boolean? = null)

            fun populateTransportSettings(transport: String, headerType: String?, host: String?, path: String?, seed: String?,
                                          quicSecurity: String?, key: String?, mode: String?, serviceName: String?,
                                          authority: String?): String {
                var sni = ""
                network = transport
                when (network) {
                    "tcp" -> {
                        val tcpSetting = TcpSettingsBean()
                        if (headerType == HTTP) {
                            tcpSetting.header.type = HTTP
                            if (!TextUtils.isEmpty(host) || !TextUtils.isEmpty(path)) {
                                val requestObj = TcpSettingsBean.HeaderBean.RequestBean()
                                requestObj.headers.Host = (host ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                requestObj.path = (path ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                tcpSetting.header.request = requestObj
                                sni = requestObj.headers.Host.getOrNull(0) ?: sni
                            }
                        } else {
                            tcpSetting.header.type = "none"
                            sni = host ?: ""
                        }
                        tcpSettings = tcpSetting
                    }
                    "kcp" -> {
                        val kcpsetting = KcpSettingsBean()
                        kcpsetting.header.type = headerType ?: "none"
                        if (seed.isNullOrEmpty()) {
                            kcpsetting.seed = null
                        } else {
                            kcpsetting.seed = seed
                        }
                        kcpSettings = kcpsetting
                    }
                    "ws" -> {
                        val wssetting = WsSettingsBean()
                        wssetting.headers.Host = host ?: ""
                        sni = wssetting.headers.Host
                        wssetting.path = path ?: "/"
                        wsSettings = wssetting
                    }
                    "httpupgrade" -> {
                        val httpupgradeSetting = HttpupgradeSettingsBean()
                        httpupgradeSetting.host = host ?: ""
                        sni = httpupgradeSetting.host
                        httpupgradeSetting.path = path ?: "/"
                        httpupgradeSettings = httpupgradeSetting
                    }
                    "h2", "http" -> {
                        network = "h2"
                        val h2Setting = HttpSettingsBean()
                        h2Setting.host = (host ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        sni = h2Setting.host.getOrNull(0) ?: sni
                        h2Setting.path = path ?: "/"
                        httpSettings = h2Setting
                    }
                    "quic" -> {
                        val quicsetting = QuicSettingBean()
                        quicsetting.security = quicSecurity ?: "none"
                        quicsetting.key = key ?: ""
                        quicsetting.header.type = headerType ?: "none"
                        quicSettings = quicsetting
                    }
                    "grpc" -> {
                        val grpcSetting = GrpcSettingsBean()
                        grpcSetting.multiMode = mode == "multi"
                        grpcSetting.serviceName = serviceName ?: ""
                        grpcSetting.authority = authority ?: ""
                        sni = authority ?: ""
                        grpcSettings = grpcSetting
                    }
                }
                return sni
            }

            fun populateTlsSettings(streamSecurity: String, allowInsecure: Boolean, sni: String, fingerprint: String?, alpns: String?,
                                    publicKey: String?, shortId: String?, spiderX: String?) {
                security = streamSecurity
                val tlsSetting = TlsSettingsBean(
                        allowInsecure = allowInsecure,
                        serverName = sni,
                        fingerprint = fingerprint,
                        alpn = if (alpns.isNullOrEmpty()) null else alpns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        publicKey = publicKey,
                        shortId = shortId,
                        spiderX = spiderX
                )
                if (security == TLS) {
                    tlsSettings = tlsSetting
                    realitySettings = null
                } else if (security == REALITY) {
                    tlsSettings = null
                    realitySettings = tlsSetting
                }
            }
        }

        data class MuxBean(var enabled: Boolean,
                           var concurrency: Int = 8,
                           var xudpConcurrency: Int = 8,
                           var xudpProxyUDP443: String = "",)

        fun getServerAddress(): String? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                    || protocol.equals(EConfigType.VLESS.name, true)) {
                return settings?.vnext?.get(0)?.address
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                    || protocol.equals(EConfigType.SOCKS.name, true)
                    || protocol.equals(EConfigType.TROJAN.name, true)) {
                return settings?.servers?.get(0)?.address
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.peers?.get(0)?.endpoint?.substringBeforeLast(":")
            }
            return null
        }

        fun getServerPort(): Int? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                    || protocol.equals(EConfigType.VLESS.name, true)) {
                return settings?.vnext?.get(0)?.port
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                    || protocol.equals(EConfigType.SOCKS.name, true)
                    || protocol.equals(EConfigType.TROJAN.name, true)) {
                return settings?.servers?.get(0)?.port
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.peers?.get(0)?.endpoint?.substringAfterLast(":")?.toInt()
            }
            return null
        }

        fun getPassword(): String? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                    || protocol.equals(EConfigType.VLESS.name, true)) {
                return settings?.vnext?.get(0)?.users?.get(0)?.id
            } else if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                    || protocol.equals(EConfigType.TROJAN.name, true)) {
                return settings?.servers?.get(0)?.password
            } else if (protocol.equals(EConfigType.SOCKS.name, true)) {
                return settings?.servers?.get(0)?.users?.get(0)?.pass
            } else if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                return settings?.secretKey
            }
            return null
        }

        fun getSecurityEncryption(): String? {
            return when {
                protocol.equals(EConfigType.VMESS.name, true) -> settings?.vnext?.get(0)?.users?.get(0)?.security
                protocol.equals(EConfigType.VLESS.name, true) -> settings?.vnext?.get(0)?.users?.get(0)?.encryption
                protocol.equals(EConfigType.SHADOWSOCKS.name, true) -> settings?.servers?.get(0)?.method
                else -> null
            }
        }

        fun getTransportSettingDetails(): List<String>? {
            if (protocol.equals(EConfigType.VMESS.name, true)
                    || protocol.equals(EConfigType.VLESS.name, true)
                    || protocol.equals(EConfigType.TROJAN.name, true)
                    || protocol.equals(EConfigType.SHADOWSOCKS.name, true)) {
                val transport = streamSettings?.network ?: return null
                return when (transport) {
                    "tcp" -> {
                        val tcpSetting = streamSettings?.tcpSettings ?: return null
                        listOf(tcpSetting.header.type,
                                tcpSetting.header.request?.headers?.Host?.joinToString().orEmpty(),
                                tcpSetting.header.request?.path?.joinToString().orEmpty())
                    }
                    "kcp" -> {
                        val kcpSetting = streamSettings?.kcpSettings ?: return null
                        listOf(kcpSetting.header.type,
                                "",
                                kcpSetting.seed.orEmpty())
                    }
                    "ws" -> {
                        val wsSetting = streamSettings?.wsSettings ?: return null
                        listOf("",
                                wsSetting.headers.Host,
                                wsSetting.path)
                    }
                    "httpupgrade" -> {
                        val httpupgradeSetting = streamSettings?.httpupgradeSettings ?: return null
                        listOf("",
                            httpupgradeSetting.host,
                            httpupgradeSetting.path)
                    }
                    "h2" -> {
                        val h2Setting = streamSettings?.httpSettings ?: return null
                        listOf("",
                                h2Setting.host.joinToString(),
                                h2Setting.path)
                    }
                    "quic" -> {
                        val quicSetting = streamSettings?.quicSettings ?: return null
                        listOf(quicSetting.header.type,
                                quicSetting.security,
                                quicSetting.key)
                    }
                    "grpc" -> {
                        val grpcSetting = streamSettings?.grpcSettings ?: return null
                        listOf(if (grpcSetting.multiMode == true) "multi" else "gun",
                                grpcSetting.authority ?: "",
                                grpcSetting.serviceName)
                    }
                    else -> null
                }
            }
            return null
        }
    }

    data class DnsBean(var servers: ArrayList<Any>? = null,
                       var hosts: Map<String, Any>? = null,
                       val clientIp: String? = null,
                       val disableCache: Boolean? = null,
                       val queryStrategy: String? = null,
                       val tag: String? = null
    ) {
        data class ServersBean(var address: String = "",
                               var port: Int? = null,
                               var domains: List<String>? = null,
                               var expectIPs: List<String>? = null,
                               val clientIp: String? = null)
    }

    data class RoutingBean(var domainStrategy: String,
                           var domainMatcher: String? = null,
                           var rules: ArrayList<RulesBean>,
                           val balancers: List<Any>? = null) {

        data class RulesBean(
                             var ip: ArrayList<String>? = null,
                             var domain: ArrayList<String>? = null,
                             var outboundTag: String = "",
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
    }

    data class PolicyBean(var levels: Map<String, LevelBean>,
                          var system: Any? = null) {
        data class LevelBean(
                var handshake: Int? = null,
                var connIdle: Int? = null,
                var uplinkOnly: Int? = null,
                var downlinkOnly: Int? = null,
                val statsUserUplink: Boolean? = null,
                val statsUserDownlink: Boolean? = null,
                var bufferSize: Int? = null)
    }

    data class FakednsBean(var ipPool: String = "198.18.0.0/15",
                           var poolSize: Int = 10000) // roughly 10 times smaller than total ip pool

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

    fun toPrettyPrinting(): String {
        return GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .registerTypeAdapter( // custom serialiser is needed here since JSON by default parse number as Double, core will fail to start
                        object : TypeToken<Double>() {}.type,
                        JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? -> JsonPrimitive(src?.toInt()) }
                )
                .create()
                .toJson(this)
    }
}
