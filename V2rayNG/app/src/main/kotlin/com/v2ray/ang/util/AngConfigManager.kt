package com.v2ray.ang.util

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.*
import com.v2ray.ang.util.MmkvManager.KEY_SELECTED_SERVER
import com.v2ray.ang.util.fmt.ShadowsocksFmt
import com.v2ray.ang.util.fmt.SocksFmt
import com.v2ray.ang.util.fmt.TrojanFmt
import com.v2ray.ang.util.fmt.VlessFmt
import com.v2ray.ang.util.fmt.VmessFmt
import com.v2ray.ang.util.fmt.WireguardFmt
import java.lang.reflect.Type
import java.util.*

object AngConfigManager {
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val serverRawStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SERVER_RAW,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }

    /**
     * Legacy loading config
     */
//    fun migrateLegacyConfig(c: Context): Boolean? {
//        try {
//            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(c)
//            val context = defaultSharedPreferences.getString(ANG_CONFIG, "")
//            if (context.isNullOrBlank()) {
//                return null
//            }
//            val angConfig = Gson().fromJson(context, AngConfig::class.java)
//            for (i in angConfig.vmess.indices) {
//                upgradeServerVersion(angConfig.vmess[i])
//            }
//
//            copyLegacySettings(defaultSharedPreferences)
//            migrateVmessBean(angConfig, defaultSharedPreferences)
//            migrateSubItemBean(angConfig)
//
//            defaultSharedPreferences.edit().remove(ANG_CONFIG).apply()
//            return true
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//        return false
//    }
//
//    private fun copyLegacySettings(sharedPreferences: SharedPreferences) {
//        listOf(
//            AppConfig.PREF_MODE,
//            AppConfig.PREF_REMOTE_DNS,
//            AppConfig.PREF_DOMESTIC_DNS,
//            AppConfig.PREF_LOCAL_DNS_PORT,
//            AppConfig.PREF_SOCKS_PORT,
//            AppConfig.PREF_HTTP_PORT,
//            AppConfig.PREF_LOGLEVEL,
//            AppConfig.PREF_ROUTING_DOMAIN_STRATEGY,
//            AppConfig.PREF_ROUTING_MODE,
//            AppConfig.PREF_V2RAY_ROUTING_AGENT,
//            AppConfig.PREF_V2RAY_ROUTING_BLOCKED,
//            AppConfig.PREF_V2RAY_ROUTING_DIRECT,
//        ).forEach { key ->
//            settingsStorage?.encode(key, sharedPreferences.getString(key, null))
//        }
//        listOf(
//            AppConfig.PREF_SPEED_ENABLED,
//            AppConfig.PREF_PROXY_SHARING,
//            AppConfig.PREF_LOCAL_DNS_ENABLED,
//            AppConfig.PREF_ALLOW_INSECURE,
//            AppConfig.PREF_PREFER_IPV6,
//            AppConfig.PREF_PER_APP_PROXY,
//            AppConfig.PREF_BYPASS_APPS,
//        ).forEach { key ->
//            settingsStorage?.encode(key, sharedPreferences.getBoolean(key, false))
//        }
//        settingsStorage?.encode(
//            AppConfig.PREF_SNIFFING_ENABLED,
//            sharedPreferences.getBoolean(AppConfig.PREF_SNIFFING_ENABLED, true)
//        )
//        settingsStorage?.encode(
//            AppConfig.PREF_PER_APP_PROXY_SET,
//            sharedPreferences.getStringSet(AppConfig.PREF_PER_APP_PROXY_SET, setOf())
//        )
//    }
//
//    private fun migrateVmessBean(angConfig: AngConfig, sharedPreferences: SharedPreferences) {
//        angConfig.vmess.forEachIndexed { index, vmessBean ->
//            val type = EConfigType.fromInt(vmessBean.configType) ?: return@forEachIndexed
//            val config = ServerConfig.create(type)
//            config.remarks = vmessBean.remarks
//            config.subscriptionId = vmessBean.subid
//            if (type == EConfigType.CUSTOM) {
//                val jsonConfig = sharedPreferences.getString(ANG_CONFIG + vmessBean.guid, "")
//                val v2rayConfig = try {
//                    Gson().fromJson(jsonConfig, V2rayConfig::class.java)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    return@forEachIndexed
//                }
//                config.fullConfig = v2rayConfig
//                serverRawStorage?.encode(vmessBean.guid, jsonConfig)
//            } else {
//                config.outboundBean?.settings?.vnext?.get(0)?.let { vnext ->
//                    vnext.address = vmessBean.address
//                    vnext.port = vmessBean.port
//                    vnext.users[0].id = vmessBean.id
//                    if (config.configType == EConfigType.VMESS) {
//                        vnext.users[0].alterId = vmessBean.alterId
//                        vnext.users[0].security = vmessBean.security
//                    } else if (config.configType == EConfigType.VLESS) {
//                        vnext.users[0].encryption = vmessBean.security
//                        vnext.users[0].flow = vmessBean.flow
//                    }
//                }
//                config.outboundBean?.settings?.servers?.get(0)?.let { server ->
//                    server.address = vmessBean.address
//                    server.port = vmessBean.port
//                    if (config.configType == EConfigType.SHADOWSOCKS) {
//                        server.password = vmessBean.id
//                        server.method = vmessBean.security
//                    } else if (config.configType == EConfigType.SOCKS) {
//                        if (TextUtils.isEmpty(vmessBean.security) && TextUtils.isEmpty(vmessBean.id)) {
//                            server.users = null
//                        } else {
//                            val socksUsersBean =
//                                V2rayConfig.OutboundBean.OutSettingsBean.ServersBean.SocksUsersBean()
//                            socksUsersBean.user = vmessBean.security
//                            socksUsersBean.pass = vmessBean.id
//                            server.users = listOf(socksUsersBean)
//                        }
//                    } else if (config.configType == EConfigType.TROJAN) {
//                        server.password = vmessBean.id
//                    }
//                }
//                config.outboundBean?.streamSettings?.let { streamSetting ->
//                    val sni = streamSetting.populateTransportSettings(
//                        vmessBean.network,
//                        vmessBean.headerType,
//                        vmessBean.requestHost,
//                        vmessBean.path,
//                        vmessBean.path,
//                        vmessBean.requestHost,
//                        vmessBean.path,
//                        vmessBean.headerType,
//                        vmessBean.path,
//                        vmessBean.requestHost,
//                    )
//                    val allowInsecure = if (vmessBean.allowInsecure.isBlank()) {
//                        settingsStorage?.decodeBool(AppConfig.PREF_ALLOW_INSECURE) ?: false
//                    } else {
//                        vmessBean.allowInsecure.toBoolean()
//                    }
//                    var fingerprint = streamSetting.tlsSettings?.fingerprint
//                    streamSetting.populateTlsSettings(
//                        vmessBean.streamSecurity, allowInsecure,
//                        vmessBean.sni.ifBlank { sni }, fingerprint, null, null, null, null
//                    )
//                }
//            }
//            val key = MmkvManager.encodeServerConfig(vmessBean.guid, config)
//            if (index == angConfig.index) {
//                mainStorage?.encode(KEY_SELECTED_SERVER, key)
//            }
//        }
//    }
//
//    private fun migrateSubItemBean(angConfig: AngConfig) {
//        angConfig.subItem.forEach {
//            val subItem = SubscriptionItem()
//            subItem.remarks = it.remarks
//            subItem.url = it.url
//            subItem.enabled = it.enabled
//            subStorage?.encode(it.id, Gson().toJson(subItem))
//        }
//    }

    /**
     * parse config form qrcode or...
     */
    private fun parseConfig(
        str: String?,
        subid: String,
        removedSelectedServer: ServerConfig?
    ): Int {
        try {
            if (str == null || TextUtils.isEmpty(str)) {
                return R.string.toast_none_data
            }

            val config = if (str.startsWith(EConfigType.VMESS.protocolScheme)) {
                VmessFmt.parseVmess(str)
            } else if (str.startsWith(EConfigType.SHADOWSOCKS.protocolScheme)) {
                ShadowsocksFmt.parseShadowsocks(str)
            } else if (str.startsWith(EConfigType.SOCKS.protocolScheme)) {
                SocksFmt.parseSocks(str)
            } else if (str.startsWith(EConfigType.TROJAN.protocolScheme)) {
                TrojanFmt.parseTrojan(str)
            } else if (str.startsWith(EConfigType.VLESS.protocolScheme)) {
                VlessFmt.parseVless(str)
            } else if (str.startsWith(EConfigType.WIREGUARD.protocolScheme)) {
                WireguardFmt.parseWireguard(str)
            } else {
                null
            }

            if (config == null) {
                return R.string.toast_incorrect_protocol
            }
            config.subscriptionId = subid
            val guid = MmkvManager.encodeServerConfig("", config)
            if (removedSelectedServer != null &&
                config.getProxyOutbound()
                    ?.getServerAddress() == removedSelectedServer.getProxyOutbound()
                    ?.getServerAddress() &&
                config.getProxyOutbound()
                    ?.getServerPort() == removedSelectedServer.getProxyOutbound()
                    ?.getServerPort()
            ) {
                mainStorage?.encode(KEY_SELECTED_SERVER, guid)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * share config
     */
    private fun shareConfig(guid: String): String {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ""

            return config.configType.protocolScheme + when (config.configType) {
                EConfigType.VMESS -> VmessFmt.toUri(config)
                EConfigType.CUSTOM -> ""
                EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toUri(config)
                EConfigType.SOCKS -> SocksFmt.toUri(config)
                EConfigType.VLESS -> VlessFmt.toUri(config)
                EConfigType.TROJAN -> TrojanFmt.toUri(config)
                EConfigType.WIREGUARD -> WireguardFmt.toUri(config)
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
            return QRCodeDecoder.createQRCode(conf)

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

//    /**
//     * upgrade
//     */
//    private fun upgradeServerVersion(vmess: AngConfig.VmessBean): Int {
//        try {
//            if (vmess.configVersion == 2) {
//                return 0
//            }
//
//            when (vmess.network) {
//                "ws", "h2" -> {
//                    var path = ""
//                    var host = ""
//                    val lstParameter = vmess.requestHost.split(";")
//                    if (lstParameter.isNotEmpty()) {
//                        path = lstParameter[0].trim()
//                    }
//                    if (lstParameter.size > 1) {
//                        path = lstParameter[0].trim()
//                        host = lstParameter[1].trim()
//                    }
//                    vmess.path = path
//                    vmess.requestHost = host
//                }
//            }
//            vmess.configVersion = 2
//            return 0
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return -1
//        }
//    }

    fun importBatchConfig(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }

        var countSub = parseBatchSubscription(server)
        if (countSub <= 0) {
            countSub = parseBatchSubscription(Utils.decode(server))
        }
        if (countSub > 0) {
            updateConfigViaSubAll()
        }

        return count + countSub
    }

    fun parseBatchSubscription(servers: String?): Int {
        try {
            if (servers == null) {
                return 0
            }

            var count = 0
            servers.lines()
                .forEach { str ->
                    if (str.startsWith(AppConfig.PROTOCOL_HTTP) || str.startsWith(AppConfig.PROTOCOL_HTTPS)) {
                        count += MmkvManager.importUrlAsSubscription(str)
                    }
                }
            return count
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    fun parseBatchConfig(servers: String?, subid: String, append: Boolean): Int {
        try {
            if (servers == null) {
                return 0
            }
            val removedSelectedServer =
                if (!TextUtils.isEmpty(subid) && !append) {
                    MmkvManager.decodeServerConfig(
                        mainStorage?.decodeString(KEY_SELECTED_SERVER) ?: ""
                    )?.let {
                        if (it.subscriptionId == subid) {
                            return@let it
                        }
                        return@let null
                    }
                } else {
                    null
                }
            if (!append) {
                MmkvManager.removeServerViaSubid(subid)
            }

            var count = 0
            servers.lines()
                .reversed()
                .forEach {
                    val resId = parseConfig(it, subid, removedSelectedServer)
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

    fun parseCustomConfigServer(server: String?, subid: String): Int {
        if (server == null) {
            return 0
        }
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                //val gson = GsonBuilder().setPrettyPrinting().create()
                val gson = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .registerTypeAdapter( // custom serialiser is needed here since JSON by default parse number as Double, core will fail to start
                        object : TypeToken<Double>() {}.type,
                        JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? ->
                            JsonPrimitive(
                                src?.toInt()
                            )
                        }
                    )
                    .create()
                val serverList: Array<Any> =
                    Gson().fromJson(server, Array<Any>::class.java)

                if (serverList.isNotEmpty()) {
                    var count = 0
                    for (srv in serverList.reversed()) {
                        val config = ServerConfig.create(EConfigType.CUSTOM)
                        config.fullConfig =
                            Gson().fromJson(Gson().toJson(srv), V2rayConfig::class.java)
                        config.remarks = config.fullConfig?.remarks
                            ?: ("%04d-".format(count + 1) + System.currentTimeMillis()
                                .toString())
                        config.subscriptionId = subid
                        val key = MmkvManager.encodeServerConfig("", config)
                        serverRawStorage?.encode(key, gson.toJson(srv))
                        count += 1
                    }
                    return count
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // For compatibility
            val config = ServerConfig.create(EConfigType.CUSTOM)
            config.subscriptionId = subid
            config.fullConfig = Gson().fromJson(server, V2rayConfig::class.java)
            config.remarks = config.fullConfig?.remarks ?: System.currentTimeMillis().toString()
            val key = MmkvManager.encodeServerConfig("", config)
            serverRawStorage?.encode(key, server)
            return 1
        } else {
            return 0
        }
    }

    fun updateConfigViaSubAll(): Int {
        var count = 0
        try {
            MmkvManager.decodeSubscriptions().forEach {
                count += updateConfigViaSub(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
        return count
    }

    private fun updateConfigViaSub(it: Pair<String, SubscriptionItem>): Int {
        try {
            if (TextUtils.isEmpty(it.first)
                || TextUtils.isEmpty(it.second.remarks)
                || TextUtils.isEmpty(it.second.url)
            ) {
                return 0
            }
            if (!it.second.enabled) {
                return 0
            }
            val url = Utils.idnToASCII(it.second.url)
            if (!Utils.isValidUrl(url)) {
                return 0
            }
            Log.d(AppConfig.ANG_PACKAGE, url)
            var configText = try {
                Utils.getUrlContentWithCustomUserAgent(url)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            if (configText.isEmpty()) {
                configText = try {
                    val httpPort = Utils.parseInt(
                        settingsStorage?.decodeString(AppConfig.PREF_HTTP_PORT),
                        AppConfig.PORT_HTTP.toInt()
                    )
                    Utils.getUrlContentWithCustomUserAgent(url, 30000, httpPort)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
            }
            if (configText.isEmpty()) {
                return 0
            }
            return parseConfigViaSub(configText, it.first, false)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    private fun parseConfigViaSub(server: String?, subid: String, append: Boolean): Int {
        var count = parseBatchConfig(Utils.decode(server), subid, append)
        if (count <= 0) {
            count = parseBatchConfig(server, subid, append)
        }
        if (count <= 0) {
            count = parseCustomConfigServer(server, subid)
        }
        return count
    }
}
