package com.v2ray.ang.util.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.util.Utils
import java.net.URI

open class FmtBase {
    fun toUri(config: ProfileItem, userInfo: String?, dicQuery: HashMap<String, String>?): String {
        val query = if (dicQuery != null)
            ("?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.urlEncode(it.second) }))
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.urlEncode(userInfo ?: ""),
            Utils.getIpv6Address(config.server),
            config.serverPort
        )

        return "${url}${query}#${Utils.urlEncode(config.remarks)}"
    }

    fun getQueryParam(uri: URI): Map<String, String> {
        return uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }
    }

    fun getQueryDic(config: ProfileItem): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()

        dicQuery["security"] = config.security?.ifEmpty { "none" }.orEmpty()
        config.sni.let { if (it.isNotNullEmpty()) dicQuery["sni"] = it.orEmpty() }
        config.alpn.let { if (it.isNotNullEmpty()) dicQuery["alpn"] = it.orEmpty() }
        config.fingerPrint.let { if (it.isNotNullEmpty()) dicQuery["fp"] = it.orEmpty() }
        config.publicKey.let { if (it.isNotNullEmpty()) dicQuery["pbk"] = it.orEmpty() }
        config.shortId.let { if (it.isNotNullEmpty()) dicQuery["sid"] = it.orEmpty() }
        config.spiderX.let { if (it.isNotNullEmpty()) dicQuery["spx"] = it.orEmpty() }
        config.flow.let { if (it.isNotNullEmpty()) dicQuery["flow"] = it.orEmpty() }

        dicQuery["type"] = config.network?.ifEmpty { AppConfig.DEFAULT_NETWORK }.orEmpty()

        when (config.network) {
            "tcp" -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
            }

            "kcp" -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.seed.let { if (it.isNotNullEmpty()) dicQuery["seed"] = it.orEmpty() }
            }

            "ws", "httpupgrade", "splithttp" -> {
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
                config.path.let { if (it.isNotNullEmpty()) dicQuery["path"] = it.orEmpty() }
            }

            "http", "h2" -> {
                dicQuery["type"] = "http"
                config.host.let { if (it.isNotNullEmpty()) dicQuery["host"] = it.orEmpty() }
                config.path.let { if (it.isNotNullEmpty()) dicQuery["path"] = it.orEmpty() }
            }

            "quic" -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.quicSecurity.let { if (it.isNotNullEmpty()) dicQuery["quicSecurity"] = it.orEmpty() }
                config.quicKey.let { if (it.isNotNullEmpty()) dicQuery["key"] = it.orEmpty() }
            }

            "grpc" -> {
                config.mode.let { if (it.isNotNullEmpty()) dicQuery["mode"] = it.orEmpty() }
                config.authority.let { if (it.isNotNullEmpty()) dicQuery["authority"] = it.orEmpty() }
                config.serviceName.let { if (it.isNotNullEmpty()) dicQuery["serviceName"] = it.orEmpty() }
            }
        }

        return dicQuery
    }

}