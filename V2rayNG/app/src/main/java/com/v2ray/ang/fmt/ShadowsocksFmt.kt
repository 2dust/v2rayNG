package com.v2ray.ang.fmt

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.Utils
import java.net.URI

object ShadowsocksFmt : FmtBase() {
    /**
     * Parses a Shadowsocks URI string into a ProfileItem object.
     *
     * @param str the Shadowsocks URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem? {
        return parseSip002(str) ?: parseLegacy(str)
    }

    /**
     * Parses a SIP002 Shadowsocks URI string into a ProfileItem object.
     *
     * @param str the SIP002 Shadowsocks URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parseSip002(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null
        if (uri.userInfo.isNullOrEmpty()) return null

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty()).let { if (it.isEmpty()) "none" else it }
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()

        val result = if (uri.userInfo.contains(":")) {
            uri.userInfo.split(":", limit = 2)
        } else {
            Utils.decode(uri.userInfo).split(":", limit = 2)
        }
        if (result.count() == 2) {
            config.method = result.first()
            config.password = result.last()
        }

        if (!uri.rawQuery.isNullOrEmpty()) {
            val queryParam = getQueryParam(uri)
            if (queryParam["plugin"]?.contains("obfs=http") == true) {
                val queryPairs = HashMap<String, String>()
                for (pair in queryParam["plugin"]?.split(";") ?: listOf()) {
                    val idx = pair.split("=")
                    if (idx.count() == 2) {
                        queryPairs.put(idx.first(), idx.last())
                    }
                }
                config.network = NetworkType.TCP.type
                config.headerType = "http"
                config.host = queryPairs["obfs-host"]
                config.path = queryPairs["path"]
            }
        }

        return config
    }

    /**
     * Parses a legacy Shadowsocks URI string into a ProfileItem object.
     *
     * @param str the legacy Shadowsocks URI string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parseLegacy(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)
        var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
        val indexSplit = result.indexOf("#")
        if (indexSplit > 0) {
            try {
                config.remarks =
                    Utils.urlDecode(result.substring(indexSplit + 1, result.length))
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to decode remarks in SS legacy URL", e)
            }

            result = result.substring(0, indexSplit)
        }

        //part decode
        val indexS = result.indexOf("@")
        result = if (indexS > 0) {
            Utils.decode(result.substring(0, indexS)) + result.substring(
                indexS,
                result.length
            )
        } else {
            Utils.decode(result)
        }

        val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
        val match = legacyPattern.matchEntire(result) ?: return null

        config.server = match.groupValues[3].removeSurrounding("[", "]")
        config.serverPort = match.groupValues[4]
        config.password = match.groupValues[2]
        config.method = match.groupValues[1].lowercase()

        return config
    }

    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem): String {
        val pw = "${config.method}:${config.password}"

        return toUri(config, Utils.encode(pw, true), null)
    }

    /**
     * Converts a ProfileItem object to an OutboundBean object.
     *
     * @param profileItem the ProfileItem object to convert
     * @return the converted OutboundBean object, or null if conversion fails
     */
    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = V2rayConfigManager.createInitOutbound(EConfigType.SHADOWSOCKS)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.method = profileItem.method
        }

        val sni = outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            V2rayConfigManager.populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }
}