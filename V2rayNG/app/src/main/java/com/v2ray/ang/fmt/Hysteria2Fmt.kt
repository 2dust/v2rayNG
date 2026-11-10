package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.util.Utils
import java.net.URI

object Hysteria2Fmt : FmtBase() {
    /**
     * Parses a Hysteria2 URI string into a ProfileItem object.
     *
     * @param str the Hysteria2 URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.decodeURIComponent(uri.fragment.orEmpty()).let { it.ifEmpty { "none" } }
        config.server = uri.idnHost
        config.serverPort = (if (uri.port == -1) AppConfig.DEFAULT_PORT else uri.port).toString()
        config.password = uri.userInfo
        config.security = AppConfig.TLS
        config.network = NetworkType.HYSTERIA.type

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)

            getItemFormQuery(config, queryParam)

            config.security = queryParam["security"] ?: AppConfig.TLS
            config.obfs = queryParam["obfs"]?.takeIf { !it.equals("none", ignoreCase = true) }
            config.obfsPassword = queryParam["obfs-password"]
            config.minPacketSize = queryParam["minPacketSize"]
            config.maxPacketSize = queryParam["maxPacketSize"]
            config.portHopping = queryParam["mport"]
            config.pinnedCA256 = queryParam["pinSHA256"]
            if (config.isGeckoObfs()) {
                if (config.minPacketSize.isNullOrEmpty()) {
                    config.minPacketSize = "512"
                }
                if (config.maxPacketSize.isNullOrEmpty()) {
                    config.maxPacketSize = "1200"
                }
            }

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
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        config.insecure.let { dicQuery["insecure"] = if (it == true) "1" else "0" }

        val isGecko = config.isGeckoObfs()
        if (config.obfsPassword.isNotNullEmpty() && !config.obfs.equals("none", ignoreCase = true)) {
            dicQuery["obfs"] = if (isGecko) "gecko" else "salamander"
            dicQuery["obfs-password"] = config.obfsPassword.orEmpty()
            if (isGecko) {
                config.minPacketSize?.let { dicQuery["minPacketSize"] = it }
                config.maxPacketSize?.let { dicQuery["maxPacketSize"] = it }
            }
        }
        if (config.portHopping.isNotNullEmpty()) {
            dicQuery["mport"] = config.portHopping.orEmpty()
        }
        if (config.pinnedCA256.isNotNullEmpty()) {
            dicQuery["pinSHA256"] = config.pinnedCA256.orEmpty()
        }

        return toUri(config, config.password, dicQuery)
    }
}