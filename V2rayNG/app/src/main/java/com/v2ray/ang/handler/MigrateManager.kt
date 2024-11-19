package com.v2ray.ang.handler

import android.util.Log
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.handler.MmkvManager.decodeServerConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

object MigrateManager {
    private const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }

    fun migrateServerConfig2Profile(): Boolean {
        if (serverStorage.count().toInt() == 0) {
            return false
        }
        val serverList = serverStorage.allKeys() ?: return false
        Log.d(ANG_PACKAGE, "migrateServerConfig2Profile-" + serverList.count())

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
            Log.d(ANG_PACKAGE, "migrateServerConfig2Profile-" + config.remarks)
        }
        Log.d(ANG_PACKAGE, "migrateServerConfig2Profile-end")
        return true
    }

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
        config.alpn = Utils.removeWhiteSpace(tlsSettings?.alpn?.joinToString(",")).toString()

        config.publicKey = tlsSettings?.publicKey
        config.shortId = tlsSettings?.shortId
        config.spiderX = tlsSettings?.spiderX

        return config
    }

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

    private fun migrate2ProfileWireguard(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.WIREGUARD)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()

        outbound.settings?.let { wireguard ->
            config.secretKey = wireguard.secretKey
            config.localAddress = Utils.removeWhiteSpace((wireguard.address as List<*>).joinToString(",")).toString()
            config.publicKey = wireguard.peers?.getOrNull(0)?.publicKey
            config.mtu = wireguard.mtu
            config.reserved = Utils.removeWhiteSpace(wireguard.reserved?.joinToString(",")).toString()
        }
        return config
    }

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
            config.alpn = Utils.removeWhiteSpace(tlsSetting.alpn?.joinToString(",")).orEmpty()

        }
        config.obfsPassword = outbound.settings?.obfsPassword

        return config
    }

    private fun migrate2ProfileCustom(configOld: ServerConfig): ProfileItem? {
        val config = ProfileItem.create(EConfigType.CUSTOM)

        val outbound = configOld.getProxyOutbound() ?: return null
        config.remarks = configOld.remarks
        config.server = outbound.getServerAddress()
        config.serverPort = outbound.getServerPort().toString()

        return config
    }


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
