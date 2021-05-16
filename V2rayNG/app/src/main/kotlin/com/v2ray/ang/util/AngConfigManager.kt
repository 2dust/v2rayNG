package com.v2ray.ang.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_CONFIG
import com.v2ray.ang.AppConfig.HTTPS_PROTOCOL
import com.v2ray.ang.AppConfig.HTTP_PROTOCOL
import com.v2ray.ang.R
import com.v2ray.ang.dto.*
import com.v2ray.ang.dto.V2rayConfig.Companion.DEFAULT_SECURITY
import com.v2ray.ang.dto.V2rayConfig.Companion.TLS
import com.v2ray.ang.util.MmkvManager.KEY_SELECTED_SERVER
import java.net.URI
import java.net.URLDecoder
import java.util.*

object AngConfigManager {
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }

    /**
     * Legacy loading config
     */
    fun migrateLegacyConfig(c: Context): Boolean? {
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(c)
            val context = defaultSharedPreferences.getString(ANG_CONFIG, "")
            if (context.isNullOrBlank()) {
                return null
            }
            val angConfig = Gson().fromJson(context, AngConfig::class.java)
            for (i in angConfig.vmess.indices) {
                upgradeServerVersion(angConfig.vmess[i])
            }

            copyLegacySettings(defaultSharedPreferences)
            migrateVmessBean(angConfig, defaultSharedPreferences)
            migrateSubItemBean(angConfig)

            defaultSharedPreferences.edit().remove(ANG_CONFIG).apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun copyLegacySettings(sharedPreferences: SharedPreferences) {
        listOf(AppConfig.PREF_MODE,
                AppConfig.PREF_REMOTE_DNS,
                AppConfig.PREF_DOMESTIC_DNS,
//                AppConfig.PREF_LOCAL_DNS_PORT,
//                AppConfig.PREF_SOCKS_PORT,
//                AppConfig.PREF_HTTP_PORT,
//                AppConfig.PREF_LOGLEVEL,
                AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
                AppConfig.PREF_ROUTING_MODE,
                AppConfig.PREF_V2RAY_ROUTING_AGENT,
                AppConfig.PREF_V2RAY_ROUTING_BLOCKED,
                AppConfig.PREF_V2RAY_ROUTING_DIRECT,).forEach { key ->
            settingsStorage?.encode(key, sharedPreferences.getString(key, null))
        }
        listOf(AppConfig.PREF_SPEED_ENABLED,
                AppConfig.PREF_PROXY_SHARING,
                AppConfig.PREF_LOCAL_DNS_ENABLED,
//                AppConfig.PREF_ALLOW_INSECURE,
//                AppConfig.PREF_PREFER_IPV6,
                AppConfig.PREF_PER_APP_PROXY,
                AppConfig.PREF_BYPASS_APPS,).forEach { key ->
            settingsStorage?.encode(key, sharedPreferences.getBoolean(key, false))
        }
        settingsStorage?.encode(AppConfig.PREF_SNIFFING_ENABLED, sharedPreferences.getBoolean(AppConfig.PREF_SNIFFING_ENABLED, true))
        settingsStorage?.encode(AppConfig.PREF_PER_APP_PROXY_SET, sharedPreferences.getStringSet(AppConfig.PREF_PER_APP_PROXY_SET, setOf()))
    }

    private fun migrateVmessBean(angConfig: AngConfig, sharedPreferences: SharedPreferences) {
        angConfig.vmess.forEachIndexed { index, vmessBean ->
            val type = EConfigType.fromInt(vmessBean.configType) ?: return@forEachIndexed
            val config = ServerConfig.create(type)
            config.remarks = vmessBean.remarks
            config.subscriptionId = vmessBean.subid
            if (type == EConfigType.CUSTOM) {
                val jsonConfig = sharedPreferences.getString(ANG_CONFIG + vmessBean.guid, "")
                val v2rayConfig = try {
                    Gson().fromJson(jsonConfig, V2rayConfig::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@forEachIndexed
                }
                config.fullConfig = v2rayConfig
                serverRawStorage?.encode(vmessBean.guid, jsonConfig)
            } else {
                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                    vnext.address = vmessBean.address
                    vnext.port = vmessBean.port
                    vnext.users[0].id = vmessBean.id
                    if (config.configType == EConfigType.VMESS) {
                        vnext.users[0].alterId = vmessBean.alterId
                        vnext.users[0].security = vmessBean.security
                    } else if (config.configType == EConfigType.VLESS) {
                        vnext.users[0].encryption = vmessBean.security
//                        vnext.users[0].flow = vmessBean.flow
                    }
                }
                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = vmessBean.address
                    server.port = vmessBean.port
                    if (config.configType == EConfigType.SHADOWSOCKS) {
                        server.password = vmessBean.id
                        server.method = vmessBean.security
                    } else if (config.configType == EConfigType.SOCKS) {
                        if (TextUtils.isEmpty(vmessBean.security) && TextUtils.isEmpty(vmessBean.id)) {
                            server.users = null
                        } else {
                            val socksUsersBean = V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                            socksUsersBean.user = vmessBean.security
                            socksUsersBean.pass = vmessBean.id
                            server.users = listOf(socksUsersBean)
                        }
                    } else if (config.configType == EConfigType.TROJAN) {
                        server.password = vmessBean.id
                    }
                }
                config.outboundBean?.streamSettings?.let { streamSetting ->
                    val sni = streamSetting.populateTransportSettings(vmessBean.network, vmessBean.headerType,
                            vmessBean.requestHost, vmessBean.path, vmessBean.path, vmessBean.requestHost, vmessBean.path,
                            vmessBean.headerType, vmessBean.path)
//                    val allowInsecure = if (vmessBean.allowInsecure.isBlank()) {
//                        settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
//                    } else {
//                        vmessBean.allowInsecure.toBoolean()
//                    }
                    streamSetting.populateTlsSettings(vmessBean.streamSecurity, false,
                            sni)//if (vmessBean.sni.isNotBlank()) vmessBean.sni else sni)
                }
            }
            val key = MmkvManager.encodeServerConfig(vmessBean.guid, config)
            if (index == angConfig.index) {
                mainStorage?.encode(KEY_SELECTED_SERVER, key)
            }
        }
    }

    private fun migrateSubItemBean(angConfig: AngConfig) {
        angConfig.subItem.forEach {
            val subItem = SubscriptionItem()
            subItem.remarks = it.remarks
            subItem.url = it.url
            //subItem.enabled = it.enabled
            subStorage?.encode(it.id, Gson().toJson(subItem))
        }
    }

    /**
     * import config form qrcode or...
     */
    private fun importConfig(str: String?, subid: String, removedSelectedServer: ServerConfig?): Int {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return R.string.toast_none_data
            }

            //maybe sub
            if (str.startsWith(HTTP_PROTOCOL) || str.startsWith(HTTPS_PROTOCOL)) {
                MmkvManager.importUrlAsSubscription(str)
                return 0
            }

            var config: ServerConfig? = null
            val allowInsecure = false//settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
            if (str.startsWith(EConfigType.VMESS.protocolScheme)) {
                config = ServerConfig.create(EConfigType.VMESS)
                val streamSetting = config.outboundBean?.streamSettings ?: return -1

                if (!tryParseNewVmess(str, config, allowInsecure)) {
                    if (str.indexOf("?") > 0) {
                        if (!tryResolveVmess4Kitsunebi(str, config)) {
                            return R.string.toast_incorrect_protocol
                        }
                    } else {
                        var result = str.replace(EConfigType.VMESS.protocolScheme, "")
                        result = Utils.decode(result)
                        if (TextUtils.isEmpty(result)) {
                            return R.string.toast_decoding_failed
                        }
                        val vmessQRCode = Gson().fromJson(result, VmessQRCode::class.java)
                        if (TextUtils.isEmpty(vmessQRCode.add)
                                || TextUtils.isEmpty(vmessQRCode.port)
                                || TextUtils.isEmpty(vmessQRCode.id)
                                || TextUtils.isEmpty(vmessQRCode.aid)
                                || TextUtils.isEmpty(vmessQRCode.net)
                        ) {
                            return R.string.toast_incorrect_protocol
                        }

                        config.remarks = vmessQRCode.ps
                        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                            vnext.address = vmessQRCode.add
                            vnext.port = Utils.parseInt(vmessQRCode.port)
                            vnext.users[0].id = vmessQRCode.id
                            vnext.users[0].encryption = DEFAULT_SECURITY
                            vnext.users[0].alterId = Utils.parseInt(vmessQRCode.aid)
                        }
                        val sni = streamSetting.populateTransportSettings(vmessQRCode.net, vmessQRCode.type, vmessQRCode.host,
                                vmessQRCode.path, vmessQRCode.path, vmessQRCode.host, vmessQRCode.path, vmessQRCode.type, vmessQRCode.path)
                        streamSetting.populateTlsSettings(vmessQRCode.tls, allowInsecure,
                                if (vmessQRCode.sni.isNotBlank()) vmessQRCode.sni else sni)
                    }
                }
            } else if (str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme)) {
                var result = str.replace(EConfigType.SHADOWSOCKS.protocolScheme, "")
                val indexSplit = result.indexOf("#")
                config = ServerConfig.create(EConfigType.SHADOWSOCKS)
                if (indexSplit > 0) {
                    try {
                        config.remarks = Utils.urlDecode(result.substring(indexSplit + 1, result.length))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    result = result.substring(0, indexSplit)
                }

                //part decode
                val indexS = result.indexOf("@")
                result = if (indexS > 0) {
                    Utils.decode(result.substring(0, indexS)) + result.substring(indexS, result.length)
                } else {
                    Utils.decode(result)
                }

                val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)/?$".toRegex()
                val match = legacyPattern.matchEntire(result) ?: return R.string.toast_incorrect_protocol

                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = match.groupValues[3].removeSurrounding("[", "]")
                    server.port = match.groupValues[4].toInt()
                    server.password = match.groupValues[2]
                    server.method = match.groupValues[1].toLowerCase()
                }
            } else if (str.startsWith(EConfigType.SOCKS.protocolScheme)) {
                var result = str.replace(EConfigType.SOCKS.protocolScheme, "")
                val indexSplit = result.indexOf("#")
                config = ServerConfig.create(EConfigType.SOCKS)
                if (indexSplit > 0) {
                    try {
                        config.remarks = Utils.urlDecode(result.substring(indexSplit + 1, result.length))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    result = result.substring(0, indexSplit)
                }

                //part decode
                val indexS = result.indexOf("@")
                if (indexS > 0) {
                    //result = Utils.decode(result.substring(0, indexS)) + result.substring(indexS, result.length)
                } else {
                    result = Utils.decode(result)
                }

                val legacyPattern = "^(.*):(.*)@(.+?):(\\d+?)$".toRegex()
                val match = legacyPattern.matchEntire(result) ?: return R.string.toast_incorrect_protocol

                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = match.groupValues[3].removeSurrounding("[", "]")
                    server.port = match.groupValues[4].toInt()
                    val socksUsersBean = V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
                    socksUsersBean.user = match.groupValues[1].toLowerCase()
                    socksUsersBean.pass = match.groupValues[2]
                    server.users = listOf(socksUsersBean)
                }
            } else if (str.startsWith(EConfigType.TROJAN.protocolScheme)) {
                val uri = URI(str)
                config = ServerConfig.create(EConfigType.TROJAN)
                config.remarks = uri.fragment ?: ""
                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
                    server.address = uri.host
                    server.port = uri.port
                    server.password = uri.userInfo
                }
                var sni = ""
                uri.rawQuery?.let { rawQuery ->
                    val queryParam = rawQuery.split("&")
                            .map { it.split("=").let { (k, v) -> k to URLDecoder.decode(v, "utf-8")!! } }
                            .toMap()
                    sni = queryParam["sni"] ?: ""
                }
                config.outboundBean?.streamSettings?.populateTlsSettings(TLS, allowInsecure, sni)
            } else if (str.startsWith(EConfigType.VLESS.protocolScheme)) {
                val uri = URI(str)
                val queryParam = uri.rawQuery.split("&")
                        .map { it.split("=").let { (k, v) -> k to URLDecoder.decode(v, "utf-8")!! } }
                        .toMap()
                config = ServerConfig.create(EConfigType.VLESS)
                val streamSetting = config.outboundBean?.streamSettings ?: return -1
                config.remarks = uri.fragment ?: ""
                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
                    vnext.address = uri.host
                    vnext.port = uri.port
                    vnext.users[0].id = uri.userInfo
                    vnext.users[0].encryption = queryParam["encryption"] ?: "none"
                    vnext.users[0].flow =queryParam["flow"] ?: ""
                }

                val sni = streamSetting.populateTransportSettings(queryParam["type"] ?: "tcp", queryParam["headerType"],
                        queryParam["host"], queryParam["path"], queryParam["seed"], queryParam["quicSecurity"], queryParam["key"],
                        queryParam["mode"], queryParam["serviceName"])
                streamSetting.populateTlsSettings(queryParam["security"] ?: "", allowInsecure, queryParam["sni"] ?: sni)
            }
            if (config == null){
                return R.string.toast_incorrect_protocol
            }
            config.subscriptionId = subid
            val guid = MmkvManager.encodeServerConfig("", config)
            if (removedSelectedServer != null &&
                    config.getProxyOutbound()?.getServerAddress() == removedSelectedServer.getProxyOutbound()?.getServerAddress() &&
                    config.getProxyOutbound()?.getServerPort() == removedSelectedServer.getProxyOutbound()?.getServerPort()) {
                mainStorage?.encode(KEY_SELECTED_SERVER, guid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    private fun tryParseNewVmess(uriString: String, config: ServerConfig, allowInsecure: Boolean): Boolean {
        return runCatching {
            val uri = URI(uriString)
            check(uri.scheme == "vmess")
            val (_, protocol, tlsStr, uuid, alterId) =
                    Regex("(tcp|http|ws|kcp|quic|grpc)(\\+tls)?:([0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12})-([0-9]+)")
                            .matchEntire(uri.userInfo)?.groupValues
                            ?: error("parse user info fail.")
            val tls = tlsStr.isNotBlank()
            val queryParam = uri.rawQuery.split("&")
                    .map { it.split("=").let { (k, v) -> k to URLDecoder.decode(v, "utf-8")!! } }
                    .toMap()

            val streamSetting = config.outboundBean?.streamSettings ?: return false
            config.remarks = uri.fragment
            config.outboundBean.settings?.vnext?.get(0)?.let { vnext ->
                vnext.address = uri.host
                vnext.port = uri.port
                vnext.users[0].id = uuid
                vnext.users[0].encryption = DEFAULT_SECURITY
                vnext.users[0].alterId = alterId.toInt()
            }

            val sni = streamSetting.populateTransportSettings(protocol, queryParam["type"],
                    queryParam["host"]?.split("|")?.get(0) ?: "",
                    queryParam["path"]?.takeIf { it.trim() != "/" } ?: "", queryParam["seed"], queryParam["security"],
                    queryParam["key"], queryParam["mode"], queryParam["serviceName"])
            streamSetting.populateTlsSettings(if (tls) TLS else "", allowInsecure, sni)
            true
        }.getOrElse { false }
    }

    private fun tryResolveVmess4Kitsunebi(server: String, config: ServerConfig): Boolean {

        var result = server.replace(EConfigType.VMESS.protocolScheme, "")
        val indexSplit = result.indexOf("?")
        if (indexSplit > 0) {
            result = result.substring(0, indexSplit)
        }
        result = Utils.decode(result)

        val arr1 = result.split('@')
        if (arr1.count() != 2) {
            return false
        }
        val arr21 = arr1[0].split(':')
        val arr22 = arr1[1].split(':')
        if (arr21.count() != 2) {
            return false
        }

        config.remarks = "Alien"
        config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
            vnext.address = arr22[0]
            vnext.port = Utils.parseInt(arr22[1])
            vnext.users[0].id = arr21[1]
            vnext.users[0].encryption = arr21[0]
            vnext.users[0].alterId = 0
        }
        return true
    }

    /**
     * share config
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""
            val outbound = config.getProxyOutbound() ?: return ""
            val streamSetting = outbound.streamSettings ?: return ""
            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> {
                    val vmessQRCode = VmessQRCode()
                    vmessQRCode.v = "2"
                    vmessQRCode.ps = config.remarks
                    vmessQRCode.add = outbound.getServerAddress().orEmpty()
                    vmessQRCode.port = outbound.getServerPort().toString()
                    vmessQRCode.id = outbound.getPassword().orEmpty()
                    vmessQRCode.aid = outbound.settings?.vnext?.get(0)?.users?.get(0)?.alterId.toString()
                    vmessQRCode.net = streamSetting.network
                    vmessQRCode.tls = streamSetting.security
                    vmessQRCode.sni = streamSetting.tlsSettings?.serverName.orEmpty()
                    outbound.getTransportSettingDetails()?.let { transportDetails ->
                        vmessQRCode.type = transportDetails[0]
                        vmessQRCode.host = transportDetails[1]
                        vmessQRCode.path = transportDetails[2]
                    }
                    val json = Gson().toJson(vmessQRCode)
                    Utils.encode(json)
                }
                EConfigType.CUSTOM -> ""
                EConfigType.SHADOWSOCKS -> {
                    val remark = "#" + Utils.urlEncode(config.remarks)
                    val url = String.format("%s:%s@%s:%s",
                            outbound.getSecurityEncryption(),
                            outbound.getPassword(),
                            outbound.getServerAddress(),
                            outbound.getServerPort())
                    Utils.encode(url) + remark
                }
                EConfigType.SOCKS -> {
                    val remark = "#" + Utils.urlEncode(config.remarks)
                    val url = String.format("%s:%s@%s:%s",
                            outbound.settings?.servers?.get(0)?.users?.get(0)?.user,
                            outbound.getPassword(),
                            outbound.getServerAddress(),
                            outbound.getServerPort())
                    Utils.encode(url) + remark
                }
                EConfigType.VLESS -> {
                    val remark = "#" + Utils.urlEncode(config.remarks)

                    val dicQuery = HashMap<String, String>()
                    outbound.settings?.vnext?.get(0)?.users?.get(0)?.flow?.let {
                        if (!TextUtils.isEmpty(it)) {
                            dicQuery["flow"] = it
                        }
                    }
                    dicQuery["encryption"] = if (outbound.getSecurityEncryption().isNullOrEmpty()) "none"
                    else outbound.getSecurityEncryption().orEmpty()
                    dicQuery["security"] = if (streamSetting.security.isEmpty()) "none"
                    else streamSetting.security
                    (streamSetting.tlsSettings?: streamSetting.xtlsSettings)?.let { tlsSetting ->
                        if (!TextUtils.isEmpty(tlsSetting.serverName)) {
                            dicQuery["sni"] = tlsSetting.serverName
                        }
                    }
                    dicQuery["type"] = if (streamSetting.network.isEmpty()) V2rayConfig.DEFAULT_NETWORK
                    else streamSetting.network

                    outbound.getTransportSettingDetails()?.let { transportDetails ->
                        when (streamSetting.network) {
                            "tcp" -> {
                                dicQuery["headerType"] = if (transportDetails[0].isEmpty()) "none"
                                else transportDetails[0]
                                if (!TextUtils.isEmpty(transportDetails[1])) {
                                    dicQuery["host"] = Utils.urlEncode(transportDetails[1])
                                }
                            }
                            "kcp" -> {
                                dicQuery["headerType"] = if (transportDetails[0].isEmpty()) "none"
                                else transportDetails[0]
                                if (!TextUtils.isEmpty(transportDetails[2])) {
                                    dicQuery["seed"] = Utils.urlEncode(transportDetails[2])
                                }
                            }
                            "ws" -> {
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
                                dicQuery["headerType"] = if (transportDetails[0].isEmpty()) "none"
                                else transportDetails[0]
                                dicQuery["quicSecurity"] = Utils.urlEncode(transportDetails[1])
                                dicQuery["key"] = Utils.urlEncode(transportDetails[2])
                            }
                            "grpc" -> {
                                dicQuery["mode"] = transportDetails[0]
                                dicQuery["serviceName"] = transportDetails[2]
                            }
                        }
                    }
                    val query = "?" + dicQuery.toList().joinToString(
                            separator = "&",
                            transform = { it.first + "=" + it.second })

                    val url = String.format("%s@%s:%s",
                            outbound.getPassword(),
                            outbound.getServerAddress(),
                            outbound.getServerPort())
                    url + query + remark
                }
                EConfigType.TROJAN -> {
                    val remark = "#" + Utils.urlEncode(config.remarks)
                    var query = ""
                    (streamSetting.tlsSettings?: streamSetting.xtlsSettings)?.let { tlsSetting ->
                        if (!TextUtils.isEmpty(tlsSetting.serverName)) {
                            query = "?sni=${tlsSetting.serverName}"
                        }
                    }
                    val url = String.format("%s@%s:%s",
                            outbound.getPassword(),
                            outbound.getServerAddress(),
                            outbound.getServerPort())
                    url + query + remark
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * share2Clipboard
     */
    fun share2Clipboard(context: Context, guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(context, conf)

        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * share2Clipboard
     */
    fun shareNonCustomConfigsToClipboard(context: Context, serverList: List<String>): Int {
        try {
            val sb = StringBuilder()
            for (guid in serverList) {
                val url = shareConfig(guid)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendLine()
            }
            if (sb.count() > 0) {
                Utils.setClipboard(context, sb.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * share2QRCode
     */
    fun share2QRCode(guid: String): Bitmap? {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            return Utils.createQRCode(conf)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * shareFullContent2Clipboard
     */
    fun shareFullContent2Clipboard(context: Context, guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = V2rayConfigUtil.getV2rayConfig(context, guid)
            if (result.status) {
                Utils.setClipboard(context, result.content)
            } else {
                return -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * upgrade
     */
    private fun upgradeServerVersion(vmess: AngConfig.VmessBean): Int {
        try {
            if (vmess.configVersion == 2) {
                return 0
            }

            when (vmess.network) {
                "ws", "h2" -> {
                    var path = ""
                    var host = ""
                    val lstParameter = vmess.requestHost.split(";")
                    if (lstParameter.isNotEmpty()) {
                        path = lstParameter[0].trim()
                    }
                    if (lstParameter.size > 1) {
                        path = lstParameter[0].trim()
                        host = lstParameter[1].trim()
                    }
                    vmess.path = path
                    vmess.requestHost = host
                }
            }
            vmess.configVersion = 2
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    fun importBatchConfig(servers: String?, subid: String): Int {
        try {
            if (servers == null) {
                return 0
            }
            val removedSelectedServer =
                    if (!TextUtils.isEmpty(subid)) {
                        MmkvManager.decodeServerConfig(mainStorage?.decodeString(KEY_SELECTED_SERVER) ?: "")?.let {
                            if (it.subscriptionId == subid) {
                                return@let it
                            }
                            return@let null
                        }
                    } else {
                        null
                    }
            MmkvManager.removeServerViaSubid(subid)

//            var servers = server
//            if (server.indexOf("vmess") >= 0 && server.indexOf("vmess") == server.lastIndexOf("vmess")) {
//                servers = server.replace("\n", "")
//            }

            var count = 0
            servers.lines()
                    .forEach {
                        val resId = importConfig(it, subid, removedSelectedServer)
                        if (resId == 0) {
                            count++
                        }
                    }
            return count
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }
}
