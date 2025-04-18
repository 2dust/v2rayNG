package com.v2ray.ang.fmt

import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.GrpcSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.HttpSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.HttpupgradeSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.KcpSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.TlsSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.WsSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.XhttpSettingsBean
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import java.net.URI

open class FmtBase {
    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @param userInfo the user information to include in the URI
     * @param dicQuery the query parameters to include in the URI
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem, userInfo: String?, dicQuery: HashMap<String, String>?): String {
        val query = if (dicQuery != null)
            "?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.urlEncode(it.second) })
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.urlEncode(userInfo ?: ""),
            Utils.getIpv6Address(config.server),
            config.serverPort
        )

        return "${url}${query}#${Utils.urlEncode(config.remarks)}"
    }

    /**
     * Extracts query parameters from a URI.
     *
     * @param uri the URI to extract query parameters from
     * @return a map of query parameters
     */
    fun getQueryParam(uri: URI): Map<String, String> {
        return uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }
    }

    /**
     * Populates a ProfileItem object with values from query parameters.
     *
     * @param config the ProfileItem object to populate
     * @param queryParam the query parameters to use for populating the ProfileItem
     * @param allowInsecure whether to allow insecure connections
     */
    fun getItemFormQuery(config: ProfileItem, queryParam: Map<String, String>, allowInsecure: Boolean) {
        config.network = queryParam["type"] ?: NetworkType.TCP.type
        config.headerType = queryParam["headerType"]
        config.host = queryParam["host"]
        config.path = queryParam["path"]

        config.seed = queryParam["seed"]
        config.quicSecurity = queryParam["quicSecurity"]
        config.quicKey = queryParam["key"]
        config.mode = queryParam["mode"]
        config.serviceName = queryParam["serviceName"]
        config.authority = queryParam["authority"]
        config.xhttpMode = queryParam["mode"]
        config.xhttpExtra = queryParam["extra"]

        config.security = queryParam["security"]
        if (config.security != AppConfig.TLS && config.security != AppConfig.REALITY) {
            config.security = null
        }
        config.insecure = if (queryParam["allowInsecure"].isNullOrEmpty()) {
            allowInsecure
        } else {
            queryParam["allowInsecure"].orEmpty() == "1"
        }
        config.sni = queryParam["sni"]
        config.fingerPrint = queryParam["fp"]
        config.alpn = queryParam["alpn"]
        config.publicKey = queryParam["pbk"]
        config.shortId = queryParam["sid"]
        config.spiderX = queryParam["spx"]
        config.flow = queryParam["flow"]
    }

    /**
     * Creates a map of query parameters from a ProfileItem object.
     *
     * @param config the ProfileItem object to create query parameters from
     * @return a map of query parameters
     */
    fun getQueryDic(config: ProfileItem): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()
        dicQuery["security"] = config.security?.ifEmpty { "none" }.orEmpty()
        config.sni.let { if (it.isNotNullEmpty()) dicQuery["sni"] = it.orEmpty() }
        config.alpn.let { if (it.isNotNullEmpty()) dicQuery["alpn"] = it.orEmpty() }
        config.fingerPrint.let { if (it.isNotNullEmpty()) dicQuery["fp"] = it.orEmpty() }
        config.publicKey.let { if (it.isNotNullEmpty()) dicQuery["pbk"] = it.orEmpty() }
        config.shortId.let { if (it.isNotNullEmpty()) dicQuery["sid"] = it.orEmpty() }
        config.spiderX.let { if (it.isNotNullEmpty()) dicQuery["spx"] = it.orEmpty() }
        config.flow.let { if (it.isNotNullEmpty()) dicQuery["flow"] = it.orEmpty() }

        val networkType = NetworkType.fromString(config.network)
        dicQuery["type"] = networkType.type

        when (networkType) {
            NetworkType.TCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
            }

            NetworkType.KCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.seed.let { if (it.isNotNullEmpty()) dicQuery["seed"] = it.orEmpty() }
            }

            NetworkType.WS, NetworkType.HTTP_UPGRADE -> {
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
                config.path.let { if (it.isNotNullEmpty()) dicQuery["path"] = it.orEmpty() }
            }

            NetworkType.XHTTP -> {
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
                config.path.let { if (it.isNotNullEmpty()) dicQuery["path"] = it.orEmpty() }
                config.xhttpMode.let { if (it.isNotNullEmpty()) dicQuery["mode"] = it.orEmpty() }
                config.xhttpExtra.let { if (it.isNotNullEmpty()) dicQuery["extra"] = it.orEmpty() }
            }

            NetworkType.HTTP, NetworkType.H2 -> {
                dicQuery["type"] = "http"
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
                config.path.let { if (it.isNotNullEmpty()) dicQuery["path"] = it.orEmpty() }
            }

//            NetworkType.QUIC -> {
//                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
//                config.quicSecurity.let { if (it.isNotNullEmpty()) dicQuery["quicSecurity"] = it.orEmpty() }
//                config.quicKey.let { if (it.isNotNullEmpty()) dicQuery["key"] = it.orEmpty() }
//            }

            NetworkType.GRPC -> {
                config.mode.let { if (it.isNotNullEmpty()) dicQuery["mode"] = it.orEmpty() }
                config.authority.let { if (it.isNotNullEmpty()) dicQuery["authority"] = it.orEmpty() }
                config.serviceName.let { if (it.isNotNullEmpty()) dicQuery["serviceName"] = it.orEmpty() }
            }
        }

        return dicQuery
    }

    fun populateTransportSettings(
        streamSettings: StreamSettingsBean,
        transport: String,
        headerType: String?,
        host: String?,
        path: String?,
        seed: String?,
        quicSecurity: String?,
        key: String?,
        mode: String?,
        serviceName: String?,
        authority: String?,
        xhttpMode: String?,
        xhttpExtra: String?,
    ): String? {
        var sni: String? = null
        streamSettings.network = if (transport.isEmpty()) NetworkType.TCP.type else transport
        when (streamSettings.network) {
            NetworkType.TCP.type -> {
                val tcpSetting = TcpSettingsBean()
                if (headerType == AppConfig.HEADER_TYPE_HTTP) {
                    tcpSetting.header.type = AppConfig.HEADER_TYPE_HTTP
                    if (!TextUtils.isEmpty(host) || !TextUtils.isEmpty(path)) {
                        val requestObj = TcpSettingsBean.HeaderBean.RequestBean()
                        requestObj.headers.Host = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        requestObj.path = path.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        tcpSetting.header.request = requestObj
                        sni = requestObj.headers.Host?.getOrNull(0)
                    }
                } else {
                    tcpSetting.header.type = "none"
                    sni = host
                }
                streamSettings.tcpSettings = tcpSetting
            }

            NetworkType.KCP.type -> {
                val kcpsetting = KcpSettingsBean()
                kcpsetting.header.type = headerType ?: "none"
                if (seed.isNullOrEmpty()) {
                    kcpsetting.seed = null
                } else {
                    kcpsetting.seed = seed
                }
                if (host.isNullOrEmpty()) {
                    kcpsetting.header.domain = null
                } else {
                    kcpsetting.header.domain = host
                }
                streamSettings.kcpSettings = kcpsetting
            }

            NetworkType.WS.type -> {
                val wssetting = WsSettingsBean()
                wssetting.headers.Host = host.orEmpty()
                sni = host
                wssetting.path = path ?: "/"
                streamSettings.wsSettings = wssetting
            }

            NetworkType.HTTP_UPGRADE.type -> {
                val httpupgradeSetting = HttpupgradeSettingsBean()
                httpupgradeSetting.host = host.orEmpty()
                sni = host
                httpupgradeSetting.path = path ?: "/"
                streamSettings.httpupgradeSettings = httpupgradeSetting
            }

            NetworkType.XHTTP.type -> {
                val xhttpSetting = XhttpSettingsBean()
                xhttpSetting.host = host.orEmpty()
                sni = host
                xhttpSetting.path = path ?: "/"
                xhttpSetting.mode = xhttpMode
                xhttpSetting.extra = JsonUtil.parseString(xhttpExtra)
                streamSettings.xhttpSettings = xhttpSetting
            }

            NetworkType.H2.type, NetworkType.HTTP.type -> {
                streamSettings.network = NetworkType.H2.type
                val h2Setting = HttpSettingsBean()
                h2Setting.host = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                sni = h2Setting.host.getOrNull(0)
                h2Setting.path = path ?: "/"
                streamSettings.httpSettings = h2Setting
            }

//                    "quic" -> {
//                        val quicsetting = QuicSettingBean()
//                        quicsetting.security = quicSecurity ?: "none"
//                        quicsetting.key = key.orEmpty()
//                        quicsetting.header.type = headerType ?: "none"
//                        quicSettings = quicsetting
//                    }

            NetworkType.GRPC.type -> {
                val grpcSetting = GrpcSettingsBean()
                grpcSetting.multiMode = mode == "multi"
                grpcSetting.serviceName = serviceName.orEmpty()
                grpcSetting.authority = authority.orEmpty()
                grpcSetting.idle_timeout = 60
                grpcSetting.health_check_timeout = 20
                sni = authority
                streamSettings.grpcSettings = grpcSetting
            }
        }
        return sni
    }

    fun populateTlsSettings(
        streamSettings: StreamSettingsBean,
        streamSecurity: String,
        allowInsecure: Boolean,
        sni: String?,
        fingerprint: String?,
        alpns: String?,
        publicKey: String?,
        shortId: String?,
        spiderX: String?
    ) {
        streamSettings.security = if (streamSecurity.isEmpty()) null else streamSecurity
        if (streamSettings.security == null) return
        val tlsSetting = TlsSettingsBean(
            allowInsecure = allowInsecure,
            serverName = if (sni.isNullOrEmpty()) null else sni,
            fingerprint = if (fingerprint.isNullOrEmpty()) null else fingerprint,
            alpn = if (alpns.isNullOrEmpty()) null else alpns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            publicKey = if (publicKey.isNullOrEmpty()) null else publicKey,
            shortId = if (shortId.isNullOrEmpty()) null else shortId,
            spiderX = if (spiderX.isNullOrEmpty()) null else spiderX,
        )
        if (streamSettings.security == AppConfig.TLS) {
            streamSettings.tlsSettings = tlsSetting
            streamSettings.realitySettings = null
        } else if (streamSettings.security == AppConfig.REALITY) {
            streamSettings.tlsSettings = null
            streamSettings.realitySettings = tlsSetting
        }
    }

}
