package com.v2ray.ang.util.fmt

import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.Hysteria2Bean
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.MmkvManager.settingsStorage
import com.v2ray.ang.util.Utils
import java.net.URI

object Hysteria2Fmt : FmtBase() {

    fun parse(str: String): ServerConfig {
        val allowInsecure = settingsStorage.decodeBool(AppConfig.PREF_ALLOW_INSECURE,false)
        val config = ServerConfig.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())

        val queryParam = uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

        config.outboundBean?.streamSettings?.populateTlsSettings(
            V2rayConfig.TLS,
            if ((queryParam["insecure"].orEmpty()) == "1") true else allowInsecure,
            queryParam["sni"] ?: uri.idnHost,
            null,
            queryParam["alpn"],
            null,
            null,
            null
        )

        config.outboundBean?.settings?.servers?.get(0)?.let { server ->
            server.address = uri.idnHost
            server.port = uri.port
            server.password = uri.userInfo
        }
        if (!queryParam["obfs-password"].isNullOrEmpty()) {
            config.outboundBean?.settings?.obfsPassword = queryParam["obfs-password"]
        }

        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()


        val dicQuery = HashMap<String, String>()
        dicQuery["security"] = streamSetting.security.ifEmpty { "none" }
        streamSetting.tlsSettings?.let { tlsSetting ->
            dicQuery["insecure"] = if (tlsSetting.allowInsecure) "1" else "0"
            if (!TextUtils.isEmpty(tlsSetting.serverName)) {
                dicQuery["sni"] = tlsSetting.serverName
            }
            if (!tlsSetting.alpn.isNullOrEmpty() && tlsSetting.alpn.isNotEmpty()) {
                dicQuery["alpn"] = Utils.removeWhiteSpace(tlsSetting.alpn.joinToString(",")).orEmpty()
            }
        }
        if (!outbound.settings?.obfsPassword.isNullOrEmpty()) {
            dicQuery["obfs"] = "salamander"
            dicQuery["obfs-password"] = outbound.settings?.obfsPassword ?: ""
        }

        return toUri(outbound.getServerAddress(), outbound.getServerPort(), outbound.getPassword(), dicQuery, config.remarks)
    }

    fun toNativeConfig(config: ServerConfig, socksPort: Int): Hysteria2Bean? {
        val outbound = config.getProxyOutbound() ?: return null
        val tls = outbound.streamSettings?.tlsSettings
        val obfs = if (outbound.settings?.obfsPassword.isNullOrEmpty()) null else
            Hysteria2Bean.ObfsBean(
                type = "salamander",
                salamander = Hysteria2Bean.ObfsBean.SalamanderBean(
                    password = outbound.settings?.obfsPassword
                )
            )

        val bean = Hysteria2Bean(
            server = outbound.getServerAddressAndPort(),
            auth = outbound.getPassword(),
            obfs = obfs,
            socks5 = Hysteria2Bean.Socks5Bean(
                listen = "$LOOPBACK:${socksPort}",
            ),
            http = Hysteria2Bean.Socks5Bean(
                listen = "$LOOPBACK:${socksPort}",
            ),
            tls = Hysteria2Bean.TlsBean(
                sni = tls?.serverName ?: outbound.getServerAddress(),
                insecure = tls?.allowInsecure
            )
        )
        return bean
    }
}