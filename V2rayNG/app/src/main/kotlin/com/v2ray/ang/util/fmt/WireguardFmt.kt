package com.v2ray.ang.util.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ServerConfig
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.extension.removeWhiteSpace
import com.v2ray.ang.util.Utils
import java.net.URI

object WireguardFmt {
    fun parseWireguard(str: String): ServerConfig? {
        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery != null) {
            val config = ServerConfig.create(EConfigType.WIREGUARD)
            config.remarks = Utils.urlDecode(uri.fragment.orEmpty())

            val queryParam = uri.rawQuery.split("&")
                .associate { it.split("=").let { (k, v) -> k to Utils.urlDecode(v) } }

            config.outboundBean?.settings?.let { wireguard ->
                wireguard.secretKey = uri.userInfo
                wireguard.address =
                    (queryParam["address"]
                        ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4).removeWhiteSpace()
                        .split(",")
                wireguard.peers?.get(0)?.publicKey = queryParam["publickey"].orEmpty()
                wireguard.peers?.get(0)?.endpoint =
                    Utils.getIpv6Address(uri.idnHost) + ":${uri.port}"
                wireguard.mtu = Utils.parseInt(queryParam["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
                wireguard.reserved =
                    (queryParam["reserved"] ?: "0,0,0").removeWhiteSpace().split(",")
                        .map { it.toInt() }
            }
            return config
        } else {
            return null
        }
    }

    fun parseWireguardConfFile(str: String): ServerConfig? {
        val config = ServerConfig.create(EConfigType.WIREGUARD)
        val queryParam: MutableMap<String, String> = mutableMapOf()

        var currentSection: String? = null

        str.lines().forEach { line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine.startsWith("[Interface]", ignoreCase = true) -> currentSection = "Interface"
                trimmedLine.startsWith("[Peer]", ignoreCase = true) -> currentSection = "Peer"
                trimmedLine.isBlank() || trimmedLine.startsWith("#") -> Unit  // Skip blank lines or comments
                currentSection != null -> {
                    val (key, value) = trimmedLine.split("=").map { it.trim() }
                    queryParam[key.lowercase()] = value  // Store the key in lowercase for case-insensitivity
                }
            }
        }

        config.outboundBean?.settings?.let { wireguard ->
            wireguard.secretKey = queryParam["privatekey"].orEmpty()
            wireguard.address = (queryParam["address"] ?: AppConfig.WIREGUARD_LOCAL_ADDRESS_V4).removeWhiteSpace().split(",")
            wireguard.peers?.getOrNull(0)?.publicKey = queryParam["publickey"].orEmpty()
            wireguard.peers?.getOrNull(0)?.endpoint = queryParam["endpoint"].orEmpty()
            wireguard.mtu = Utils.parseInt(queryParam["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
            wireguard.reserved = (queryParam["reserved"] ?: "0,0,0").removeWhiteSpace().split(",").map { it.toInt() }
        }

        return config
    }



    fun toUri(config: ServerConfig): String {
        val outbound = config.getProxyOutbound() ?: return ""

        val remark = "#" + Utils.urlEncode(config.remarks)
        val dicQuery = HashMap<String, String>()
        dicQuery["publickey"] =
            Utils.urlEncode(outbound.settings?.peers?.get(0)?.publicKey.toString())
        if (outbound.settings?.reserved != null) {
            dicQuery["reserved"] = Utils.urlEncode(
                Utils.removeWhiteSpace(outbound.settings?.reserved?.joinToString())
                    .toString()
            )
        }
        dicQuery["address"] = Utils.urlEncode(
            Utils.removeWhiteSpace((outbound.settings?.address as List<*>).joinToString())
                .toString()
        )
        if (outbound.settings?.mtu != null) {
            dicQuery["mtu"] = outbound.settings?.mtu.toString()
        }
        val query = "?" + dicQuery.toList().joinToString(
            separator = "&",
            transform = { it.first + "=" + it.second })

        val url = String.format(
            "%s@%s:%s",
            Utils.urlEncode(outbound.getPassword().toString()),
            Utils.getIpv6Address(outbound.getServerAddress()),
            outbound.getServerPort()
        )
        return url + query + remark
    }
}