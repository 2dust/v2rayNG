package com.v2ray.ang.dto

import com.v2ray.ang.AppConfig.TAG_AGENT
import com.v2ray.ang.AppConfig.TAG_BLOCKED
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.util.Utils

data class ServerConfig(
        val configVersion: Int = 3,
        val configType: EConfigType,
        var subscriptionId: String = "",
        val addedTime: Long = System.currentTimeMillis(),
        var remarks: String = "",
        val outboundBean: V2rayConfig.OutboundBean? = null,
        var fullConfig: V2rayConfig? = null
) {
    companion object {
        fun create(configType: EConfigType): ServerConfig {
            when(configType) {
                EConfigType.VMESS, EConfigType.VLESS ->
                    return ServerConfig(
                            configType = configType,
                            outboundBean = V2rayConfig.OutboundBean(
                                    protocol = configType.name.toLowerCase(),
                                    settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                            vnext = listOf(V2rayConfig.OutboundBean.OutSettingsBean.VnextBean(
                                                    users = listOf(V2rayConfig.OutboundBean.OutSettingsBean.VnextBean.UsersBean())))),
                                    streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()))
                EConfigType.CUSTOM ->
                    return ServerConfig(configType = configType)
                EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.TROJAN ->
                    return ServerConfig(
                            configType = configType,
                            outboundBean = V2rayConfig.OutboundBean(
                                    protocol = configType.name.toLowerCase(),
                                    settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                            servers = listOf(V2rayConfig.OutboundBean.OutSettingsBean.ServersBean())),
                                    streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()))
            }
        }
    }

    fun getProxyOutbound(): V2rayConfig.OutboundBean? {
        if (configType != EConfigType.CUSTOM) {
            return outboundBean
        }
        return fullConfig?.getProxyOutbound()
    }

    fun getAllOutboundTags(): MutableList<String> {
        if (configType != EConfigType.CUSTOM) {
            return mutableListOf(TAG_AGENT, TAG_DIRECT, TAG_BLOCKED)
        }
        fullConfig?.let { config ->
            return config.outbounds.map { it.tag }.toMutableList()
        }
        return mutableListOf()
    }

    fun getV2rayPointDomainAndPort(): String {
        val address = getProxyOutbound()?.getServerAddress().orEmpty()
        val port = getProxyOutbound()?.getServerPort()
        return if (Utils.isIpv6Address(address)) {
            String.format("[%s]:%s", address, port)
        } else {
            String.format("%s:%s", address, port)
        }
    }
}
