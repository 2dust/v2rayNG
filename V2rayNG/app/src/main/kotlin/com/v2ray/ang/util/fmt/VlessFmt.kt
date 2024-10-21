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

object VlessFmt : FmtBase() {

    fun parse(str: String): ServerConfig? {
        var allowInsecure = settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
        val config = ServerConfig.create(EConfigType.VLESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

        val streamSetting = config.outboundBean?.streamSettings ?: return null

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
        config.outboundBean.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = uri.idnHost
            vnext.port = uri.port
            vnext.users[0].id = uri.userInfo
            vnext.users[0].encryption = queryParam["encryption"] ?: "none"
            vnext.users[0].flow = queryParam["flow"].orEmpty()
        }

        val sni = streamSetting.populateTransportSettings(
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
        allowInsecure = if ((queryParam["allowInsecure"].orEmpty()) == "1") true else allowInsecure
        streamSetting.populateTlsSettings(
            queryParam["security"].orEmpty(),
            allowInsecure,
            queryParam["sni"] ?: sni,
            queryParam["fp"].orEmpty(),
            queryParam["alpn"],
            queryParam["pbk"].orEmpty(),
            queryParam["sid"].orEmpty(),
            queryParam["spx"].orEmpty()
        )

        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()

        
        val dicQuery = getStdTransport(outbound, streamSetting)

        outbound.settings?.vnext?.get(0)?.users?.get(0)?.flow?.let {
            if (!TextUtils.isEmpty(it)) {
                dicQuery["flow"] = it
            }
        }
        dicQuery["encryption"] =
            if (outbound.getSecurityEncryption().isNullOrEmpty()) "none"
            else outbound.getSecurityEncryption().orEmpty()

        return toUri(outbound.getServerAddress(), outbound.getServerPort(), outbound.getPassword(), dicQuery, config.remarks)
    }
}