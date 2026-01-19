package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.StreamSettingsBean.UdpMasksBean.UdpMasksSettingsBean
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
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo
        config.security = AppConfig.TLS
        config.network = NetworkType.HYSTERIA.type

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
     * Converts a ProfileItem object to an OutboundBean object.
     *
     * @param profileItem the ProfileItem object to convert
     * @return the converted OutboundBean object, or null if conversion fails
     */
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.HYSTERIA2) ?: return null
        profileItem.network = NetworkType.HYSTERIA.type
        profileItem.alpn = "h3"

        outboundBean.settings?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.version = 2
        }

        val sni = outboundBean.streamSettings?.let {
            V2rayConfigManager.populateTransportSettings(it, profileItem)
        }

        outboundBean.streamSettings?.let {
            V2rayConfigManager.populateTlsSettings(it, profileItem, sni)
        }

        if (profileItem.obfsPassword.isNotNullEmpty()) {
            outboundBean.streamSettings?.udpmasks = mutableListOf(
                StreamSettingsBean.UdpMasksBean(
                    type = "salamander",
                    settings = UdpMasksSettingsBean(
                        password = profileItem.obfsPassword
                    )
                )
            )
        }
        return outboundBean
    }
}