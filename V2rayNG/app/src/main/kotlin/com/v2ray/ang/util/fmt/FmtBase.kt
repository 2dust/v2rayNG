package com.v2ray.ang.util.fmt

import android.text.TextUtils
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.Utils

open class FmtBase {
    fun toUri(address: String?, port: Int?, userInfo: String?, dicQuery: HashMap<String, String>?, remark: String): String {
        val query = if (dicQuery != null)
            ("?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.urlEncode(it.second) }))
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.urlEncode(userInfo ?: ""),
            Utils.getIpv6Address(address),
            port
        )

        return "${url}${query}#${Utils.urlEncode(remark)}"
    }

    fun getStdTransport(outbound: V2rayConfig.OutboundBean, streamSetting: V2rayConfig.OutboundBean.StreamSettingsBean): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()

        dicQuery["security"] = streamSetting.security.ifEmpty { "none" }
        (streamSetting.tlsSettings
            ?: streamSetting.realitySettings)?.let { tlsSetting ->
            if (!TextUtils.isEmpty(tlsSetting.serverName)) {
                dicQuery["sni"] = tlsSetting.serverName
            }
            if (!tlsSetting.alpn.isNullOrEmpty() && tlsSetting.alpn.isNotEmpty()) {
                dicQuery["alpn"] =
                    Utils.removeWhiteSpace(tlsSetting.alpn.joinToString(",")).orEmpty()
            }
            if (!TextUtils.isEmpty(tlsSetting.fingerprint)) {
                dicQuery["fp"] = tlsSetting.fingerprint.orEmpty()
            }
            if (!TextUtils.isEmpty(tlsSetting.publicKey)) {
                dicQuery["pbk"] = tlsSetting.publicKey.orEmpty()
            }
            if (!TextUtils.isEmpty(tlsSetting.shortId)) {
                dicQuery["sid"] = tlsSetting.shortId.orEmpty()
            }
            if (!TextUtils.isEmpty(tlsSetting.spiderX)) {
                dicQuery["spx"] = tlsSetting.spiderX.orEmpty()
            }
        }
        dicQuery["type"] =
            streamSetting.network.ifEmpty { V2rayConfig.DEFAULT_NETWORK }

        outbound.getTransportSettingDetails()?.let { transportDetails ->
            when (streamSetting.network) {
                "tcp" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = transportDetails[1]
                    }
                }

                "kcp" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["seed"] = transportDetails[2]
                    }
                }

                "ws", "httpupgrade", "splithttp" -> {
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = transportDetails[1]
                    }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["path"] = transportDetails[2]
                    }
                }

                "http", "h2" -> {
                    dicQuery["type"] = "http"
                    if (!TextUtils.isEmpty(transportDetails[1])) {
                        dicQuery["host"] = transportDetails[1]
                    }
                    if (!TextUtils.isEmpty(transportDetails[2])) {
                        dicQuery["path"] = transportDetails[2]
                    }
                }

                "quic" -> {
                    dicQuery["headerType"] = transportDetails[0].ifEmpty { "none" }
                    dicQuery["quicSecurity"] = transportDetails[1]
                    dicQuery["key"] = transportDetails[2]
                }

                "grpc" -> {
                    dicQuery["mode"] = transportDetails[0]
                    dicQuery["authority"] = transportDetails[1]
                    dicQuery["serviceName"] = transportDetails[2]
                }
            }
        }
        return dicQuery
    }
}