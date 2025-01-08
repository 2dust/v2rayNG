package com.v2ray.ang.handler

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.DEFAULT_NETWORK
import com.v2ray.ang.AppConfig.DNS_ALIDNS_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_ALIDNS_DOMAIN
import com.v2ray.ang.AppConfig.DNS_CLOUDFLARE_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_CLOUDFLARE_DOMAIN
import com.v2ray.ang.AppConfig.DNS_DNSPOD_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_DNSPOD_DOMAIN
import com.v2ray.ang.AppConfig.DNS_GOOGLE_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_GOOGLE_DOMAIN
import com.v2ray.ang.AppConfig.DNS_QUAD9_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_QUAD9_DOMAIN
import com.v2ray.ang.AppConfig.DNS_YANDEX_ADDRESSES
import com.v2ray.ang.AppConfig.DNS_YANDEX_DOMAIN
import com.v2ray.ang.AppConfig.GEOIP_CN
import com.v2ray.ang.AppConfig.GEOSITE_CN
import com.v2ray.ang.AppConfig.GEOSITE_PRIVATE
import com.v2ray.ang.AppConfig.GOOGLEAPIS_CN_DOMAIN
import com.v2ray.ang.AppConfig.GOOGLEAPIS_COM_DOMAIN
import com.v2ray.ang.AppConfig.HEADER_TYPE_HTTP
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.AppConfig.PROTOCOL_FREEDOM
import com.v2ray.ang.AppConfig.TAG_BLOCKED
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.AppConfig.TAG_FRAGMENT
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.v2ray.ang.AppConfig.WIREGUARD_LOCAL_ADDRESS_V6
import com.v2ray.ang.dto.ConfigResult
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.NetworkType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.dto.RulesetItem
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.V2rayConfig.RoutingBean.RulesBean
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.fmt.HttpFmt
import com.v2ray.ang.fmt.Hysteria2Fmt
import com.v2ray.ang.fmt.ShadowsocksFmt
import com.v2ray.ang.fmt.SocksFmt
import com.v2ray.ang.fmt.TrojanFmt
import com.v2ray.ang.fmt.VlessFmt
import com.v2ray.ang.fmt.VmessFmt
import com.v2ray.ang.fmt.WireguardFmt
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils

object V2rayConfigManager {

    fun getV2rayConfig(context: Context, guid: String): ConfigResult {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ConfigResult(false)
            if (config.configType == EConfigType.CUSTOM) {
                val raw = MmkvManager.decodeServerRaw(guid) ?: return ConfigResult(false)
                val domainPort = config.getServerAddressAndPort()
                return ConfigResult(true, guid, raw, domainPort)
            }

            val result = getV2rayNonCustomConfig(context, config)
            //Log.d(ANG_PACKAGE, result.content)
            result.guid = guid
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return ConfigResult(false)
        }
    }

    private fun getV2rayNonCustomConfig(context: Context, config: ProfileItem): ConfigResult {
        val result = ConfigResult(false)

        val address = config.server ?: return result
        if (!Utils.isIpAddress(address)) {
            if (!Utils.isValidUrl(address)) {
                Log.d(ANG_PACKAGE, "$address is an invalid ip or domain")
                return result
            }
        }

        //取得默认配置
        val assets = Utils.readTextFromAssets(context, "v2ray_config.json")
        if (TextUtils.isEmpty(assets)) {
            return result
        }
        val v2rayConfig = JsonUtil.fromJson(assets, V2rayConfig::class.java) ?: return result
        v2rayConfig.log.loglevel =
            MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = config.remarks

        inbounds(v2rayConfig)

        val isPlugin = config.configType == EConfigType.HYSTERIA2
        val retOut = outbounds(v2rayConfig, config, isPlugin) ?: return result
        val retMore = moreOutbounds(v2rayConfig, config.subscriptionId, isPlugin)

        routing(v2rayConfig)

        fakedns(v2rayConfig)

        dns(v2rayConfig)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            customLocalDns(v2rayConfig)
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

        result.status = true
        result.content = v2rayConfig.toPrettyPrinting()
        result.domainPort = if (retMore.first) retMore.second else retOut.second
        return result
    }

    private fun inbounds(v2rayConfig: V2rayConfig): Boolean {
        try {
            val socksPort = SettingsManager.getSocksPort()

            v2rayConfig.inbounds.forEach { curInbound ->
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) != true) {
                    //bind all inbounds to localhost if the user requests
                    curInbound.listen = LOOPBACK
                }
            }
            v2rayConfig.inbounds[0].port = socksPort
            val fakedns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
            val sniffAllTlsAndHttp =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true) != false
            v2rayConfig.inbounds[0].sniffing?.enabled = fakedns || sniffAllTlsAndHttp
            v2rayConfig.inbounds[0].sniffing?.routeOnly =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
            if (!sniffAllTlsAndHttp) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.clear()
            }
            if (fakedns) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.add("fakedns")
            }

            if (Utils.isXray()) {
                v2rayConfig.inbounds.removeAt(1)
            } else {
                val httpPort = SettingsManager.getHttpPort()
                v2rayConfig.inbounds[1].port = httpPort
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun outbounds(v2rayConfig: V2rayConfig, config: ProfileItem, isPlugin: Boolean): Pair<Boolean, String>? {
        if (isPlugin) {
            val socksPort = Utils.findFreePort(listOf(100 + SettingsManager.getSocksPort(), 0))
            val outboundNew = V2rayConfig.OutboundBean(
                mux = null,
                protocol = EConfigType.SOCKS.name.lowercase(),
                settings = V2rayConfig.OutboundBean.OutSettingsBean(
                    servers = listOf(
                        V2rayConfig.OutboundBean.OutSettingsBean.ServersBean(
                            address = LOOPBACK,
                            port = socksPort
                        )
                    )
                )
            )
            if (v2rayConfig.outbounds.isNotEmpty()) {
                v2rayConfig.outbounds[0] = outboundNew
            } else {
                v2rayConfig.outbounds.add(outboundNew)
            }
            return Pair(true, outboundNew.getServerAddressAndPort())
        }

        val outbound = getProxyOutbound(config) ?: return null
        val ret = updateOutboundWithGlobalSettings(outbound)
        if (!ret) return null

        if (v2rayConfig.outbounds.isNotEmpty()) {
            v2rayConfig.outbounds[0] = outbound
        } else {
            v2rayConfig.outbounds.add(outbound)
        }

        updateOutboundFragment(v2rayConfig)
        return Pair(true, config.getServerAddressAndPort())
    }

    private fun fakedns(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true
            && MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
        ) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
        }
    }

    private fun routing(v2rayConfig: V2rayConfig): Boolean {
        try {

            v2rayConfig.routing.domainStrategy =
                MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
                    ?: "IPIfNonMatch"

            val rulesetItems = MmkvManager.decodeRoutingRulesets()
            rulesetItems?.forEach { key ->
                routingUserRule(key, v2rayConfig)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun routingUserRule(item: RulesetItem?, v2rayConfig: V2rayConfig) {
        try {
            if (item == null || !item.enabled) {
                return
            }

            val rule = JsonUtil.fromJson(JsonUtil.toJson(item), RulesBean::class.java) ?: return

            v2rayConfig.routing.rules.add(rule)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun userRule2Domain(tag: String): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && key.outboundTag == tag && !key.domain.isNullOrEmpty()) {
                key.domain?.forEach {
                    if (it != GEOSITE_PRIVATE
                        && (it.startsWith("geosite:") || it.startsWith("domain:"))
                    ) {
                        domain.add(it)
                    }
                }
            }
        }

        return domain
    }

    private fun customLocalDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
                val geositeCn = arrayListOf(GEOSITE_CN)
                val proxyDomain = userRule2Domain(TAG_PROXY)
                val directDomain = userRule2Domain(TAG_DIRECT)
                // fakedns with all domains to make it always top priority
                v2rayConfig.dns.servers?.add(
                    0,
                    V2rayConfig.DnsBean.ServersBean(
                        address = "fakedns",
                        domains = geositeCn.plus(proxyDomain).plus(directDomain)
                    )
                )
            }

            // DNS inbound对象
            val remoteDns = Utils.getRemoteDnsServers()
            if (v2rayConfig.inbounds.none { e -> e.protocol == "dokodemo-door" && e.tag == "dns-in" }) {
                val dnsInboundSettings = V2rayConfig.InboundBean.InSettingsBean(
                    address = if (Utils.isPureIpAddress(remoteDns.first())) remoteDns.first() else AppConfig.DNS_PROXY,
                    port = 53,
                    network = "tcp,udp"
                )

                val localDnsPort = Utils.parseInt(
                    MmkvManager.decodeSettingsString(AppConfig.PREF_LOCAL_DNS_PORT),
                    AppConfig.PORT_LOCAL_DNS.toInt()
                )
                v2rayConfig.inbounds.add(
                    V2rayConfig.InboundBean(
                        tag = "dns-in",
                        port = localDnsPort,
                        listen = LOOPBACK,
                        protocol = "dokodemo-door",
                        settings = dnsInboundSettings,
                        sniffing = null
                    )
                )
            }

            // DNS outbound对象
            if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
                v2rayConfig.outbounds.add(
                    V2rayConfig.OutboundBean(
                        protocol = "dns",
                        tag = "dns-out",
                        settings = null,
                        streamSettings = null,
                        mux = null
                    )
                )
            }

            // DNS routing tag
            v2rayConfig.routing.rules.add(
                0, RulesBean(
                    inboundTag = arrayListOf("dns-in"),
                    outboundTag = "dns-out",
                    domain = null
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun dns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val hosts = mutableMapOf<String, Any>()
            val servers = ArrayList<Any>()

            //remote Dns
            val remoteDns = Utils.getRemoteDnsServers()
            val proxyDomain = userRule2Domain(TAG_PROXY)
            remoteDns.forEach {
                servers.add(it)
            }
            if (proxyDomain.isNotEmpty()) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = remoteDns.first(),
                        domains = proxyDomain,
                    )
                )
            }

            // domestic DNS
            val domesticDns = Utils.getDomesticDnsServers()
            val directDomain = userRule2Domain(TAG_DIRECT)
            val isCnRoutingMode = directDomain.contains(GEOSITE_CN)
            val geoipCn = arrayListOf(GEOIP_CN)
            if (directDomain.isNotEmpty()) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = domesticDns.first(),
                        domains = directDomain,
                        expectIPs = if (isCnRoutingMode) geoipCn else null,
                        skipFallback = true
                    )
                )
            }

            if (Utils.isPureIpAddress(domesticDns.first())) {
                v2rayConfig.routing.rules.add(
                    0, RulesBean(
                        outboundTag = TAG_DIRECT,
                        port = "53",
                        ip = arrayListOf(domesticDns.first()),
                        domain = null
                    )
                )
            }

            //User DNS hosts
            try {
                val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
                if (userHosts.isNotNullEmpty()) {
                    var userHostsMap = userHosts?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.filter { it.contains(":") }
                        ?.associate { it.split(":").let { (k, v) -> k to v } }
                    if (userHostsMap != null) hosts.putAll(userHostsMap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            //block dns
            val blkDomain = userRule2Domain(TAG_BLOCKED)
            if (blkDomain.isNotEmpty()) {
                hosts.putAll(blkDomain.map { it to LOOPBACK })
            }

            // hardcode googleapi rule to fix play store problems
            hosts[GOOGLEAPIS_CN_DOMAIN] = GOOGLEAPIS_COM_DOMAIN

            // hardcode popular Android Private DNS rule to fix localhost DNS problem
            hosts[DNS_ALIDNS_DOMAIN] = DNS_ALIDNS_ADDRESSES
            hosts[DNS_CLOUDFLARE_DOMAIN] = DNS_CLOUDFLARE_ADDRESSES
            hosts[DNS_DNSPOD_DOMAIN] = DNS_DNSPOD_ADDRESSES
            hosts[DNS_GOOGLE_DOMAIN] = DNS_GOOGLE_ADDRESSES
            hosts[DNS_QUAD9_DOMAIN] = DNS_QUAD9_ADDRESSES
            hosts[DNS_YANDEX_DOMAIN] = DNS_YANDEX_ADDRESSES


            // DNS dns对象
            v2rayConfig.dns = V2rayConfig.DnsBean(
                servers = servers,
                hosts = hosts
            )

            // DNS routing
            if (Utils.isPureIpAddress(remoteDns.first())) {
                v2rayConfig.routing.rules.add(
                    0, RulesBean(
                        outboundTag = TAG_PROXY,
                        port = "53",
                        ip = arrayListOf(remoteDns.first()),
                        domain = null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun updateOutboundWithGlobalSettings(outbound: V2rayConfig.OutboundBean): Boolean {
        try {
            var muxEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
            val protocol = outbound.protocol
            if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.WIREGUARD.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
            ) {
                muxEnabled = false
            } else if (outbound.streamSettings?.network == NetworkType.XHTTP.type) {
                muxEnabled = false
            }

            if (muxEnabled == true) {
                outbound.mux?.enabled = true
                outbound.mux?.concurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8").orEmpty().toInt()
                outbound.mux?.xudpConcurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "16").orEmpty().toInt()
                outbound.mux?.xudpProxyUDP443 = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_QUIC,"reject")
                if (protocol.equals(EConfigType.VLESS.name, true) && outbound.settings?.vnext?.first()?.users?.first()?.flow?.isNotEmpty() == true) {
                    outbound.mux?.concurrency = -1
                }
            } else {
                outbound.mux?.enabled = false
                outbound.mux?.concurrency = -1
            }

            if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                var localTunAddr = if (outbound.settings?.address == null) {
                    listOf(WIREGUARD_LOCAL_ADDRESS_V4, WIREGUARD_LOCAL_ADDRESS_V6)
                } else {
                    outbound.settings?.address as List<*>
                }
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) != true) {
                    localTunAddr = listOf(localTunAddr.first())
                }
                outbound.settings?.address = localTunAddr
            }

            if (outbound.streamSettings?.network == DEFAULT_NETWORK
                && outbound.streamSettings?.tcpSettings?.header?.type == HEADER_TYPE_HTTP
            ) {
                val path = outbound.streamSettings?.tcpSettings?.header?.request?.path
                val host = outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host

                val requestString: String by lazy {
                    """{"version":"1.1","method":"GET","headers":{"User-Agent":["Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}"""
                }
                outbound.streamSettings?.tcpSettings?.header?.request = JsonUtil.fromJson(
                    requestString,
                    V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean::class.java
                )
                outbound.streamSettings?.tcpSettings?.header?.request?.path =
                    if (path.isNullOrEmpty()) {
                        listOf("/")
                    } else {
                        path
                    }
                outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host = host
            }


        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun updateOutboundFragment(v2rayConfig: V2rayConfig): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false) == false) {
                return true
            }
            if (v2rayConfig.outbounds[0].streamSettings?.security != AppConfig.TLS
                && v2rayConfig.outbounds[0].streamSettings?.security != AppConfig.REALITY
            ) {
                return true
            }

            val fragmentOutbound =
                V2rayConfig.OutboundBean(
                    protocol = PROTOCOL_FREEDOM,
                    tag = TAG_FRAGMENT,
                    mux = null
                )

            var packets =
                MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_PACKETS) ?: "tlshello"
            if (v2rayConfig.outbounds[0].streamSettings?.security == AppConfig.REALITY
                && packets == "tlshello"
            ) {
                packets = "1-3"
            } else if (v2rayConfig.outbounds[0].streamSettings?.security == AppConfig.TLS
                && packets != "tlshello"
            ) {
                packets = "tlshello"
            }

            fragmentOutbound.settings = V2rayConfig.OutboundBean.OutSettingsBean(
                fragment = V2rayConfig.OutboundBean.OutSettingsBean.FragmentBean(
                    packets = packets,
                    length = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_LENGTH)
                        ?: "50-100",
                    interval = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_INTERVAL)
                        ?: "10-20"
                ),
                noises = listOf(
                    V2rayConfig.OutboundBean.OutSettingsBean.NoiseBean(
                        type = "rand",
                        packet = "10-20",
                        delay = "10-16",
                    )
                ),
            )
            fragmentOutbound.streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean(
                sockopt = V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                    TcpNoDelay = true,
                    mark = 255
                )
            )
            v2rayConfig.outbounds.add(fragmentOutbound)

            //proxy chain
            v2rayConfig.outbounds[0].streamSettings?.sockopt =
                V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                    dialerProxy = TAG_FRAGMENT
                )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun moreOutbounds(
        v2rayConfig: V2rayConfig,
        subscriptionId: String,
        isPlugin: Boolean
    ): Pair<Boolean, String> {
        val returnPair = Pair(false, "")
        var domainPort: String = ""

        if (isPlugin) {
            return returnPair
        }
        //fragment proxy
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false) == true) {
            return returnPair
        }

        if (subscriptionId.isEmpty()) {
            return returnPair
        }
        try {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return returnPair

            //current proxy
            val outbound = v2rayConfig.outbounds[0]

            //Previous proxy
            val prevNode = SettingsManager.getServerViaRemarks(subItem.prevProfile)
            if (prevNode != null) {
                val prevOutbound = getProxyOutbound(prevNode)
                if (prevOutbound != null) {
                    updateOutboundWithGlobalSettings(prevOutbound)
                    prevOutbound.tag = TAG_PROXY + "2"
                    v2rayConfig.outbounds.add(prevOutbound)
                    outbound.streamSettings?.sockopt =
                        V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                            dialerProxy = prevOutbound.tag
                        )
                    domainPort = prevNode.getServerAddressAndPort()
                }
            }

            //Next proxy
            val nextNode = SettingsManager.getServerViaRemarks(subItem.nextProfile)
            if (nextNode != null) {
                val nextOutbound = getProxyOutbound(nextNode)
                if (nextOutbound != null) {
                    updateOutboundWithGlobalSettings(nextOutbound)
                    nextOutbound.tag = TAG_PROXY
                    v2rayConfig.outbounds.add(0, nextOutbound)
                    outbound.tag = TAG_PROXY + "1"
                    nextOutbound.streamSettings?.sockopt =
                        V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                            dialerProxy = outbound.tag
                        )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return returnPair
        }

        if (domainPort.isNotEmpty()) {
            return Pair(true, domainPort)
        }
        return returnPair
    }

    fun getProxyOutbound(profileItem: ProfileItem): V2rayConfig.OutboundBean? {
        return when (profileItem.configType) {
            EConfigType.VMESS -> VmessFmt.toOutbound(profileItem)
            EConfigType.CUSTOM -> null
            EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toOutbound(profileItem)
            EConfigType.SOCKS -> SocksFmt.toOutbound(profileItem)
            EConfigType.VLESS -> VlessFmt.toOutbound(profileItem)
            EConfigType.TROJAN -> TrojanFmt.toOutbound(profileItem)
            EConfigType.WIREGUARD -> WireguardFmt.toOutbound(profileItem)
            EConfigType.HYSTERIA2 -> Hysteria2Fmt.toOutbound(profileItem)
            EConfigType.HTTP -> HttpFmt.toOutbound(profileItem)
        }

    }

}
