package com.v2ray.ang.dto

data class ServerConfig(
        val configVersion: Int = 3,
        val configType: EConfigType,
        val subscriptionId: String = "",
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
                EConfigType.SHADOWSOCKS, EConfigType.SOCKS ->
                    return ServerConfig(
                            configType = configType,
                            outboundBean = V2rayConfig.OutboundBean(
                                    protocol = configType.name.toLowerCase(),
                                    settings = V2rayConfig.OutboundBean.OutSettingsBean(
                                            servers = listOf(V2rayConfig.OutboundBean.OutSettingsBean.ServersBean()))))
                EConfigType.TROJAN ->
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
}
