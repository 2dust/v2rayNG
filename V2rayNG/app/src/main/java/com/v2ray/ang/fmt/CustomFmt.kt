package com.v2ray.ang.fmt

import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.CustomConfigUtil
import com.v2ray.ang.util.JsonUtil

object CustomFmt : FmtBase() {
    /**
     * Parses a JSON string into a ProfileItem object.
     *
     * @param str the JSON string to parse
     * @return the parsed ProfileItem object, or null if parsing fails
     */
    fun parse(str: String): ProfileItem {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val fullConfig = JsonUtil.fromJson(str, V2rayConfig::class.java)
        val outbound = fullConfig?.getProxyOutbound()

        config.remarks = fullConfig?.remarks ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        // The typed model only understands the flat "address"/"port" settings shape,
        // so fall back to the JSON tree for vnext/servers/hysteria/wireguard outbounds.
        if (config.server.isNullOrBlank() || config.serverPort.isNullOrBlank()) {
            CustomConfigUtil.getProxyOutbound(CustomConfigUtil.parseConfig(str))
                ?.let { CustomConfigUtil.extractHostAndPort(it) }
                ?.let { (host, port) ->
                    config.server = host
                    config.serverPort = port.toString()
                }
        }

        return config
    }
}