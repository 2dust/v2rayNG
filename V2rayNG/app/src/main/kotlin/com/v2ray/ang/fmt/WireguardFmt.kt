package com.v2ray.ang.fmt

import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.extension.idnHost
import com.v2ray.ang.util.Utils
import java.net.URI
import kotlin.text.orEmpty

object WireguardFmt : FmtBase() {
    fun parse(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.WIREGUARD)

        val uri = URI(Utils.fixIllegalUrl(str))
        if (uri.rawQuery.isNullOrEmpty()) return null
        val queryParam = getQueryParam(uri)

        config.remarks = Utils.urlDecode(uri.fragment.orEmpty())
        config.server = uri.idnHost
        config.serverPort = uri.port.toString()

        config.secretKey = uri.userInfo
        config.localAddress = (queryParam["address"] ?: WIREGUARD_LOCAL_ADDRESS_V4)
        config.publicKey = queryParam["publickey"].orEmpty()
        config.mtu = Utils.parseInt(queryParam["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.reserved = (queryParam["reserved"] ?: "0,0,0")

        return config
    }

    fun parseWireguardConfFile(str: String): ProfileItem? {
        val config = ProfileItem.create(EConfigType.WIREGUARD)
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

        config.secretKey = queryParam["privatekey"].orEmpty()
        config.localAddress = (queryParam["address"] ?: WIREGUARD_LOCAL_ADDRESS_V4)
        config.publicKey = queryParam["publickey"].orEmpty()
        config.mtu = Utils.parseInt(queryParam["mtu"] ?: AppConfig.WIREGUARD_LOCAL_MTU)
        config.reserved = (queryParam["reserved"] ?: "0,0,0")

        return config
    }


    fun toUri(config: ProfileItem): String {
        val dicQuery = HashMap<String, String>()

        dicQuery["publickey"] = config.publicKey.orEmpty()
        if (config.reserved != null) {
            dicQuery["reserved"] = Utils.removeWhiteSpace(config.reserved).orEmpty()
        }
        dicQuery["address"] = Utils.removeWhiteSpace(config.localAddress).orEmpty()
        if (config.mtu != null) {
            dicQuery["mtu"] = config.mtu.toString()
        }

        return toUri(config, config.secretKey, dicQuery)
    }

    fun toOutbound(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = OutboundBean.create(EConfigType.WIREGUARD)

        outboundBean?.settings?.let { wireguard ->
            wireguard.secretKey = profileItem.secretKey
            wireguard.address = (profileItem.localAddress ?: WIREGUARD_LOCAL_ADDRESS_V4).split(",")
            wireguard.peers?.get(0)?.publicKey = profileItem.publicKey.orEmpty()
            wireguard.peers?.get(0)?.endpoint = Utils.getIpv6Address(profileItem.server) + ":${profileItem.serverPort}"
            wireguard.mtu = profileItem.mtu?.toInt()
            wireguard.reserved = profileItem.reserved?.split(",")?.map { it.toInt() }
        }

        return outboundBean
    }


}