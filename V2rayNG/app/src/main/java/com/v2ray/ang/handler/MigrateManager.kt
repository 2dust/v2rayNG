package com.v2ray.ang.handler

import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.extension.removeWhiteSpace
import com.v2ray.ang.handler.MmkvManager.decodeServerConfig
import com.v2ray.ang.util.JsonUtil

object MigrateManager {
    private const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }

    /**
     * Migrates server configurations to profile items.
     *
     * @return True if migration was successful, false otherwise.
     */
    fun migrateServerConfig2Profile(): Boolean {
        if (serverStorage.count().toInt() == 0) {
            return false
        }
        val serverList = serverStorage.allKeys() ?: return false
        Log.i(AppConfig.TAG, "migrateServerConfig2Profile-" + serverList.count())

        for (guid in serverList) {
            var configOld = decodeServerConfigOld(guid) ?: continue
            var config = decodeServerConfig(guid)
            if (config != null) {
                serverStorage.remove(guid)
                continue
            }
            config = migrateServerConfig2ProfileSub(configOld) ?: continue
            config.subscriptionId = configOld.subscriptionId

            MmkvManager.encodeServerConfig(guid, config)

            //check and remove old
            decodeServerConfig(guid) ?: continue
            serverStorage.remove(guid)
            Log.i(AppConfig.TAG, "migrateServerConfig2Profile-" + config.remarks)
        }
        Log.i(AppConfig.TAG, "migrateServerConfig2Profile-end")
        return true
    }

    /**
     * Migrates a server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrateServerConfig2ProfileSub(configOld: ServerConfig): ProfileItem? {
        return when (configOld.getProxyOutbound()?.protocol) {
            EConfigType.VMESS.name.lowercase() -> migrate2ProfileCommon(configOld)
            EConfigType.VLESS.name.lowercase() -> migrate2ProfileCommon(configOld)
            EConfigType.TROJAN.name.lowercase() -> migrate2ProfileCommon(configOld)
            EConfigType.SHADOWSOCKS.name.lowercase() -> migrate2ProfileCommon(configOld)

            EConfigType.SOCKS.name.lowercase() -> migrate2ProfileSocks(configOld)
            EConfigType.HTTP.name.lowercase() -> migrate2ProfileHttp(configOld)
            EConfigType.WIREGUARD.name.lowercase() -> migrate2ProfileWireguard(configOld)
            EConfigType.HYSTERIA2.name.lowercase() -> migrate2ProfileHysteria2(configOld)

            EConfigType.CUSTOM.name.lowercase() -> migrate2ProfileCustom(configOld)

            else -> null
        }
    }

    /**
     * Migrates a common server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileCommon(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(configOld.configType)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()
        config.method = outbound.getSecurityEncryption()
        config.password = outbound.getPassword()
        config.flow = outbound?.settings?.vnext?.first()?.users?.first()?.flow ?: outbound?.settings?.servers?.first()?.flow

        config.network = outbound?.streamSettings?.network ?: NetworkType.TCP.type
        outbound.getTransportSettingDetails()?.let { transportDetails ->
            config.headerType = transportDetails[0].orEmpty()
            config.host = transportDetails[1].orEmpty()
            config.path = transportDetails[2].orEmpty()
        }

        config.seed = outbound?.streamSettings?.kcpSettings?.seed
        config.quicSecurity = outbound?.streamSettings?.quicSettings?.security
        config.quicKey = outbound?.streamSettings?.quicSettings?.key
        config.mode = if (outbound?.streamSettings?.grpcSettings?.multiMode == true) "multi" else "gun"
        config.serviceName = outbound?.streamSettings?.grpcSettings?.serviceName
        config.authority = outbound?.streamSettings?.grpcSettings?.authority

        config.security = outbound.streamSettings?.security
        val tlsSettings = outbound?.streamSettings?.realitySettings ?: outbound?.streamSettings?.tlsSettings
        config.insecure = tlsSettings?.allowInsecure
        config.sni = tlsSettings?.serverName
        config.fingerPrint = tlsSettings?.fingerprint
        config.alpn = tlsSettings?.alpn?.joinToString(",").removeWhiteSpace().toString()

        config.publicKey = tlsSettings?.publicKey
        config.shortId = tlsSettings?.shortId
        config.spiderX = tlsSettings?.spiderX

        return config
    }

    /**
     * Migrates a SOCKS server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileSocks(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.SOCKS)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()
        config.username = outbound.settings?.servers?.first()?.users?.first()?.user
        config.password = outbound.getPassword()

        return config
    }

    /**
     * Migrates an HTTP server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileHttp(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.HTTP)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()
        config.username = outbound.settings?.servers?.first()?.users?.first()?.user
        config.password = outbound.getPassword()

        return config
    }

    /**
     * Migrates a WireGuard server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileWireguard(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.WIREGUARD)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()

        outbound.settings?.let { wireguard ->
            config.secretKey = wireguard.secretKey
            config.localAddress = (wireguard.address as List<*>).joinToString(",").removeWhiteSpace().toString()
            config.publicKey = wireguard.peers?.getOrNull(0)?.publicKey
            config.mtu = wireguard.mtu
            config.reserved = wireguard.reserved?.joinToString(",").removeWhiteSpace().toString()
        }
        return config
    }

    /**
     * Migrates a Hysteria2 server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileHysteria2(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.HYSTERIA2)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()
        config.password = outbound.getPassword()

        config.security = AppConfig.TLS
        outbound.streamSettings?.tlsSettings?.let { tlsSetting ->
            config.insecure = tlsSetting.allowInsecure
            config.sni = tlsSetting.serverName
            config.alpn = tlsSetting.alpn?.joinToString(",").removeWhiteSpace().orEmpty()

        }
        config.obfsPassword = outbound.settings?.obfsPassword

        return config
    }

    /**
     * Migrates a custom server configuration to a profile item.
     *
     * @param configOld The old server configuration.
     * @return The profile item.
     */
    private fun migrate2ProfileCustom(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()

        return config
    }

    /**
     * Decodes the old server configuration.
     *
     * @param guid The server GUID.
     * @return The old server configuration.
     */
    private fun decodeServerConfigOld(guid: String): ServerConfig? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJson(json, ServerConfig::class.java)
    }
}
