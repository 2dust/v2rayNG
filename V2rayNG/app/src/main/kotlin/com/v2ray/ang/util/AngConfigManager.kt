package com.v2ray.ang.util

import android.graphics.Bitmap
import android.support.v7.preference.PreferenceManager
import android.text.TextUtils
import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
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
    private lateinit var app: AngApplication
    private lateinit var angConfig: AngConfig
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    fun inject(app: AngApplication) {
        this.app = app
        loadConfig()
    }

    /**
     * loading config
     */
    private fun loadConfig() {
        try {
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(app)
            val context = defaultSharedPreferences.getString(ANG_CONFIG, "")
            if (!TextUtils.isEmpty(context)) {
                angConfig = Gson().fromJson(context, AngConfig::class.java)
            } else {
                angConfig = AngConfig(0, vmess = arrayListOf(AngConfig.VmessBean()), subItem = arrayListOf(AngConfig.SubItemBean()))
                angConfig.index = -1
                angConfig.vmess.clear()
                angConfig.subItem.clear()
            }

            for (i in angConfig.vmess.indices) {
                upgradeServerVersion(angConfig.vmess[i])
            }

//            if (configs.subItem == null) {
//                configs.subItem = arrayListOf(AngConfig.SubItemBean())
//            }

        } catch (e: Exception) {
            e.printStackTrace()
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
                                vmessQRCode.path, vmessQRCode.path, vmessQRCode.host, vmessQRCode.path)
                        streamSetting.populateTlsSettings(vmessQRCode.tls, allowInsecure, sni)
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
                        queryParam["host"], queryParam["path"], queryParam["seed"], queryParam["quicSecurity"], queryParam["key"])
                streamSetting.populateTlsSettings(queryParam["security"] ?: "", allowInsecure, sni)
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
                    Regex("(tcp|http|ws|kcp|quic)(\\+tls)?:([0-9a-z]{8}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{12})-([0-9]+)")
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
                    queryParam["path"]?.takeIf { it.trim() != "/" } ?: "",
                    queryParam["seed"], queryParam["security"], queryParam["key"])
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
    fun share2Clipboard(guid: String): Int {
        try {
            val conf = shareConfig(guid)
            if (TextUtils.isEmpty(conf)) {
                return -1
            }

            Utils.setClipboard(app.applicationContext, conf)

        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * share2Clipboard
     */
    fun shareNonCustomConfigsToClipboard(serverList: List<String>): Int {
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
                Utils.setClipboard(app.applicationContext, sb.toString())
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
    fun shareFullContent2Clipboard(guid: String?): Int {
        try {
            if (guid == null) return -1
            val result = V2rayConfigUtil.getV2rayConfig(app, guid)
            if (result.status) {
                Utils.setClipboard(app.applicationContext, result.content)
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
