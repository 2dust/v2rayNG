package com.v2ray.ang.util.fmt

import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.Utils

object SocksFmt : FmtBase() {
    fun parse(str: String): ServerConfig? {
        val config = ServerConfig.create(EConfigType.SOCKS)
        var result = str.replace(EConfigType.SOCKS.protocolScheme, "")
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
        if (indexS > 0) {
            result = Utils.decode(result.substring(0, indexS)) + result.substring(
                indexS,
                result.length
            )
        } else {
            result = Utils.decode(result)
        }

        val legacyPattern = "^(.*):(.*)@(.+?):(\\d+?)$".toRegex()
        val match =
            legacyPattern.matchEntire(result) ?: return null

        config.outboundBean?.settings?.servers?.get(0)?.let { server ->
            server.address = match.groupValues[3].removeSurrounding("[", "]")
            server.port = match.groupValues[4].toInt()
            val socksUsersBean =
                V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
            socksUsersBean.user = match.groupValues[1]
            socksUsersBean.pass = match.groupValues[2]
            server.users = listOf(socksUsersBean)
        }

        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        
        val pw =
            if (outbound.settings?.servers?.get(0)?.users?.get(0)?.user != null)
                "${outbound.settings?.servers?.get(0)?.users?.get(0)?.user}:${outbound.getPassword()}"
            else
                ":"

        return toUri(outbound.getServerAddress(), outbound.getServerPort(), pw, null, config.remarks)
    }
}