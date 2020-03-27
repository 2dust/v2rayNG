package com.v2ray.ang.util

import SpeedUpVPN.VpnEncrypt
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_CONFIG
import com.v2ray.ang.AppConfig.PREF_CURR_CONFIG
import com.v2ray.ang.AppConfig.PREF_CURR_CONFIG_GUID
import com.v2ray.ang.AppConfig.PREF_CURR_CONFIG_NAME
import com.v2ray.ang.AppConfig.SOCKS_PROTOCOL
import com.v2ray.ang.AppConfig.SSR_PROTOCOL
import com.v2ray.ang.AppConfig.SS_PROTOCOL
import com.v2ray.ang.AppConfig.VMESS_PROTOCOL
import com.v2ray.ang.R
import com.v2ray.ang.dto.AngConfig
import com.v2ray.ang.dto.VmessQRCode
import com.v2ray.ang.extension.defaultDPreference
import org.jetbrains.anko.toast
import java.net.URLDecoder
import java.util.*
import java.net.*
import java.math.BigInteger

object AngConfigManager {
    private lateinit var app: AngApplication
    private lateinit var angConfig: AngConfig
    val configs: AngConfig get() = angConfig

    fun inject(app: AngApplication) {
        this.app = app
        if (app.firstRun) {
        }
        loadConfig()
    }

    /**
     * loading config
     */
    fun loadConfig() {
        try {
            val context = app.defaultDPreference.getPrefString(ANG_CONFIG, "")
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

            if (configs.subItem == null) {
                configs.subItem = arrayListOf(AngConfig.SubItemBean())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * add or edit server
     */
    fun addServer(vmess: AngConfig.VmessBean, index: Int): Int {
        try {
            if (vmess.address=="127.0.0.1" || vmess.address=="localhost") return -1

            if (vmess.subid != VpnEncrypt.builtinSubID) {
                for (k in 0 until configs.vmess.count()) {//Check for duplicates
                    if (configs.vmess[k].address == vmess.address && configs.vmess[k].port == vmess.port) return -1
                }
            }

            vmess.configVersion = 2
            vmess.configType = AppConfig.EConfigType.Vmess

            if (index >= 0) {
                //edit
                angConfig.vmess[index] = vmess
            } else {
                //add
                vmess.guid = Utils.getUuid()
                angConfig.vmess.add(vmess)
                if (angConfig.vmess.count() == 1) {
                    angConfig.index = 0
                }
            }

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * 移除服务器
     */
    fun removeServer(index: Int): Int {
        try {
            if (index < 0 || index > angConfig.vmess.count() - 1) {
                return -1
            }

            //删除
            angConfig.vmess.removeAt(index)

            //移除的是活动的
            if (angConfig.index == index) {
                if (angConfig.vmess.count() > 0) {
                    angConfig.index = 0
                } else {
                    angConfig.index = -1
                }
            } else if (index < angConfig.index)//移除活动之前的
            {
                angConfig.index--
            }

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun swapServer(fromPosition: Int, toPosition: Int): Int {
        try {
            Collections.swap(angConfig.vmess, fromPosition, toPosition)

            val index = angConfig.index
            if (index == fromPosition) {
                angConfig.index = toPosition
            } else if (index == toPosition) {
                angConfig.index = fromPosition
            }
            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * set active server
     */
    fun setActiveServer(index: Int): Int {
        try {
            if (index < 0 || index > angConfig.vmess.count() - 1) {
                app.curIndex = -1
                return -1
            }
            angConfig.index = index
            app.curIndex = index
            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            app.curIndex = -1
            return -1
        }
        return 0
    }

    /**
     * store config to file
     */
    fun storeConfigFile() {
        try {
            val conf = Gson().toJson(angConfig)
            app.defaultDPreference.setPrefString(ANG_CONFIG, conf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * gen and store v2ray config file
     */
    fun genStoreV2rayConfig(index: Int): Boolean {
        try {
            if (angConfig.index < 0
                    || angConfig.vmess.count() <= 0
                    || angConfig.index > angConfig.vmess.count() - 1
            ) {
                return false
            }
            var index2 = angConfig.index
            if (index >= 0) {
                index2 = index
            }

            val result = V2rayConfigUtil.getV2rayConfig(app, angConfig.vmess[index2])
            if (result.status) {
                app.defaultDPreference.setPrefString(PREF_CURR_CONFIG, result.content)
                app.defaultDPreference.setPrefString(PREF_CURR_CONFIG_GUID, currConfigGuid())
                app.defaultDPreference.setPrefString(PREF_CURR_CONFIG_NAME, currConfigName())
                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun currGeneratedV2rayConfig(): String {
        return app.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
    }

    fun currConfigType(): Int {
        if (angConfig.index < 0
                || angConfig.vmess.count() <= 0
                || angConfig.index > angConfig.vmess.count() - 1
        ) {
            return -1
        }
        return angConfig.vmess[angConfig.index].configType
    }

    fun currConfigName(): String {
        if (angConfig.index < 0
                || angConfig.vmess.count() <= 0
                || angConfig.index > angConfig.vmess.count() - 1
        ) {
            return ""
        }
        return angConfig.vmess[angConfig.index].remarks
    }

    fun currConfigGuid(): String {
        if (angConfig.index < 0
                || angConfig.vmess.count() <= 0
                || angConfig.index > angConfig.vmess.count() - 1
        ) {
            return ""
        }
        return angConfig.vmess[angConfig.index].guid
    }

    /**
     * import config form qrcode or...
     */
    fun importConfig(server: String?, subid: String): Int {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                return R.string.toast_none_data
            }

            var vmess = AngConfig.VmessBean()

            if (server.startsWith(VMESS_PROTOCOL)) {

                val indexSplit = server.indexOf("?")
                if (indexSplit > 0) {
                    vmess = ResolveVmess4Kitsunebi(server,subid)
                } else {

                    var result = server.replace(VMESS_PROTOCOL, "")
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

                    vmess.configType = AppConfig.EConfigType.Vmess
                    vmess.security = "auto"
                    vmess.network = "tcp"
                    vmess.headerType = "none"

                    vmess.configVersion = Utils.parseInt(vmessQRCode.v)
                    vmess.remarks = vmessQRCode.ps
                    vmess.address = vmessQRCode.add
                    vmess.port = Utils.parseInt(vmessQRCode.port)
                    vmess.id = vmessQRCode.id
                    vmess.alterId = Utils.parseInt(vmessQRCode.aid)
                    vmess.network = vmessQRCode.net
                    vmess.headerType = vmessQRCode.type
                    vmess.requestHost = vmessQRCode.host
                    vmess.path = vmessQRCode.path
                    vmess.streamSecurity = vmessQRCode.tls
                    vmess.subid = subid
                }
                upgradeServerVersion(vmess)
                addServer(vmess, -1)

            } else if (server.startsWith(SS_PROTOCOL)) {
                var result = server.replace(SS_PROTOCOL, "")
                val indexSplit = result.indexOf("#")
                if (indexSplit > 0) {
                    try {
                        vmess.remarks = Utils.urlDecode(result.substring(indexSplit + 1, result.length))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    result = result.substring(0, indexSplit)
                }

                //part decode
                val indexS = result.indexOf("@")
                if (indexS > 0) {
                    result = Utils.decode(result.substring(0, indexS)) + result.substring(indexS, result.length)
                } else {
                    result = Utils.decode(result)
                }

                val legacyPattern = "^(.+?):(.*)@(.+?):(\\d+?)$".toRegex()
                val match = legacyPattern.matchEntire(result)
                if (match == null) {
                    return R.string.toast_incorrect_protocol
                }
                vmess.security = match.groupValues[1].toLowerCase()
                vmess.id = match.groupValues[2]
                vmess.address = match.groupValues[3]
                if (vmess.address.firstOrNull() == '[' && vmess.address.lastOrNull() == ']')
                    vmess.address = vmess.address.substring(1, vmess.address.length - 1)
                vmess.port = match.groupValues[4].toInt()
                vmess.subid = subid

                addShadowsocksServer(vmess, -1)
            } else if (server.startsWith(SSR_PROTOCOL)) {
                var server = server.replace(SSR_PROTOCOL, "")
                server = Utils.decode(server)
                //Log.e("------","server="+server)

                val decodedPattern_ssr = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):(.+)/(.*))".toRegex()
                val match = decodedPattern_ssr.matchEntire(server)

                if (match == null) {
                    return R.string.toast_incorrect_protocol
                }

                for (i in 0 until match.groupValues.size) {
                    //Log.e("---",i.toString()+":"+match.groupValues[i])
                }

                vmess.remarks=""
                val decodedPattern_ssr_groupparam = "(?i)(.*)[?&]group=([A-Za-z0-9_=-]*)(.*)".toRegex()
                val match4 = decodedPattern_ssr_groupparam.matchEntire(match.groupValues[8])

                if (match4 != null) {
                    vmess.remarks = Utils.decode(match4.groupValues[2])
                    for (i in 0 until match4.groupValues.size) {
                        //Log.e("---", i.toString() + ":" + match4.groupValues[i])
                    }
                }
                else{//某些不明情况下，Base64会解析错误，无法取得群组名，就直接设为内置吧
                    vmess.remarks=VpnEncrypt.vpnRemark
                }

                if (vmess.remarks==VpnEncrypt.vpnGroupName)vmess.remarks=VpnEncrypt.vpnRemark
                vmess.security = match.groupValues[5].toLowerCase(Locale.ENGLISH)
                vmess.id = Utils.decode(match.groupValues[7]) //is passwd?
                vmess.address = match.groupValues[2].toLowerCase(Locale.ENGLISH)
                if (vmess.address.firstOrNull() == '[' && vmess.address.lastOrNull() == ']')
                    vmess.address = vmess.address.substring(1, vmess.address.length - 1)
                vmess.port = match.groupValues[3].toInt()
                vmess.subid = subid

                addShadowsocksServer(vmess, -1)
            } else if (server.startsWith(SOCKS_PROTOCOL)) {
                var result = server.replace(SOCKS_PROTOCOL, "")
                val indexSplit = result.indexOf("#")
                if (indexSplit > 0) {
                    try {
                        vmess.remarks = Utils.urlDecode(result.substring(indexSplit + 1, result.length))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    result = result.substring(0, indexSplit)
                }

                //part decode
                val indexS = result.indexOf(":")
                if (indexS < 0) {
                    result = Utils.decode(result)
                }

                val legacyPattern = "^(.+?):(\\d+?)$".toRegex()
                val match = legacyPattern.matchEntire(result)
                if (match == null) {
                    return R.string.toast_incorrect_protocol
                }
                vmess.address = match.groupValues[1]
                if (vmess.address.firstOrNull() == '[' && vmess.address.lastOrNull() == ']')
                    vmess.address = vmess.address.substring(1, vmess.address.length - 1)
                vmess.port = match.groupValues[2].toInt()
                vmess.subid = subid

                addSocksServer(vmess, -1)
            } else {
                return R.string.toast_incorrect_protocol
            }
        } catch (e: Exception) {
            Log.e("server---","err",e)
            return -1
        }
        return 0
    }

    private fun ResolveVmess4Kitsunebi(server: String): AngConfig.VmessBean {

        val vmess = AngConfig.VmessBean()

        var result = server.replace(VMESS_PROTOCOL, "")
        val indexSplit = result.indexOf("?")
        if (indexSplit > 0) {
            result = result.substring(0, indexSplit)
        }
        result = Utils.decode(result)

        val arr1 = result.split('@')
        if (arr1.count() != 2) {
            return vmess
        }
        val arr21 = arr1[0].split(':')
        val arr22 = arr1[1].split(':')
        if (arr21.count() != 2 || arr21.count() != 2) {
            return vmess
        }

        vmess.address = arr22[0]
        vmess.port = Utils.parseInt(arr22[1])
        vmess.security = arr21[0]
        vmess.id = arr21[1]

        vmess.security = "chacha20-poly1305"
        vmess.network = "tcp"
        vmess.headerType = "none"
        vmess.remarks = "Alien"
        vmess.alterId = 0

        return vmess
    }

    private fun ResolveVmess4Kitsunebi(server: String,subid:String): AngConfig.VmessBean {

        val vmess = AngConfig.VmessBean()

        var result = server.replace(VMESS_PROTOCOL, "")
        val indexSplit = result.indexOf("?")
        if (indexSplit > 0) {
            result = result.substring(0, indexSplit)
        }
        result = Utils.decode(result)

        val arr1 = result.split('@')
        if (arr1.count() != 2) {
            return vmess
        }
        val arr21 = arr1[0].split(':')
        val arr22 = arr1[1].split(':')
        if (arr21.count() != 2 || arr21.count() != 2) {
            return vmess
        }
        vmess.subid=subid
        vmess.address = arr22[0]
        vmess.port = Utils.parseInt(arr22[1])
        vmess.security = arr21[0]
        vmess.id = arr21[1]

        vmess.security = "chacha20-poly1305"
        vmess.network = "tcp"
        vmess.headerType = "none"
        vmess.remarks = "Alien"
        vmess.alterId = 0

        return vmess
    }

    /**
     * share config
     */
    fun shareConfig(index: Int): String {
        try {
            if (index < 0 || index > angConfig.vmess.count() - 1) {
                return ""
            }

            val vmess = angConfig.vmess[index]
            if (vmess.remarks== VpnEncrypt.vpnRemark)return ""
            if (angConfig.vmess[index].configType == AppConfig.EConfigType.Vmess) {

                val vmessQRCode = VmessQRCode()
                vmessQRCode.v = vmess.configVersion.toString()
                vmessQRCode.ps = vmess.remarks
                vmessQRCode.add = vmess.address
                vmessQRCode.port = vmess.port.toString()
                vmessQRCode.id = vmess.id
                vmessQRCode.aid = vmess.alterId.toString()
                vmessQRCode.net = vmess.network
                vmessQRCode.type = vmess.headerType
                vmessQRCode.host = vmess.requestHost
                vmessQRCode.path = vmess.path
                vmessQRCode.tls = vmess.streamSecurity
                val json = Gson().toJson(vmessQRCode)
                val conf = VMESS_PROTOCOL + Utils.encode(json)

                return conf
            } else if (angConfig.vmess[index].configType == AppConfig.EConfigType.Shadowsocks) {
                val remark = "#" + Utils.urlEncode(vmess.remarks)
                val url = String.format("%s:%s@%s:%s",
                        vmess.security,
                        vmess.id,
                        vmess.address,
                        vmess.port)
                return SS_PROTOCOL + Utils.encode(url) + remark
            } else if (angConfig.vmess[index].configType == AppConfig.EConfigType.Socks) {
                val remark = "#" + Utils.urlEncode(vmess.remarks)
                val url = String.format("%s:%s",
                        vmess.address,
                        vmess.port)
                return SOCKS_PROTOCOL + Utils.encode(url) + remark
            } else {
                return ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * share2Clipboard
     */
    fun share2Clipboard(index: Int): Int {
        try {
            val conf = shareConfig(index)
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
    fun shareAll2Clipboard(): Int {
        try {
            val sb = StringBuilder()
            for (k in 0 until angConfig.vmess.count()) {
                val url = shareConfig(k)
                if (TextUtils.isEmpty(url)) {
                    continue
                }
                sb.append(url)
                sb.appendln()
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
    fun share2QRCode(index: Int): Bitmap? {
        try {
            val conf = shareConfig(index)
            if (TextUtils.isEmpty(conf)) {
                return null
            }
            val bitmap = Utils.createQRCode(conf)
            return bitmap

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * shareFullContent2Clipboard
     */
    fun shareFullContent2Clipboard(index: Int): Int {
        try {
            if (AngConfigManager.genStoreV2rayConfig(index)) {
                val configContent = app.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
                Utils.setClipboard(app.applicationContext, configContent)
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
     * import customize config
     */
    fun importCustomizeConfig(server: String?): Int {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                return R.string.toast_none_data
            }

            val guid = System.currentTimeMillis().toString()
            app.defaultDPreference.setPrefString(ANG_CONFIG + guid, server)

            //add
            val vmess = AngConfig.VmessBean()
            vmess.configVersion = 2
            vmess.configType = AppConfig.EConfigType.Custom
            vmess.guid = guid
            vmess.remarks = vmess.guid

            vmess.security = ""
            vmess.network = ""
            vmess.headerType = ""
            vmess.address = ""
            vmess.port = 0
            vmess.id = ""
            vmess.alterId = 0
            vmess.network = ""
            vmess.headerType = ""
            vmess.requestHost = ""
            vmess.streamSecurity = ""

            angConfig.vmess.add(vmess)
            if (angConfig.vmess.count() == 1) {
                angConfig.index = 0
            }
            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     * getIndexViaGuid
     */
    fun getIndexViaGuid(guid: String): Int {
        try {
            if (TextUtils.isEmpty(guid)) {
                return -1
            }
            for (i in angConfig.vmess.indices) {
                if (angConfig.vmess[i].guid == guid) {
                    return i
                }
            }
            return -1
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }

    /**
     * upgrade
     */
    fun upgradeServerVersion(vmess: AngConfig.VmessBean): Int {
        try {
            if (vmess.configVersion == 2) {
                return 0
            }

            when (vmess.network) {
                "kcp" -> {
                }
                "ws" -> {
                    var path = ""
                    var host = ""
                    val lstParameter = vmess.requestHost.split(";")
                    if (lstParameter.size > 0) {
                        path = lstParameter.get(0).trim()
                    }
                    if (lstParameter.size > 1) {
                        path = lstParameter.get(0).trim()
                        host = lstParameter.get(1).trim()
                    }
                    vmess.path = path
                    vmess.requestHost = host
                }
                "h2" -> {
                    var path = ""
                    var host = ""
                    val lstParameter = vmess.requestHost.split(";")
                    if (lstParameter.size > 0) {
                        path = lstParameter.get(0).trim()
                    }
                    if (lstParameter.size > 1) {
                        path = lstParameter.get(0).trim()
                        host = lstParameter.get(1).trim()
                    }
                    vmess.path = path
                    vmess.requestHost = host
                }
                else -> {
                }
            }
            vmess.configVersion = 2
            return 0
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
    }


    fun addCustomServer(vmess: AngConfig.VmessBean, index: Int): Int {
        try {
            vmess.configVersion = 2
            vmess.configType = AppConfig.EConfigType.Custom

            if (index >= 0) {
                //edit
                angConfig.vmess[index] = vmess
            } else {
                //add
                vmess.guid = System.currentTimeMillis().toString()
                angConfig.vmess.add(vmess)
                if (angConfig.vmess.count() == 1) {
                    angConfig.index = 0
                }
            }

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun addShadowsocksServer(vmess: AngConfig.VmessBean, index: Int): Int {
        try {
            if (vmess.address=="127.0.0.1" || vmess.address=="localhost") return -1

            if (vmess.subid != VpnEncrypt.builtinSubID) {
                for (k in 0 until configs.vmess.count()) {//Check for duplicates
                    if (configs.vmess[k].address == vmess.address && configs.vmess[k].port == vmess.port) return -1
                }
            }

            vmess.configVersion = 2
            vmess.configType = AppConfig.EConfigType.Shadowsocks

            if (index >= 0) {
                //edit
                angConfig.vmess[index] = vmess
            } else {
                //add
                vmess.guid = System.currentTimeMillis().toString()
                angConfig.vmess.add(vmess)
                if (angConfig.vmess.count() == 1) {
                    angConfig.index = 0
                }
            }

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun addSocksServer(vmess: AngConfig.VmessBean, index: Int): Int {
        try {
            vmess.configVersion = 2
            vmess.configType = AppConfig.EConfigType.Socks

            if (index >= 0) {
                //edit
                angConfig.vmess[index] = vmess
            } else {
                //add
                vmess.guid = System.currentTimeMillis().toString()
                angConfig.vmess.add(vmess)
                if (angConfig.vmess.count() == 1) {
                    angConfig.index = 0
                }
            }

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun importBatchConfig(servers: String?, subid: String): Int {
        try {
            if (servers == null || servers =="") {
                return 0
            }
            removeServerViaSubid(subid)

//            var servers = server
//            if (server.indexOf("vmess") >= 0 && server.indexOf("vmess") == server.lastIndexOf("vmess")) {
//                servers = server.replace("\n", "")
//            }

            var count = 0
            var limit = -1
            var serverList=servers.trim().lines()
            if (servers.indexOf("MAX=") == 0) {
                limit = servers.split("\n")[0].split("MAX=")[1]
                        .replace("\\D+".toRegex(), "").toInt()
                serverList=serverList.drop(1)
            }
            //Log.e("------","limit is "+limit)
            //Log.e("------","serverList1:"+serverList)
            if (limit != -1 && limit < serverList.size) {
                serverList = serverList.shuffled().take(limit)
            }
            //Log.e("------","serverList2:"+serverList)
            serverList.forEach {
                        val resId = importConfig(it, subid)
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

    fun saveSubItem(subItem: ArrayList<AngConfig.SubItemBean>): Int {
        try {
            if (subItem.count() <= 0) {
                return -1
            }
            for (k in 0 until subItem.count()) {
                if (TextUtils.isEmpty(subItem[k].id)) {
                    subItem[k].id = Utils.getUuid()
                }
            }
            angConfig.subItem = subItem

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    fun removeServerViaSubid(subid: String): Int {
        if (TextUtils.isEmpty(subid) || configs.vmess.count() <= 0) {
            return -1
        }

        for (k in configs.vmess.count() - 1 downTo 0) {
            if (configs.vmess[k].subid.equals(subid)) {
                angConfig.vmess.removeAt(k)
            }
        }
        angConfig.vmess.trimToSize()
        storeConfigFile()
        return 0
    }

    fun addSubItem(subItem: AngConfig.SubItemBean, index: Int): Int {
        try {
            if (index >= 0) {
                //edit
                angConfig.subItem[index] = subItem
            } else {
                //add
                angConfig.subItem.add(subItem)
            }

            saveSubItem(angConfig.subItem)
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

    /**
     *
     */
    fun removeSubItem(index: Int): Int {
        try {
            if (index < 0 || index > angConfig.subItem.count() - 1) {
                return -1
            }

            //删除
            angConfig.subItem.removeAt(index)

            storeConfigFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return 0
    }

}