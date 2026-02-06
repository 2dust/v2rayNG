package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.Utils
import java.net.URI

open class FmtBase {
    /**
     * Converts a ProfileItem object to a URI string.
     *
     * @param config the ProfileItem object to convert
     * @param userInfo the user information to include in the URI
     * @param dicQuery the query parameters to include in the URI
     * @return the converted URI string
     */
    fun toUri(config: ProfileItem, userInfo: String?, dicQuery: HashMap<String, String>?): String {
        val query = if (dicQuery != null)
            "?" + dicQuery.toList().joinToString(
                separator = "&",
                transform = { it.first + "=" + Utils.encodeURIComponent(it.second) })
        else ""

        val url = String.format(
            "%s@%s:%s",
            Utils.encodeURIComponent(userInfo ?: ""),
            Utils.getIpv6Address(HttpUtil.toIdnDomain(config.server.orEmpty())),
            config.serverPort
        )

        return "${url}${query}#${Utils.encodeURIComponent(config.remarks)}"
    }

    /**
     * Extracts query parameters from a URI.
     *
     * @param uri the URI to extract query parameters from
     * @return a map of query parameters
     */
    fun getQueryParam(uri: URI): Map<String, String> {
        return uri.rawQuery.split("&")
            .associate { it.split("=").let { (k, v) -> k to Utils.decodeURIComponent(v) } }
    }

    /**
     * Populates a ProfileItem object with values from query parameters.
     *
     * @param config the ProfileItem object to populate
     * @param queryParam the query parameters to use for populating the ProfileItem
     * @param allowInsecure whether to allow insecure connections
     */
    fun getItemFormQuery(config: ProfileItem, queryParam: Map<String, String>, allowInsecure: Boolean) {
        config.network = queryParam["type"] ?: NetworkType.TCP.type
        config.headerType = queryParam["headerType"]
        config.host = queryParam["host"]
        config.path = queryParam["path"]

        config.seed = queryParam["seed"]
        config.quicSecurity = queryParam["quicSecurity"]
        config.quicKey = queryParam["key"]
        config.mode = queryParam["mode"]
        config.serviceName = queryParam["serviceName"]
        config.authority = queryParam["authority"]
        config.xhttpMode = queryParam["mode"]
        config.xhttpExtra = queryParam["extra"]

        config.security = queryParam["security"]
        if (config.security != AppConfig.TLS && config.security != AppConfig.REALITY) {
            config.security = null
        }
        // Support multiple possible query keys for allowInsecure like the C# implementation
        val allowInsecureKeys = arrayOf("insecure", "allowInsecure", "allow_insecure")
        config.insecure = when {
            allowInsecureKeys.any { queryParam[it] == "1" } -> true
            allowInsecureKeys.any { queryParam[it] == "0" } -> false
            else -> allowInsecure
        }
        config.sni = queryParam["sni"]
        config.fingerPrint = queryParam["fp"]
        config.alpn = queryParam["alpn"]
        config.echConfigList = queryParam["ech"]
        config.pinnedCA256 = queryParam["pcs"]
        config.publicKey = queryParam["pbk"]
        config.shortId = queryParam["sid"]
        config.spiderX = queryParam["spx"]
        config.mldsa65Verify = queryParam["pqv"]
        config.flow = queryParam["flow"]
    }

    /**
     * Creates a map of query parameters from a ProfileItem object.
     *
     * @param config the ProfileItem object to create query parameters from
     * @return a map of query parameters
     */
    fun getQueryDic(config: ProfileItem): HashMap<String, String> {
        val dicQuery = HashMap<String, String>()
        dicQuery["security"] = config.security?.ifEmpty { "none" }.orEmpty()
        config.sni?.nullIfBlank()?.let { dicQuery["sni"] = it }
        config.alpn?.nullIfBlank()?.let { dicQuery["alpn"] = it }
        config.echConfigList?.nullIfBlank()?.let { dicQuery["ech"] = it }
        config.pinnedCA256?.nullIfBlank()?.let { dicQuery["pcs"] = it }
        config.fingerPrint?.nullIfBlank()?.let { dicQuery["fp"] = it }
        config.publicKey?.nullIfBlank()?.let { dicQuery["pbk"] = it }
        config.shortId?.nullIfBlank()?.let { dicQuery["sid"] = it }
        config.spiderX?.nullIfBlank()?.let { dicQuery["spx"] = it }
        config.mldsa65Verify?.nullIfBlank()?.let { dicQuery["pqv"] = it }
        config.flow?.nullIfBlank()?.let { dicQuery["flow"] = it }
        // Add two keys for compatibility: "insecure" and "allowInsecure"
        if (config.security == AppConfig.TLS) {
            val insecureFlag = if (config.insecure == true) "1" else "0"
            dicQuery["insecure"] = insecureFlag
            dicQuery["allowInsecure"] = insecureFlag
        }

        val networkType = NetworkType.fromString(config.network)
        dicQuery["type"] = networkType.type

        when (networkType) {
            NetworkType.TCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
            }

            NetworkType.KCP -> {
                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
                config.seed?.nullIfBlank()?.let { dicQuery["seed"] = it }
            }

            NetworkType.WS, NetworkType.HTTP_UPGRADE -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

            NetworkType.XHTTP -> {
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
                config.xhttpMode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.xhttpExtra?.nullIfBlank()?.let { dicQuery["extra"] = it }
            }

            NetworkType.HTTP, NetworkType.H2 -> {
                dicQuery["type"] = "http"
                config.host?.nullIfBlank()?.let { dicQuery["host"] = it }
                config.path?.nullIfBlank()?.let { dicQuery["path"] = it }
            }

//            NetworkType.QUIC -> {
//                dicQuery["headerType"] = config.headerType?.ifEmpty { "none" }.orEmpty()
//                config.quicSecurity?.nullIfBlank()?.let { dicQuery["quicSecurity"] = it }
//                config.quicKey?.nullIfBlank()?.let { dicQuery["key"] = it }
//            }

            NetworkType.GRPC -> {
                config.mode?.nullIfBlank()?.let { dicQuery["mode"] = it }
                config.authority?.nullIfBlank()?.let { dicQuery["authority"] = it }
                config.serviceName?.nullIfBlank()?.let { dicQuery["serviceName"] = it }
            }

            else -> {}
        }

        return dicQuery
    }

    fun getServerAddress(profileItem: ProfileItem): String {
        if (Utils.isPureIpAddress(profileItem.server.orEmpty())) {
            return profileItem.server.orEmpty()
        }

        val domain = HttpUtil.toIdnDomain(profileItem.server.orEmpty())
        if (MmkvManager.decodeSettingsString(AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, "1") != "2") {
            return domain
        }
        //Resolve and replace domain
        val resolvedIps = HttpUtil.resolveHostToIP(domain, MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6))
        if (resolvedIps.isNullOrEmpty()) {
            return domain
        }
        return resolvedIps.first()
    }
}
