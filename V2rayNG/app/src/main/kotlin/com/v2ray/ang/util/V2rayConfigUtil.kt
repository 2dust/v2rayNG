package com.v2ray.ang.util

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.google.gson.*
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ERoutingMode
import com.v2ray.ang.dto.V2rayConfig.Companion.DEFAULT_NETWORK
import com.v2ray.ang.dto.V2rayConfig.Companion.HTTP

object V2rayConfigUtil {
    private val serverRawStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    data class Result(var status: Boolean, var content: String)

    /**
     * 生成v2ray的客户端配置文件
     */
    fun getV2rayConfig(context: Context, guid: String): Result {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return Result(false, "")
            if (config.configType == EConfigType.CUSTOM) {
                val raw = serverRawStorage?.decodeString(guid)
                val customConfig = if (raw.isNullOrBlank()) {
                    config.fullConfig?.toPrettyPrinting() ?: return Result(false, "")
                } else {
                    raw
                }
                Log.d(ANG_PACKAGE, customConfig)
                return Result(true, customConfig)
            }
            val outbound = config.getProxyOutbound() ?: return Result(false, "")
            val result = getV2rayNonCustomConfig(context, outbound)
            Log.d(ANG_PACKAGE, result.content)
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            return Result(false, "")
        }
    }

    /**
     * 生成v2ray的客户端配置文件
     */
    private fun getV2rayNonCustomConfig(context: Context, outbound: V2rayConfig.OutboundBean): Result {
        val result = Result(false, "")
        //取得默认配置
        val assets = Utils.readTextFromAssets(context, "v2ray_config.json")
        if (TextUtils.isEmpty(assets)) {
            return result
        }

        //转成Json
        val v2rayConfig = Gson().fromJson(assets, V2rayConfig::class.java) ?: return result

        v2rayConfig.log.loglevel = settingsStorage?.decodeString(AppConfig.PREF_LOGLEVEL)
                ?: "warning"

        inbounds(v2rayConfig)

        httpRequestObject(outbound)

        v2rayConfig.outbounds[0] = outbound

        routing(v2rayConfig)

        fakedns(v2rayConfig)

        dns(v2rayConfig)

        if (settingsStorage?.decodeBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            customLocalDns(v2rayConfig)
        }
        if (settingsStorage?.decodeBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }
        result.status = true
        result.content = v2rayConfig.toPrettyPrinting()
        return result
    }

    /**
     *
     */
    private fun inbounds(v2rayConfig: V2rayConfig): Boolean {
        try {
            val socksPort = Utils.parseInt(settingsStorage?.decodeString(AppConfig.PREF_SOCKS_PORT), AppConfig.PORT_SOCKS.toInt())
            val httpPort = Utils.parseInt(settingsStorage?.decodeString(AppConfig.PREF_HTTP_PORT), AppConfig.PORT_HTTP.toInt())

            v2rayConfig.inbounds.forEach { curInbound ->
                if (settingsStorage?.decodeBool(AppConfig.PREF_PROXY_SHARING) != true) {
                    //bind all inbounds to localhost if the user requests
                    curInbound.listen = "127.0.0.1"
                }
            }
            v2rayConfig.inbounds[0].port = socksPort
            val fakedns = settingsStorage?.decodeBool(AppConfig.PREF_FAKE_DNS_ENABLED)
                    ?: false
            val sniffAllTlsAndHttp = settingsStorage?.decodeBool(AppConfig.PREF_SNIFFING_ENABLED, true)
                    ?: true
            v2rayConfig.inbounds[0].sniffing?.enabled = fakedns || sniffAllTlsAndHttp
            if (!sniffAllTlsAndHttp) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.clear()
            }
            if (fakedns) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.add("fakedns")
            }

            v2rayConfig.inbounds[1].port = httpPort

//            if (httpPort > 0) {
//                val httpCopy = v2rayConfig.inbounds[0].copy()
//                httpCopy.port = httpPort
//                httpCopy.protocol = "http"
//                v2rayConfig.inbounds.add(httpCopy)
//            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun fakedns(v2rayConfig: V2rayConfig) {
        if (settingsStorage?.decodeBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
            v2rayConfig.outbounds.filter { it.protocol == "freedom" }.forEach {
                it.settings?.domainStrategy = "UseIP"
            }
        }
    }

    /**
     * routing
     */
    private fun routing(v2rayConfig: V2rayConfig): Boolean {
        try {
            routingUserRule(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_AGENT)
                    ?: "", AppConfig.TAG_AGENT, v2rayConfig)
            routingUserRule(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_DIRECT)
                    ?: "", AppConfig.TAG_DIRECT, v2rayConfig)
            routingUserRule(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED)
                    ?: "", AppConfig.TAG_BLOCKED, v2rayConfig)

            v2rayConfig.routing.domainStrategy = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
                    ?: "IPIfNonMatch"
//            v2rayConfig.routing.domainMatcher = "mph"
            val routingMode = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_MODE) ?: ERoutingMode.GLOBAL_PROXY.value

            // Hardcode googleapis.cn
            val googleapisRoute = V2rayConfig.RoutingBean.RulesBean(
                    type = "field",
                    outboundTag = AppConfig.TAG_AGENT,
                    domain = arrayListOf("domain:googleapis.cn")
            )

            when (routingMode) {
                ERoutingMode.BYPASS_LAN.value -> {
                    routingGeo("ip", "private", AppConfig.TAG_DIRECT, v2rayConfig)
                }
                ERoutingMode.BYPASS_MAINLAND.value -> {
                    routingGeo("", "cn", AppConfig.TAG_DIRECT, v2rayConfig)
                    v2rayConfig.routing.rules.add(0, googleapisRoute)
                }
                ERoutingMode.BYPASS_LAN_MAINLAND.value -> {
                    routingGeo("ip", "private", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "cn", AppConfig.TAG_DIRECT, v2rayConfig)
                    v2rayConfig.routing.rules.add(0, googleapisRoute)
                }
                ERoutingMode.BYPASS_IRAN.value -> {
                    routingGeo("", "ir", AppConfig.TAG_DIRECT, v2rayConfig)
                }
                ERoutingMode.BYPASS_LAN_IRAN.value -> {
                    routingGeo("ip", "private", AppConfig.TAG_DIRECT, v2rayConfig)
                    routingGeo("", "ir", AppConfig.TAG_DIRECT, v2rayConfig)
                }
                ERoutingMode.GLOBAL_DIRECT.value -> {
                    val globalDirect = V2rayConfig.RoutingBean.RulesBean(
                        type = "field",
                        outboundTag = AppConfig.TAG_DIRECT,
                        port = "0-65535"
                    )
                    v2rayConfig.routing.rules.add(globalDirect)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun routingGeo(ipOrDomain: String, code: String, tag: String, v2rayConfig: V2rayConfig) {
        try {
            if (!TextUtils.isEmpty(code)) {
                //IP
                if (ipOrDomain == "ip" || ipOrDomain == "") {
                    val rulesIP = V2rayConfig.RoutingBean.RulesBean()
                    rulesIP.type = "field"
                    rulesIP.outboundTag = tag
                    rulesIP.ip = ArrayList()
                    rulesIP.ip?.add("geoip:$code")
                    v2rayConfig.routing.rules.add(rulesIP)
                }

                if (ipOrDomain == "domain" || ipOrDomain == "") {
                    //Domain
                    val rulesDomain = V2rayConfig.RoutingBean.RulesBean()
                    rulesDomain.type = "field"
                    rulesDomain.outboundTag = tag
                    rulesDomain.domain = ArrayList()
                    rulesDomain.domain?.add("geosite:$code")
                    v2rayConfig.routing.rules.add(rulesDomain)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun routingUserRule(userRule: String, tag: String, v2rayConfig: V2rayConfig) {
        try {
            if (!TextUtils.isEmpty(userRule)) {
                //Domain
                val rulesDomain = V2rayConfig.RoutingBean.RulesBean()
                rulesDomain.type = "field"
                rulesDomain.outboundTag = tag
                rulesDomain.domain = ArrayList()

                //IP
                val rulesIP = V2rayConfig.RoutingBean.RulesBean()
                rulesIP.type = "field"
                rulesIP.outboundTag = tag
                rulesIP.ip = ArrayList()

                userRule.split(",").map { it.trim() }.forEach {
                    if (Utils.isIpAddress(it) || it.startsWith("geoip:")) {
                        rulesIP.ip?.add(it)
                    } else if (it.isNotEmpty())
//                                if (Utils.isValidUrl(it)
//                                    || it.startsWith("geosite:")
//                                    || it.startsWith("regexp:")
//                                    || it.startsWith("domain:")
//                                    || it.startsWith("full:"))
                    {
                        rulesDomain.domain?.add(it)
                    }
                }
                if (rulesDomain.domain?.size!! > 0) {
                    v2rayConfig.routing.rules.add(rulesDomain)
                }
                if (rulesIP.ip?.size!! > 0) {
                    v2rayConfig.routing.rules.add(rulesIP)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun userRule2Domian(userRule: String): ArrayList<String> {
        val domain = ArrayList<String>()
        userRule.split(",").map { it.trim() }.forEach {
            if (it.startsWith("geosite:") || it.startsWith("domain:")) {
                domain.add(it)
            }
        }
        return domain
    }

    /**
     * Custom Dns
     */
    private fun customLocalDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val geoProvider = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_ASSETS_PROVIDER) ?: "0"
            val routingMode = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_MODE) ?: ERoutingMode.GLOBAL_PROXY.value

            val geosite = when {
                (routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) -> arrayListOf("geosite:cn")

                (routingMode == ERoutingMode.BYPASS_IRAN.value || routingMode == ERoutingMode.BYPASS_LAN_IRAN.value) -> arrayListOf("geosite:ir")

                else -> {
                    when {
                        (geoProvider == "0" || geoProvider == "1") -> arrayListOf("geosite:cn")

                        geoProvider == "2" -> arrayListOf("geosite:ir")

                        else -> arrayListOf("geosite:cn")
                    }
                }
            }

            if (settingsStorage?.decodeBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
                val proxyDomain = userRule2Domian(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_AGENT)
                        ?: "")
                val directDomain = userRule2Domian(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_DIRECT)
                        ?: "")
                // fakedns with all domains to make it always top priority
                v2rayConfig.dns.servers?.add(0,
                        V2rayConfig.DnsBean.ServersBean(address = "fakedns", domains = geosite.plus(proxyDomain).plus(directDomain)))
            }

            // DNS inbound对象
            val remoteDns = Utils.getRemoteDnsServers()
            if (v2rayConfig.inbounds.none { e -> e.protocol == "dokodemo-door" && e.tag == "dns-in" }) {
                val dnsInboundSettings = V2rayConfig.InboundBean.InSettingsBean(
                        address = if (Utils.isPureIpAddress(remoteDns.first())) remoteDns.first() else "1.1.1.1",
                        port = 53,
                        network = "tcp,udp")

                val localDnsPort = Utils.parseInt(settingsStorage?.decodeString(AppConfig.PREF_LOCAL_DNS_PORT), AppConfig.PORT_LOCAL_DNS.toInt())
                v2rayConfig.inbounds.add(
                        V2rayConfig.InboundBean(
                                tag = "dns-in",
                                port = localDnsPort,
                                listen = "127.0.0.1",
                                protocol = "dokodemo-door",
                                settings = dnsInboundSettings,
                                sniffing = null))
            }

            // DNS outbound对象
            if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
                v2rayConfig.outbounds.add(
                        V2rayConfig.OutboundBean(
                                protocol = "dns",
                                tag = "dns-out",
                                settings = null,
                                streamSettings = null,
                                mux = null))
            }

            // DNS routing tag
            v2rayConfig.routing.rules.add(0, V2rayConfig.RoutingBean.RulesBean(
                    type = "field",
                    inboundTag = arrayListOf("dns-in"),
                    outboundTag = "dns-out",
                    domain = null)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun dns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val hosts = mutableMapOf<String, String>()
            val servers = ArrayList<Any>()
            val remoteDns = Utils.getRemoteDnsServers()
            val proxyDomain = userRule2Domian(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_AGENT)
                    ?: "")

            remoteDns.forEach {
                servers.add(it)
            }
            if (proxyDomain.size > 0) {
                servers.add(V2rayConfig.DnsBean.ServersBean(remoteDns.first(), 53, proxyDomain, null))
            }

            // domestic DNS
            val directDomain = userRule2Domian(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_DIRECT)
                    ?: "")
            val geosite: ArrayList<String>
            val geoip: ArrayList<String>
            val geoProvider = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_ASSETS_PROVIDER) ?: "0"
            val routingMode = settingsStorage?.decodeString(AppConfig.PREF_ROUTING_MODE) ?: ERoutingMode.GLOBAL_PROXY.value

            when {
                (routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value) -> {
                    geosite = arrayListOf("geosite:cn")
                    geoip = arrayListOf("geoip:cn")
                }
                (routingMode == ERoutingMode.BYPASS_IRAN.value || routingMode == ERoutingMode.BYPASS_LAN_IRAN.value) -> {
                    geosite = arrayListOf("geosite:ir")
                    geoip = arrayListOf("geoip:ir")
                }
                else -> {
                    when {
                        (geoProvider == "0" || geoProvider == "1") -> {
                            geosite = arrayListOf("geosite:cn")
                            geoip = arrayListOf("geoip:cn")
                        }
                        geoProvider == "2" -> {
                            geosite = arrayListOf("geosite:ir")
                            geoip = arrayListOf("geoip:ir")
                        }
                        else -> {
                            geosite = arrayListOf("geosite:cn")
                            geoip = arrayListOf("geoip:cn")
                        }
                    }
                }
            }

            if (directDomain.size > 0 || routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value || routingMode == ERoutingMode.BYPASS_IRAN.value || routingMode == ERoutingMode.BYPASS_LAN_IRAN.value) {
                val domesticDns = Utils.getDomesticDnsServers()
                if (directDomain.size > 0) {
                    servers.add(V2rayConfig.DnsBean.ServersBean(domesticDns.first(), 53, directDomain, geoip))
                }
                if (routingMode == ERoutingMode.BYPASS_MAINLAND.value || routingMode == ERoutingMode.BYPASS_LAN_MAINLAND.value || routingMode == ERoutingMode.BYPASS_IRAN.value || routingMode == ERoutingMode.BYPASS_LAN_IRAN.value) {
                    servers.add(V2rayConfig.DnsBean.ServersBean(domesticDns.first(), 53, geosite, geoip))
                }
                if (Utils.isPureIpAddress(domesticDns.first())) {
                    v2rayConfig.routing.rules.add(0, V2rayConfig.RoutingBean.RulesBean(
                            type = "field",
                            outboundTag = AppConfig.TAG_DIRECT,
                            port = "53",
                            ip = arrayListOf(domesticDns.first()),
                            domain = null)
                    )
                }
            }

            val blkDomain = userRule2Domian(settingsStorage?.decodeString(AppConfig.PREF_V2RAY_ROUTING_BLOCKED)
                    ?: "")
            if (blkDomain.size > 0) {
                hosts.putAll(blkDomain.map { it to "127.0.0.1" })
            }

            // if routing mode and provider is not related to iran, set googleapi rule to fix play store problems

            if (geoProvider != "2" && routingMode != ERoutingMode.BYPASS_IRAN.value && routingMode != ERoutingMode.BYPASS_LAN_IRAN.value) {
                hosts["domain:googleapis.cn"] = "googleapis.com"
            }
            // DNS dns对象
            v2rayConfig.dns = V2rayConfig.DnsBean(
                    servers = servers,
                    hosts = hosts)

            // DNS routing
            if (Utils.isPureIpAddress(remoteDns.first())) {
                v2rayConfig.routing.rules.add(0, V2rayConfig.RoutingBean.RulesBean(
                        type = "field",
                        outboundTag = AppConfig.TAG_AGENT,
                        port = "53",
                        ip = arrayListOf(remoteDns.first()),
                        domain = null)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun httpRequestObject(outbound: V2rayConfig.OutboundBean): Boolean {
        try {
            if (outbound.streamSettings?.network == DEFAULT_NETWORK
                    && outbound.streamSettings?.tcpSettings?.header?.type == HTTP) {
                val path = outbound.streamSettings?.tcpSettings?.header?.request?.path
                val host = outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host

                val requestString: String by lazy {
                    """{"version":"1.1","method":"GET","headers":{"User-Agent":["Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36","Mozilla/5.0 (iPhone; CPU iPhone OS 10_0_2 like Mac OS X) AppleWebKit/601.1 (KHTML, like Gecko) CriOS/53.0.2785.109 Mobile/14A456 Safari/601.1.46"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}"""
                }
                outbound.streamSettings?.tcpSettings?.header?.request = Gson().fromJson(
                    requestString,
                    V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean::class.java
                )
                outbound.streamSettings?.tcpSettings?.header?.request?.path =
                    if (path.isNullOrEmpty()) {
                        listOf("/")
                    } else {
                        path
                    }
                outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host = host!!
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

}
