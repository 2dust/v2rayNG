package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig.OutboundBean
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

/**
 * Centralizes ProfileItem -> OutboundBean conversion.
 * Most protocol builders mirror the previous *Fmt.toOutbound behavior.
 */
object CoreOutboundBuilder {

    /** Dispatches a profile to protocol-specific outbound builder. */
    fun convert(profileItem: ProfileItem): OutboundBean? {
        val outbound = when (profileItem.configType) {
            EConfigType.VMESS -> toOutboundVmess(profileItem)
            EConfigType.SHADOWSOCKS -> toOutboundShadowsocks(profileItem)
            EConfigType.SOCKS -> toOutboundSocks(profileItem)
            EConfigType.VLESS -> toOutboundVless(profileItem)
            EConfigType.TROJAN -> toOutboundTrojan(profileItem)
            EConfigType.WIREGUARD -> toOutboundWireguard(profileItem)
            EConfigType.HYSTERIA2 -> toOutboundHysteria2(profileItem)
            EConfigType.HTTP -> toOutboundHttp(profileItem)
            else -> null
        }

        outbound ?: return null
        val ret = updateOutboundWithGlobalSettings(outbound)
        if (!ret) return null
        return outbound
    }

    /** Applies global outbound options (mux, protocol-specific tweaks, etc.). */
    private fun updateOutboundWithGlobalSettings(outbound: OutboundBean): Boolean {
        try {
            var muxEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
            val protocol = outbound.protocol
            if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.WIREGUARD.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
                || protocol.equals(EConfigType.HYSTERIA.name, true)
            ) {
                muxEnabled = false
            } else if (outbound.streamSettings?.network == NetworkType.XHTTP.type) {
                muxEnabled = false
            }

            if (muxEnabled) {
                outbound.mux?.enabled = true
                outbound.mux?.concurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8").orEmpty().toInt()
                outbound.mux?.xudpConcurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "16").orEmpty().toInt()
                outbound.mux?.xudpProxyUDP443 = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_QUIC, "reject")
                if (protocol.equals(EConfigType.VLESS.name, true) && outbound.settings?.vnext?.first()?.users?.first()?.flow?.isNotEmpty() == true) {
                    outbound.mux?.concurrency = -1
                }
            } else {
                outbound.mux?.enabled = false
                outbound.mux?.concurrency = -1
            }

        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update outbound with global settings", e)
            return false
        }
        return true
    }

    /** Creates an initial outbound template for a protocol type. */
    fun createInitOutbound(configType: EConfigType): OutboundBean? {
        return when (configType) {
            EConfigType.VMESS,
            EConfigType.VLESS ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutboundBean.OutSettingsBean(
                        vnext = listOf(
                            OutboundBean.OutSettingsBean.VnextBean(
                                users = listOf(OutboundBean.OutSettingsBean.VnextBean.UsersBean())
                            )
                        )
                    ),
                    streamSettings = OutboundBean.StreamSettingsBean()
                )

            EConfigType.SHADOWSOCKS,
            EConfigType.SOCKS,
            EConfigType.HTTP,
            EConfigType.TROJAN ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutboundBean.OutSettingsBean(
                        servers = listOf(OutboundBean.OutSettingsBean.ServersBean())
                    ),
                    streamSettings = OutboundBean.StreamSettingsBean()
                )

            EConfigType.WIREGUARD ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutboundBean.OutSettingsBean(
                        secretKey = "",
                        peers = listOf(OutboundBean.OutSettingsBean.WireGuardBean())
                    )
                )

            EConfigType.HYSTERIA,
            EConfigType.HYSTERIA2 ->
                return OutboundBean(
                    protocol = EConfigType.HYSTERIA.name.lowercase(),
                    settings = OutboundBean.OutSettingsBean(
                        servers = null
                    ),
                    streamSettings = OutboundBean.StreamSettingsBean()
                )

            else -> null
        }
    }

    // ── Per-protocol builders — implementations are identical to each *Fmt.toOutbound ──

    private fun toOutboundVmess(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.VMESS)

        outboundBean?.settings?.vnext?.first()?.let { vnext ->
            vnext.address = getServerAddress(profileItem)
            vnext.port = profileItem.serverPort.orEmpty().toInt()
            vnext.users[0].id = profileItem.password.orEmpty()
            vnext.users[0].security = profileItem.method
        }

        val sni = outboundBean?.streamSettings?.let {
            populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

    private fun toOutboundVless(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.VLESS)

        outboundBean?.settings?.vnext?.first()?.let { vnext ->
            vnext.address = getServerAddress(profileItem)
            vnext.port = profileItem.serverPort.orEmpty().toInt()
            vnext.users[0].id = profileItem.password.orEmpty()
            vnext.users[0].encryption = profileItem.method
            vnext.users[0].flow = profileItem.flow
        }

        val sni = outboundBean?.streamSettings?.let {
            populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

    private fun toOutboundShadowsocks(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.SHADOWSOCKS)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.method = profileItem.method
        }

        val sni = outboundBean?.streamSettings?.let {
            populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

    private fun toOutboundTrojan(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.TROJAN)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.password = profileItem.password
            server.flow = profileItem.flow
        }

        val sni = outboundBean?.streamSettings?.let {
            populateTransportSettings(it, profileItem)
        }

        outboundBean?.streamSettings?.let {
            populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

    private fun toOutboundSocks(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.SOCKS)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            if (profileItem.username.isNotNullEmpty()) {
                val socksUsersBean = OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                socksUsersBean.user = profileItem.username.orEmpty()
                socksUsersBean.pass = profileItem.password.orEmpty()
                server.users = listOf(socksUsersBean)
            }
        }

        return outboundBean
    }

    private fun toOutboundHttp(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.HTTP)

        outboundBean?.settings?.servers?.first()?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            if (profileItem.username.isNotNullEmpty()) {
                val socksUsersBean = OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                socksUsersBean.user = profileItem.username.orEmpty()
                socksUsersBean.pass = profileItem.password.orEmpty()
                server.users = listOf(socksUsersBean)
            }
        }

        return outboundBean
    }

    private fun toOutboundWireguard(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.WIREGUARD)

        val rawAddresses = profileItem.localAddress
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.ifEmpty { null }
            ?: listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4)

        val addresses = if (MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED) == true) {
            rawAddresses
        } else {
            val ipv4Addresses = rawAddresses.filter { !it.contains(":") }
            ipv4Addresses.ifEmpty { listOf(AppConfig.WIREGUARD_LOCAL_ADDRESS_V4) }
        }

        outboundBean?.settings?.let { wireguard ->
            wireguard.secretKey = profileItem.secretKey
            wireguard.address = addresses
            wireguard.peers?.firstOrNull()?.let { peer ->
                peer.publicKey = profileItem.publicKey.orEmpty()
                peer.preSharedKey = profileItem.preSharedKey?.nullIfBlank()
                peer.endpoint = Utils.getIpv6Address(profileItem.server) + ":${profileItem.serverPort}"
            }
            wireguard.mtu = profileItem.mtu
            wireguard.reserved = profileItem.reserved?.takeIf { it.isNotBlank() }?.split(",")?.filter { it.isNotBlank() }?.map { it.trim().toInt() }
        }

        return outboundBean
    }

    private fun toOutboundHysteria2(profileItem: ProfileItem): OutboundBean? {
        val outboundBean = createInitOutbound(EConfigType.HYSTERIA2) ?: return null
        profileItem.network = NetworkType.HYSTERIA.type
        profileItem.alpn = "h3"

        outboundBean.settings?.let { server ->
            server.address = getServerAddress(profileItem)
            server.port = profileItem.serverPort.orEmpty().toInt()
            server.version = 2
        }

        val sni = outboundBean.streamSettings?.let {
            populateTransportSettings(it, profileItem)
        }

        outboundBean.streamSettings?.let {
            populateTlsSettings(it, profileItem, sni)
        }

        return outboundBean
    }

    private fun createTcpHttpRequest(
        host: String?,
        path: String?
    ): OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean {
        val requestString =
            """{"version":"1.1","method":"GET","headers":{"User-Agent":["Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}"""
        val request = JsonUtil.fromJson(
            requestString,
            OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean::class.java
        ) ?: OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean()

        val parsedHost = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        request.headers.Host = parsedHost.ifEmpty { null }
        request.path = path.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("/") }
        return request
    }

    /**
     * Configures transport settings for an outbound connection.
     *
     * Sets up protocol-specific transport options based on the profile settings.
     *
     * @param streamSettings The stream settings to configure
     * @param profileItem The profile containing transport configuration
     * @return The Server Name Indication (SNI) value to use, or null if not applicable
     */
    fun populateTransportSettings(streamSettings: OutboundBean.StreamSettingsBean, profileItem: ProfileItem): String? {
        val transport = profileItem.network.orEmpty()
        val headerType = profileItem.headerType
        val host = profileItem.host
        val path = profileItem.path
        val seed = profileItem.seed
//        val quicSecurity = profileItem.quicSecurity
//        val key = profileItem.quicKey
        val mode = profileItem.mode
        val serviceName = profileItem.serviceName
        val authority = profileItem.authority
        val xhttpMode = profileItem.xhttpMode
        val xhttpExtra = profileItem.xhttpExtra
        val finalMask = profileItem.finalMask
        var sni: String? = null
        streamSettings.network = transport.ifEmpty { NetworkType.TCP.type }
        when (streamSettings.network) {
            NetworkType.TCP.type -> {
                val tcpSetting = OutboundBean.StreamSettingsBean.TcpSettingsBean()
                if (headerType == AppConfig.HEADER_TYPE_HTTP) {
                    tcpSetting.header.type = AppConfig.HEADER_TYPE_HTTP
                    val requestObj = createTcpHttpRequest(host, path)
                    tcpSetting.header.request = requestObj
                    sni = requestObj.headers.Host?.getOrNull(0)
                } else {
                    tcpSetting.header.type = "none"
                    sni = host
                }
                streamSettings.tcpSettings = tcpSetting
            }

            NetworkType.KCP.type -> {
                val kcpSetting = OutboundBean.StreamSettingsBean.KcpSettingsBean()
                profileItem.kcpMtu?.let { kcpSetting.mtu = it }
                profileItem.kcpTti?.let { kcpSetting.tti = it }
                streamSettings.kcpSettings = kcpSetting
                val udpMaskList =
                    mutableListOf<OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean>()
                if (!headerType.isNullOrEmpty() && headerType != "none") {
                    val kcpHeaderType = when {
                        headerType == "wechat-video" -> "wechat"
                        else -> headerType
                    }
                    udpMaskList.add(
                        OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                            type = "mkcp-legacy",
                            settings =
                                OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                                    header = kcpHeaderType,
                                    value = if (headerType == "dns" && !host.isNullOrEmpty()) {
                                        host
                                    } else {
                                        null
                                    }
                                )
                        )
                    )
                }
                if (seed.isNullOrEmpty()) {
                    udpMaskList.add(
                        OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                            type = "mkcp-legacy"
                        )
                    )
                } else {
                    udpMaskList.add(
                        OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                            type = "mkcp-legacy",
                            settings = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                                value = seed
                            )
                        )
                    )
                }
                streamSettings.finalmask = OutboundBean.StreamSettingsBean.FinalMaskBean(
                    udp = udpMaskList.toList()
                )
            }

            NetworkType.WS.type -> {
                val wssetting = OutboundBean.StreamSettingsBean.WsSettingsBean()
                wssetting.host = host.orEmpty()
                sni = host
                wssetting.path = path ?: "/"
                streamSettings.wsSettings = wssetting
            }

            NetworkType.HTTP_UPGRADE.type -> {
                val httpupgradeSetting = OutboundBean.StreamSettingsBean.HttpupgradeSettingsBean()
                httpupgradeSetting.host = host.orEmpty()
                sni = host
                httpupgradeSetting.path = path ?: "/"
                streamSettings.httpupgradeSettings = httpupgradeSetting
            }

            NetworkType.XHTTP.type -> {
                val xhttpSetting = OutboundBean.StreamSettingsBean.XhttpSettingsBean()
                xhttpSetting.host = host.orEmpty()
                sni = host
                xhttpSetting.path = path ?: "/"
                xhttpSetting.mode = xhttpMode
                xhttpSetting.extra = JsonUtil.parseString(xhttpExtra)
                streamSettings.xhttpSettings = xhttpSetting
            }

            NetworkType.H2.type, NetworkType.HTTP.type -> {
                streamSettings.network = NetworkType.H2.type
                val h2Setting = OutboundBean.StreamSettingsBean.HttpSettingsBean()
                h2Setting.host = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                sni = h2Setting.host.getOrNull(0)
                h2Setting.path = path ?: "/"
                streamSettings.httpSettings = h2Setting
            }

//                    "quic" -> {
//                        val quicsetting = QuicSettingBean()
//                        quicsetting.security = quicSecurity ?: "none"
//                        quicsetting.key = key.orEmpty()
//                        quicsetting.header.type = headerType ?: "none"
//                        quicSettings = quicsetting
//                    }

            NetworkType.GRPC.type -> {
                val grpcSetting = OutboundBean.StreamSettingsBean.GrpcSettingsBean()
                grpcSetting.multiMode = mode == "multi"
                grpcSetting.serviceName = serviceName.orEmpty()
                grpcSetting.authority = authority.orEmpty()
                grpcSetting.idle_timeout = 60
                grpcSetting.health_check_timeout = 20
                sni = authority
                streamSettings.grpcSettings = grpcSetting
            }

            NetworkType.HYSTERIA.type -> {
                val hysteriaSetting = OutboundBean.StreamSettingsBean.HysteriaSettingsBean(
                    version = 2,
                    auth = profileItem.password.orEmpty(),
                )
                val quicParams = OutboundBean.StreamSettingsBean.FinalMaskBean.QuicParamsBean(
                    brutalUp = profileItem.bandwidthUp?.nullIfBlank(),
                    brutalDown = profileItem.bandwidthDown?.nullIfBlank(),
                )
                quicParams.congestion = if (quicParams.brutalUp != null || quicParams.brutalDown != null) "brutal" else null
                if (profileItem.portHopping.isNotNullEmpty()) {
                    val rawInterval = profileItem.portHoppingInterval?.trim().nullIfBlank()
                    val interval = if (rawInterval == null) {
                        "30"
                    } else {
                        val singleValue = rawInterval.toIntOrNull()
                        if (singleValue != null) {
                            if (singleValue < 5) {
                                "30"
                            } else {
                                rawInterval
                            }
                        } else {
                            val parts = rawInterval.split('-')
                            if (parts.size == 2) {
                                val start = parts[0].trim().toIntOrNull()
                                val end = parts[1].trim().toIntOrNull()
                                if (start != null && end != null) {
                                    val minStart = maxOf(5, start)
                                    val minEnd = maxOf(minStart, end)
                                    "$minStart-$minEnd"
                                } else {
                                    "30"
                                }
                            } else {
                                "30"
                            }
                        }
                    }
                    quicParams.udpHop = OutboundBean.StreamSettingsBean.FinalMaskBean.QuicParamsBean.UdpHopBean(
                        ports = profileItem.portHopping,
                        interval = interval
                    )
                }
                val finalmask = OutboundBean.StreamSettingsBean.FinalMaskBean(
                    quicParams = quicParams
                )
                if (profileItem.obfsPassword.isNotNullEmpty()) {
                    finalmask.udp = listOf(
                        OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                            type = "salamander",
                            settings = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                                password = profileItem.obfsPassword.orEmpty()
                            )
                        )
                    )
                }
                streamSettings.hysteriaSettings = hysteriaSetting
                streamSettings.finalmask = finalmask
            }
        }
        finalMask?.let {
            val parsedFinalMask = JsonUtil.parseString(finalMask)
            if (parsedFinalMask != null) {
                streamSettings.finalmask = parsedFinalMask
            } else {
                LogUtil.w("V2rayConfigManager", "Invalid finalMask JSON, keeping previously generated finalmask")
            }
        }
        return sni
    }

    /**
     * Configures TLS or REALITY security settings for an outbound connection.
     *
     * Sets up security-related parameters like certificates, fingerprints, and SNI.
     *
     * @param streamSettings The stream settings to configure
     * @param profileItem The profile containing security configuration
     * @param sniExt An external SNI value to use if the profile doesn't specify one
     */
    fun populateTlsSettings(streamSettings: OutboundBean.StreamSettingsBean, profileItem: ProfileItem, sniExt: String?) {
        val streamSecurity = profileItem.security.orEmpty()
        val allowInsecure = profileItem.insecure == true && profileItem.pinnedCA256.isNullOrEmpty()
        val sni = if (profileItem.sni.isNullOrEmpty()) {
            when {
                sniExt.isNotNullEmpty() && Utils.isDomainName(sniExt) -> sniExt
                profileItem.server.isNotNullEmpty() && Utils.isDomainName(profileItem.server) -> profileItem.server
                else -> sniExt
            }
        } else {
            profileItem.sni
        }

        streamSettings.security = streamSecurity.nullIfBlank()
        if (streamSettings.security == null) return
        val tlsSetting = OutboundBean.StreamSettingsBean.TlsSettingsBean(
            allowInsecure = allowInsecure,
            serverName = sni.nullIfBlank(),
            fingerprint = profileItem.fingerPrint.nullIfBlank(),
            alpn = profileItem.alpn?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.takeIf { !it.isNullOrEmpty() },
            echConfigList = profileItem.echConfigList.nullIfBlank(),
            verifyPeerCertByName = profileItem.verifyPeerCertByName.nullIfBlank(),
            pinnedPeerCertSha256 = profileItem.pinnedCA256.nullIfBlank(),
            publicKey = profileItem.publicKey.nullIfBlank(),
            shortId = profileItem.shortId.nullIfBlank(),
            spiderX = profileItem.spiderX.nullIfBlank(),
            mldsa65Verify = profileItem.mldsa65Verify.nullIfBlank(),
        )
        if (streamSettings.security == AppConfig.TLS) {
            streamSettings.tlsSettings = tlsSetting
            streamSettings.realitySettings = null
        } else if (streamSettings.security == AppConfig.REALITY) {
            streamSettings.tlsSettings = null
            streamSettings.realitySettings = tlsSetting
        }

        if (profileItem.finalMask.isNullOrEmpty()) {
            updateOutboundFragment(streamSettings)
        }
    }

    /**
     * Updates the outbound with fragment settings for traffic optimization.
     *
     * Configures packet fragmentation for TLS and REALITY protocols if enabled.
     *
     * @param streamSettings The streamSettings object to be modified
     * @return true if fragment configuration was successful, false otherwise
     */
    private fun updateOutboundFragment(streamSettings: OutboundBean.StreamSettingsBean): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false) == false) {
                return true
            }
            if (streamSettings.security != AppConfig.TLS
                && streamSettings.security != AppConfig.REALITY
            ) {
                return true
            }
            if (streamSettings.sockopt?.dialerProxy.isNotNullEmpty()) {
                return true
            }

            var packets =
                MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_PACKETS) ?: "tlshello"
            if (streamSettings.security == AppConfig.REALITY
                && packets == "tlshello"
            ) {
                packets = "1-3"
            }

            val fragmentMask = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                type = "fragment",
                settings = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                    packets = packets,
                    length = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_LENGTH)
                        ?: "50-100",
                    delay = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_INTERVAL)
                        ?: "10-20",
                    maxSplit = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_MAXSPLIT)
                        ?: "10"
                )
            )
            val noiseMask = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean(
                type = "noise",
                settings = OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean(
                    noise = listOf(
                        OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean.MaskSettingsBean.NoiseMaskBean(
                            rand = "10-20",
                            delay = "10-16",
                        )
                    )
                )
            )

            val finalMaskObj = streamSettings.finalmask?.let { existingFinalMask ->
                JsonUtil.parseString(JsonUtil.toJson(existingFinalMask))
            } ?: JsonObject()

            // finalmask.tcp / finalmask.udp are arrays; prepend mask at index 0.
            fun prependMask(scope: String, mask: OutboundBean.StreamSettingsBean.FinalMaskBean.MaskBean) {
                val current = finalMaskObj.get(scope)
                if (current != null && current.isJsonArray && current.asJsonArray.size() > 0) {
                    return
                }

                val newArray = JsonArray()
                newArray.add(JsonUtil.parseString(JsonUtil.toJson(mask)))

                if (current != null && current.isJsonArray) {
                    current.asJsonArray.forEach { newArray.add(it) }
                }
                finalMaskObj.add(scope, newArray)
            }

            prependMask("tcp", fragmentMask)
            prependMask("udp", noiseMask)
            streamSettings.finalmask = finalMaskObj
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to update outbound fragment", e)
            return false
        }
        return true
    }

    private fun getServerAddress(profileItem: ProfileItem): String {
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
