package com.v2ray.ang.util

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.util.Base64
import java.util.*
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import java.net.*
import com.v2ray.ang.service.V2RayServiceManager
import java.io.IOException

object Utils {

    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    /**
     * convert string to editalbe for kotlin
     *
     * @param text
     * @return
     */
    fun getEditable(text: String): Editable {
        return Editable.Factory.getInstance().newEditable(text)
    }

    /**
     * find value in array position
     */
    fun arrayFind(array: Array<out String>, value: String): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return -1
    }

    /**
     * parseInt
     */
    fun parseInt(str: String): Int {
        return parseInt(str, 0)
    }

    fun parseInt(str: String?, default: Int): Int {
        str ?: return default
        return try {
            Integer.parseInt(str)
        } catch (e: Exception) {
            e.printStackTrace()
            default
        }
    }

    /**
     * get text from clipboard
     */
    fun getClipboard(context: Context): String {
        return try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * set text to clipboard
     */
    fun setClipboard(context: Context, content: String) {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(null, content)
            cmb.setPrimaryClip(clipData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * base64 decode
     */
    fun decode(text: String): String {
        tryDecodeBase64(text)?.let { return it }
        if (text.endsWith('=')) {
            // try again for some loosely formatted base64
            tryDecodeBase64(text.trimEnd('='))?.let { return it }
        }
        return ""
    }

    fun tryDecodeBase64(text: String): String? {
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(charset("UTF-8"))
        } catch (e: Exception) {
            Log.i(ANG_PACKAGE, "Parse base64 standard failed $e")
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE)).toString(charset("UTF-8"))
        } catch (e: Exception) {
            Log.i(ANG_PACKAGE, "Parse base64 url safe failed $e")
        }
        return null
    }

    /**
     * base64 encode
     */
    fun encode(text: String): String {
        return try {
            Base64.encodeToString(text.toByteArray(charset("UTF-8")), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * get remote dns servers from preference
     */
    fun getRemoteDnsServers(): List<String> {
        val remoteDns = settingsStorage?.decodeString(AppConfig.PREF_REMOTE_DNS) ?: AppConfig.DNS_AGENT
        val ret = remoteDns.split(",").filter { isPureIpAddress(it) || isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_AGENT)
        }
        return ret
    }

    fun getVpnDnsServers(): List<String> {
        val vpnDns = settingsStorage?.decodeString(AppConfig.PREF_VPN_DNS)
                ?: settingsStorage?.decodeString(AppConfig.PREF_REMOTE_DNS)
                ?: AppConfig.DNS_AGENT
        return vpnDns.split(",").filter { isPureIpAddress(it) }
        // allow empty, in that case dns will use system default
    }

    /**
     * get remote dns servers from preference
     */
    fun getDomesticDnsServers(): List<String> {
        val domesticDns = settingsStorage?.decodeString(AppConfig.PREF_DOMESTIC_DNS) ?: AppConfig.DNS_DIRECT
        val ret = domesticDns.split(",").filter { isPureIpAddress(it) || isCoreDNSAddress(it) }
        if (ret.isEmpty()) {
            return listOf(AppConfig.DNS_DIRECT)
        }
        return ret
    }

    /**
     * is ip address
     */
    fun isIpAddress(value: String): Boolean {
        try {
            var addr = value
            if (addr.isEmpty() || addr.isBlank()) {
                return false
            }
            //CIDR
            if (addr.indexOf("/") > 0) {
                val arr = addr.split("/")
                if (arr.count() == 2 && Integer.parseInt(arr[1]) > -1) {
                    addr = arr[0]
                }
            }

            // "::ffff:192.168.173.22"
            // "[::ffff:192.168.173.22]:80"
            if (addr.startsWith("::ffff:") && '.' in addr) {
                addr = addr.drop(7)
            } else if (addr.startsWith("[::ffff:") && '.' in addr) {
                addr = addr.drop(8).replace("]", "")
            }

            // addr = addr.toLowerCase()
            val octets = addr.split('.').toTypedArray()
            if (octets.size == 4) {
                if (octets[3].indexOf(":") > 0) {
                    addr = addr.substring(0, addr.indexOf(":"))
                }
                return isIpv4Address(addr)
            }

            // Ipv6addr [2001:abc::123]:8080
            return isIpv6Address(addr)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun isPureIpAddress(value: String): Boolean {
        return (isIpv4Address(value) || isIpv6Address(value))
    }

    fun isIpv4Address(value: String): Boolean {
        val regV4 = Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
        return regV4.matches(value)
    }

    fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.indexOf("[") == 0 && addr.lastIndexOf("]") > 0) {
            addr = addr.drop(1)
            addr = addr.dropLast(addr.count() - addr.lastIndexOf("]"))
        }
        val regV6 = Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")
        return regV6.matches(addr)
    }

    private fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https") || s.startsWith("tcp") || s.startsWith("quic")
    }

    /**
     * is valid url
     */
    fun isValidUrl(value: String?): Boolean {
        try {
            if (value != null && Patterns.WEB_URL.matcher(value).matches() || URLUtil.isValidUrl(value)) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return false
    }

    fun startVServiceFromToggle(context: Context): Boolean {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        V2RayServiceManager.startV2Ray(context)
        return true
    }

    /**
     * stopVService
     */
    fun stopVService(context: Context) {
        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    fun openUri(context: Context, uriString: String) {
        val uri = Uri.parse(uriString)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * uuid
     */
    fun getUuid(): String {
        return try {
            UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }

    fun urlEncode(url: String): String {
        return try {
            URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }


    /**
     * readTextFromAssets
     */
    fun readTextFromAssets(context: Context, fileName: String): String {
        val content = context.assets.open(fileName).bufferedReader().use {
            it.readText()
        }
        return content
    }

    fun userAssetPath(context: Context?): String {
        if (context == null)
            return ""
        val extDir = context.getExternalFilesDir(AppConfig.DIR_ASSETS)
                ?: return context.getDir(AppConfig.DIR_ASSETS, 0).absolutePath
        return extDir.absolutePath
    }

    fun getDeviceIdForXUDPBaseKey(): String {
        val androidId = Settings.Secure.ANDROID_ID.toByteArray(charset("UTF-8"))
        return Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
    }

    fun getUrlContext(url: String, timeout: Int): String {
        var result: String
        var conn: HttpURLConnection? = null

        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.setRequestProperty("Connection", "close")
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            //val code = conn.responseCode
            result = conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            result = ""
        } finally {
            conn?.disconnect()
        }
        return result
    }

    @Throws(IOException::class)
    fun getUrlContentWithCustomUserAgent(urlStr: String?): String {
        val url = URL(urlStr)
        val conn = url.openConnection()
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("User-agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
        url.userInfo?.let {
            conn.setRequestProperty("Authorization",
                "Basic ${encode(urlDecode(it))}")
        }
        conn.useCaches = false
        return conn.inputStream.use {
            it.bufferedReader().readText()
        }
    }

    fun getDarkModeStatus(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK
        return mode != UI_MODE_NIGHT_NO
    }

    fun getIpv6Address(address: String): String {
        return if (isIpv6Address(address) && !address.contains('[') && !address.contains(']')) {
            String.format("[%s]", address)
        } else {
            address
        }
    }

    fun getLocale(context: Context): Locale =
        when (settingsStorage?.decodeString(AppConfig.PREF_LANGUAGE) ?: "auto") {
            "auto" ->  getSysLocale()
            "en" -> Locale("en")
            "zh-rCN" -> Locale("zh", "CN")
            "zh-rTW" -> Locale("zh", "TW")
            "vi" -> Locale("vi")
            "ru" -> Locale("ru")
            "fa" -> Locale("fa")
            else -> getSysLocale()
        }

    private fun getSysLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.getDefault()[0]
    } else {
        Locale.getDefault()
    }

    fun fixIllegalUrl(str: String): String {
        return str
            .replace(" ","%20")
            .replace("|","%7C")
    }

    fun removeWhiteSpace(str: String?): String? {
        return str?.replace(" ", "")
    }

    fun idnToASCII(str: String): String {
        val url = URL(str)
        return URL(url.protocol, IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED), url.port, url.file)
            .toExternalForm()
    }

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

}

