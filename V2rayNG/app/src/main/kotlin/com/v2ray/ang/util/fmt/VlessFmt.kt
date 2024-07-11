package com.v2ray.ang.util.fmt

import android.text.TextUtils
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import java.net.URI

object VlessFmt {
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    fun parseVless(str: String): ServerConfig? {
        var allowInsecure = settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
        val config = ServerConfig.create(EConfigType.VLESS)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

        val streamSetting = config.outboundBean?.streamSettings ?: return null

        config.remarks = Utils.urlDecode(uri.fragment ?: "")
        config.outboundBean.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = uri.idnHost
            vnext.port = uri.port
            vnext.users[0].id = uri.userInfo
            vnext.users[0].encryption = queryParam["encryption"] ?: "none"
            vnext.users[0].flow = queryParam["flow"] ?: ""
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
        allowInsecure = if ((queryParam["allowInsecure"] ?: "") == "1") true else allowInsecure
        streamSetting.populateTlsSettings(
            queryParam["security"] ?: "",
            allowInsecure,
            queryParam["sni"] ?: sni,
            queryParam["fp"] ?: "",
            queryParam["alpn"],
            queryParam["pbk"] ?: "",
            queryParam["sid"] ?: "",
            queryParam["spx"] ?: ""
        )

        return config
    }

    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""
        val streamSetting = outbound.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean()

        val remark = "#" + Utils.urlEncode(config.remarks)
        val dicQuery = HashMap<String, String>()
        outbound.settings?.vnext?.get(0)?.users?.get(0)?.flow?.let {
            if (!TextUtils.isEmpty(it)) {
                dicQuery["flow"] = it
            }
        }
        dicQuery["encryption"] =
            if (outbound.getSecurityEncryption().isNullOrEmpty()) "none"
            else outbound.getSecurityEncryption().orEmpty()


        dicQuery["security"] = streamSetting.security.ifEmpty { "none" }
        (streamSetting.tlsSettings
            ?: streamSetting.realitySettings)?.let { tlsSetting ->
            if (!TextUtils.isEmpty(tlsSetting.serverName)) {
                dicQuery["sni"] = tlsSetting.serverName
            }
            if (!tlsSetting.alpn.isNullOrEmpty() && tlsSetting.alpn.isNotEmpty()) {
                dicQuery["alpn"] =
                    Utils.removeWhiteSpace(tlsSetting.alpn.joinToString()).orEmpty()
            }
            if (!TextUtils.isEmpty(tlsSetting.fingerprint)) {
                dicQuery["fp"] = tlsSetting.fingerprint ?: ""
            }
            if (!TextUtils.isEmpty(tlsSetting.publicKey)) {
                dicQuery["pbk"] = tlsSetting.publicKey ?: ""
            }
            if (!TextUtils.isEmpty(tlsSetting.shortId)) {
                dicQuery["sid"] = tlsSetting.shortId ?: ""
            }
            if (!TextUtils.isEmpty(tlsSetting.spiderX)) {
                dicQuery["spx"] = Utils.urlEncode(tlsSetting.spiderX ?: "")
            }
        }
        dicQuery["type"] =
            streamSetting.network.ifEmpty { V2rayConfig.DEFAULT_NETWORK }

        outbound.getTransportSettingDetails()?.let { transportDetails ->
            when (streamSetting.network) {
                "tcp" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = Utils.urlEncode(transportDetails[1])
                    }
                }

                "kcp" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["seed"] = Utils.urlEncode(transportDetails[2])
                    }
                }

                "ws", "httpupgrade", "splithttp" -> {
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = Utils.urlEncode(transportDetails[1])
                    }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["path"] = Utils.urlEncode(transportDetails[2])
                    }
                }

                "http", "h2" -> {
                    dicQuery["type"] = "http"
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = Utils.urlEncode(transportDetails[1])
                    }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["path"] = Utils.urlEncode(transportDetails[2])
                    }
                }

                "quic" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    dicQuery["quicSecurity"] = Utils.urlEncode(transportDetails[1])
                    dicQuery["key"] = Utils.urlEncode(transportDetails[2])
                }

                "grpc" -> {
                    dicQuery["mode"] = transportDetails[0]
                    dicQuery["authority"] = Utils.urlEncode(transportDetails[1])
                    dicQuery["serviceName"] = Utils.urlEncode(transportDetails[2])
                }
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
}