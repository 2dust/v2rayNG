package com.v2ray.ang.util.fmt

import android.text.TextUtils
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.MmkvManager.settingsStorage
import com.v2ray.ang.util.Utils
import java.net.URI

object TrojanFmt : FmtBase() {

    fun parse(str: String): ServerConfig {
        var allowInsecure = settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
        val config = ServerConfig.create(EConfigType.TROJAN)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())

        var flow = ""
        var fingerprint = config.outboundBean?.streamSettings?.tlsSettings?.fingerprint
        if (uri.rawQuery.isNullOrEmpty()) {
            config.outboundBean?.streamSettings?.populateTlsSettings(
                V2rayConfig.TLS,
                allowInsecure,
                "",
                fingerprint,
                null,
                null,
                null,
                null
            )
        } else {
            val queryParam = uri.rawQuery.split("&")
                .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

            val sni = config.outboundBean?.streamSettings?.populateTransportSettings(
                queryParam["type"] ?: "tcp",
                queryParam["headerType"],
                queryParam["host"],
                queryParam["path"],
                queryParam["seed"],
                queryParam["quicSecurity"],
                queryParam["key"],
                queryParam["mode"],
                queryParam["serviceName"],
                queryParam["authority"]
            )
            fingerprint = queryParam["fp"].orEmpty()
            allowInsecure = if ((queryParam["allowInsecure"].orEmpty()) == "1") true else allowInsecure
            config.outboundBean?.streamSettings?.populateTlsSettings(
                queryParam["security"] ?: V2rayConfig.TLS,
                allowInsecure,
                queryParam["sni"] ?: sni.orEmpty(),
                fingerprint,
                queryParam["alpn"],
                null,
                null,
                null
            )
            flow = queryParam["flow"].orEmpty()
        }
        config.outboundBean?.settings?.servers?.get(0)?.let { server ->
            server.address = uri.idnHost
            server.port = uri.port
            server.password = uri.userInfo
            server.flow = flow
        }

        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()

        
        val dicQuery = getStdTransport(outbound, streamSetting)

        config.outboundBean?.settings?.servers?.get(0)?.flow?.let {
            if (!TextUtils.isEmpty(it)) {
                dicQuery["flow"] = it
            }
        }

        return toUri(outbound.getServerAddress(), outbound.getServerPort(), outbound.getPassword(), dicQuery, config.remarks)
    }
}