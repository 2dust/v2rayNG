package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil

object CustomOutboundFmt : FmtBase() {
    fun parse(str: String): Pair<ProfileItem, String> {
        val config = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND)

        val outbound = JsonUtil.fromJson(str, V2rayConfig.OutboundBean::class.java)

        config.remarks = outbound?.tag ?: System.currentTimeMillis().toString()
        config.server = outbound?.getServerAddress()
        config.serverPort = outbound?.getServerPort()?.toString()

        outbound?.tag = AppConfig.TAG_PROXY
        val rawOutbound = outbound?.let { JsonUtil.toJsonPretty(it) } ?: ""
        return Pair(config, rawOutbound)
    }
}
