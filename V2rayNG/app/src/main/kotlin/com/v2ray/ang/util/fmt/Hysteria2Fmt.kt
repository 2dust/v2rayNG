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

object Hysteria2Fmt {

    fun parse(str: String): ServerConfig {
        var allowInsecure = settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
        val config = ServerConfig.create(EConfigType.HYSTERIA2)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())

        val queryParam = uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

        config.outboundBean?.streamSettings?.populateTlsSettings(
            V2rayConfig.TLS,
            if ((queryParam["allowInsecure"].orEmpty()) == "1") true else allowInsecure,
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
        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()

        val remark = "#" + Utils.urlEncode(config.remarks)
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

        val query = "?" + dicQuery.toList().joinToString(
            separator = "&",
            transform = { it.first + "=" + it.second })

        val url = String.format(
            "%s@%s:%s",
            outbound.getPassword(),
            Utils.getIpv6Address(outbound.getServerAddress()),
            outbound.getServerPort()
        )
        return url + query + remark
    }

    fun toNativeConfig(config: ServerConfig, socksPort: Int): Hysteria2Bean? {
        val outbound = config.getProxyOutbound() ?: return null
        val tls = outbound.streamSettings?.tlsSettings
        val bean = Hysteria2Bean(
            server = outbound.getServerAddressAndPort(),
            auth = outbound.getPassword(),
            socks5 = Hysteria2Bean.Socks5Bean(
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