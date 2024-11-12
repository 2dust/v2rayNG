package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import java.net.URI
import kotlin.text.orEmpty

object TrojanFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        var allowInsecure = MmkvManager.decodeSettingsBool(AppConfig.PREF_ALLOW_INSECURE, false)
        val config = ProfileItem.create(EConfigType.TROJAN)

        val uri = URI(Utils.fixIllegalUrl(str))
        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()
        config.password = uri.userInfo

        if (uri.rawQuery.isNullOrEmpty()) {
            config.network = "tcp"
            config.security = AppConfig.TLS
            config.insecure = allowInsecure
        } else {
            val queryParam = getQueryParam(uri)

            config.network = queryParam["type"] ?: "tcp"
            config.headerType = queryParam["headerType"]
            config.host = queryParam["host"]
            config.path = queryParam["path"]

            config.seed = queryParam["seed"]
            config.quicSecurity = queryParam["quicSecurity"]
            config.quicKey = queryParam["key"]
            config.mode = queryParam["mode"]
            config.serviceName = queryParam["serviceName"]
            config.authority = queryParam["authority"]

            config.security = queryParam["security"] ?: AppConfig.TLS
            config.insecure = if (queryParam["allowInsecure"].isNullOrEmpty()) {
                allowInsecure
            } else {
                queryParam["allowInsecure"].orEmpty() == "1"
            }
            config.sni = queryParam["sni"]
            config.fingerPrint = queryParam["fp"]
            config.alpn = queryParam["alpn"]
            config.publicKey = queryParam["pbk"]
            config.shortId = queryParam["sid"]
            config.spiderX = queryParam["spx"]
            config.flow = queryParam["flow"]
        }

        return config
    }

    fun toUri(config: ProfileItem): String {
        val dicQuery = getQueryDic(config)

        return toUri(config, config.password, dicQuery)
    }

    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.TROJAN)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = profileItem.server.orEmpty()
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.flow = profileItem.flow
        }

        outboundBean?.streamSettings?.populateTransportSettings(
            profileItem.network.orEmpty(),
            profileItem.headerType,
            profileItem.host,
            profileItem.path,
            profileItem.seed,
            profileItem.quicSecurity,
            profileItem.quicKey,
            profileItem.mode,
            profileItem.serviceName,
            profileItem.authority,
        )

        outboundBean?.streamSettings?.populateTlsSettings(
            profileItem.security.orEmpty(),
            profileItem.insecure == true,
            profileItem.sni,
            profileItem.fingerPrint,
            profileItem.alpn,
            profileItem.publicKey,
            profileItem.shortId,
            profileItem.spiderX,
        )

        return outboundBean
    }
}