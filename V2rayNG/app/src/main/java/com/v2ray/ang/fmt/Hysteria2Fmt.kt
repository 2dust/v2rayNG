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
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.Utils
import java.net.URI

object Hysteria2Fmt : FmtBase() {
    /**
     * Parses a Hysteria2 URI string into a ProfileItem object.
     *
     * @param str the Hysteria2 URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty()).let { if (it.isEmpty()) "none" else it }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.security = AppConfig.TLS

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)

            getItemFormQuery(config, queryParam, allowInsecure)

            config.security = queryParam["security"] ?: AppConfig.TLS
            config.obfsPassword = queryParam["obfs-password"]
            config.portHopping = queryParam["mport"]
            if (config.portHopping.isNotNullEmpty()) {
                config.portHoppingInterval = queryParam["mportHopInt"]
            }
            config.pinSHA256 = queryParam["pinSHA256"]

        }

        return config
    }

    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
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
        if (config.portHoppingInterval.isNotNullEmpty()) {
            dicQuery["mportHopInt"] = config.portHoppingInterval.orEmpty()
        }
        if (config.pinSHA256.isNotNullEmpty()) {
            dicQuery["pinSHA256"] = config.pinSHA256.orEmpty()
        }

        return toUri(config, config.password, dicQuery)
    }

    /**
     * Converts a ProfileItem object to a Hysteria2Bean object.
     *
     * @param config the ProfileItem object to convert
     * @param socksPort the port number for the socks5 proxy
     * @return the converted Hysteria2Bean object, or null if conversion fails
     */
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

        val bandwidth = if (config.bandwidthDown.isNullOrEmpty() || config.bandwidthUp.isNullOrEmpty()) null else
            Hysteria2Bean.BandwidthBean(
                down = config.bandwidthDown,
                up = config.bandwidthUp,
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
            bandwidth = bandwidth,
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

    /**
     * Converts a ProfileItem object to an OutboundBean object.
     *
     * @param profileItem the ProfileItem object to convert
     * @return the converted OutboundBean object, or null if conversion fails
     */
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.HYSTERIA2)
        return outboundBean
    }
}