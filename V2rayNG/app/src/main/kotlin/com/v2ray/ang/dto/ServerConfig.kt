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
    var outboundFragmentBean: V2rayConfig.OutboundBean? = null,
    var fullConfig: V2rayConfig? = null
) {
    companion object {
        fun create(configType: EConfigType): ServerConfig {
            when(configType) {
                EConfigType.VMESS, EConfigType.VLESS ->
                    return ServerConfig(
                        configType = configType,
                        outboundBean = V2rayConfig.OutboundBean(
                            protocol = configType.name.lowercase(),
                            settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                vnext = listOf(V2rayConfig.OutboundBean.OutSettingsBean.VnextBean(
                                    users = listOf(V2rayConfig.OutboundBean.OutSettingsBean.VnextBean.UsersBean())))),
                            streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()),
                        outboundFragmentBean = V2rayConfig.OutboundBean(
                            tag = "fragment",
                            protocol = "freedom",
                            settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                fragment = V2rayConfig.OutboundBean.OutSettingsBean.FragmentBean())))
                EConfigType.CUSTOM ->
                    return ServerConfig(configType = configType)
                EConfigType.SHADOWSOCKS, EConfigType.SOCKS, EConfigType.TROJAN ->
                    return ServerConfig(
                        configType = configType,
                        outboundBean = V2rayConfig.OutboundBean(
                            protocol = configType.name.lowercase(),
                            settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                servers = listOf(V2rayConfig.OutboundBean.OutSettingsBean.ServersBean())),
                            streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean()))
                EConfigType.WIREGUARD ->
                    return ServerConfig(
                        configType = configType,
                        outboundBean =  V2rayConfig.OutboundBean(
                            protocol = configType.name.lowercase(),
                            settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                secretKey = "",
                                peers = listOf(V2rayConfig.OutboundBean.OutSettingsBean.WireGuardBean())
                            )))
            }
        }
    }

    fun getProxyOutbound(): V2rayConfig.OutboundBean? {
        if (configType != EConfigType.CUSTOM) {
            return outboundBean
        }
        return fullConfig?.getProxyOutbound()
    }

    fun getFragmentOutbound(): V2rayConfig.OutboundBean? {
        if (configType != EConfigType.CUSTOM) {
            return outboundFragmentBean
        }
        return fullConfig?.getFragmentOutbound()
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
        return Utils.getIpv6Address(address) + ":" + port
    }
}
