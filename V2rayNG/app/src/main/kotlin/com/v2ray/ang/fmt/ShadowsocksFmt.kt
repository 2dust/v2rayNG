package com.v2ray.ang.fmt

import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.Utils
import java.net.URI

object ShadowsocksFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        return parseSip002(str) ?: parseLegacy(str)
    }

    fun parseSip002(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.idnHost.isEmpty()) return null
        if (uri.port <= 0) return null
        if (uri.userInfo.isNullOrEmpty()) return null

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
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

            if (queryParam["plugin"] == "obfs-local" && queryParam["obfs"] == "http") {
                config.network = "tcp"
                config.headerType = "http"
                config.host = queryParam["obfs-host"]
                config.path = queryParam["path"]
            }
        }

        return config
    }

    fun parseLegacy(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SHADOWSOCKS)
        var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
        val indexSplit = result.indexOf("#")
        if (indexSplit > 0) {
            try {
                config.remarks =
                    Utils.urlDecode(result.substring(indexSplit + 1, result.length))
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun toUri(config: ProfileItem): String {
        val pw = "${config.method}:${config.password}"

        return toUri(config, Utils.encode(pw), null)
    }

    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.SHADOWSOCKS)

        outboundBean?.settings?.servers?.get(0)?.let { server ->
            server.address = profileItem.server.orEmpty()
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.method = profileItem.method
        }

        return outboundBean
    }


}