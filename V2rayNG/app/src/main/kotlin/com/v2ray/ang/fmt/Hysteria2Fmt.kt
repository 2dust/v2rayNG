package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.Hysteria2Bean
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import java.net.URI

object Hysteria2Fmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.security = AppConfig.TLS

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)

            config.security = queryParam["security"] ?: AppConfig.TLS
            config.insecure = if (queryParam["insecure"].isNullOrEmpty()) {
                allowInsecure
            } else {
                queryParam["insecure"].orEmpty() == "1"
            }
            config.sni = queryParam["sni"]
            config.alpn = queryParam["alpn"]

            config.obfsPassword = queryParam["obfs-password"]
            config.portHopping = queryParam["mport"]
            config.pinSHA256 = queryParam["pinSHA256"]

        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()

        config.security.let { if (it != null) dicQuery["security"] = it }
        config.sni.let { if (it.isNotNullEmpty()) dicQuery["sni"] = it.orEmpty() }
        config.alpn.let { if (it.isNotNullEmpty()) dicQuery["alpn"] = it.orEmpty() }
        config.insecure.let { dicQuery["insecure"] = if (it == true) "1" else "0" }

        if (config.obfsPassword.isNotNullEmpty()) {
            dicQuery["obfs"] = "salamander"
            dicQuery["obfs-password"] = config.obfsPassword.orEmpty()
        }
        if (config.portHopping.isNotNullEmpty()) {
            dicQuery["mport"] = config.portHopping.orEmpty()
        }
        if (config.pinSHA256.isNotNullEmpty()) {
            dicQuery["pinSHA256"] = config.pinSHA256.orEmpty()
        }

        return toUri(config, config.password, dicQuery)
    }

    fun toNativeConfig(config: ProfileItem, socksPort: Int): Hysteria2Bean? {

        val obfs = if (config.obfsPassword.isNullOrEmpty()) null else
            Hysteria2Bean.ObfsBean(
                type = "salamander",
                salamander = Hysteria2Bean.ObfsBean.SalamanderBean(
                    password = config.obfsPassword
                )
            )

        val transport = if (config.portHopping.isNullOrEmpty()) null else
            Hysteria2Bean.TransportBean(
                type = "udp",
                udp = Hysteria2Bean.TransportBean.TransportUdpBean(
                    hopInterval = (config.portHoppingInterval ?: "30") + "s"
                )
            )

        val server =
            if (config.portHopping.isNullOrEmpty())
                config.getServerAddressAndPort()
            else
                Utils.getIpv6Address(config.server) + ":" + config.portHopping

        val bean = Hysteria2Bean(
            server = server,
            auth = config.password,
            obfs = obfs,
            transport = transport,
            socks5 = Hysteria2Bean.Socks5Bean(
                listen = "$LOOPBACK:${socksPort}",
            ),
            http = Hysteria2Bean.Socks5Bean(
                listen = "$LOOPBACK:${socksPort}",
            ),
            tls = Hysteria2Bean.TlsBean(
                sni = config.sni ?: config.server,
                insecure = config.insecure,
                pinSHA256 = if (config.pinSHA256.isNullOrEmpty()) null else config.pinSHA256
            )
        )
        return bean
    }


    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.HYSTERIA2)
        return outboundBean
    }

}