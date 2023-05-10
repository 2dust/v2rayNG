package com.v2ray.ang.util

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.util.Base64
import java.util.*
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.icu.util.Calendar
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.text.format.DateUtils
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import java.net.*
import com.v2ray.ang.service.V2RayServiceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.conscrypt.Conscrypt
import java.io.IOException
import java.security.Security
import java.util.concurrent.TimeUnit

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
                if (arr.count() == 2 && Integer.parseInt(arr[1]) > 0) {
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
//        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    fun openUri(context: Context, uriString: String): Boolean {
        val uri = Uri.parse(uriString)
        val intent=Intent(Intent.ACTION_VIEW, uri)
        return try {
            context.startActivity(intent)
            true
        }catch (e:Exception){
            false
        }

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
            URLDecoder.decode(URLDecoder.decode(url), "utf-8")
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

    fun getUrlContext(url: String, timeout: Int): String {
        if(true)
            return getUrlContentOkHttp(url,timeout.toLong()).content?:""
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

    fun getUrlContentOkHttp(urlStr: String?, timeout: Long=10000, direct:Boolean=true,proxy:Boolean=true): Response {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            // Create OkHttp Client
            var clientBuilder = OkHttpClient.Builder()
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)

            if(!direct&&proxy) {
                clientBuilder.proxy(HiddifyUtils.socksProxy())
            }
            val client=clientBuilder.build()


            // Create URL
            val url = URL(urlStr)
            // Build request
            val requestBuilder = Request.Builder().url(url).header("User-Agent", "HiddifyNG/${BuildConfig.VERSION_NAME}").header("Connection", "close")
            url.userInfo?.let {
                requestBuilder.header("Authorization", "Basic ${encode(urlDecode(it))}")
            }
            val request = requestBuilder.build()
            // Execute request
            val response = client.newCall(request).execute()
            val headers = response.headers.toMultimap()

            val content = response.body?.string() ?: ""

            response.close()

            return Response(headers, content, urlStr)
        }catch (e:Exception){

            if(direct&&proxy) {
                AngApplication.appContext.toast(R.string.msg_downloading_content_failed_no_proxy)
                return getUrlContentOkHttp(urlStr, timeout, direct = false, proxy = true)
            }
            throw e
        }

    }
    @Throws(IOException::class)
    fun getUrlContentWithCustomUserAgent(urlStr: String?): Response {

        if(true)
            return getUrlContentOkHttp(urlStr)
        val url = URL(urlStr)
        val conn = url.openConnection()
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("User-agent", "HiddifyNG/${BuildConfig.VERSION_NAME}")
        url.userInfo?.let {
            conn.setRequestProperty("Authorization",
                "Basic ${encode(urlDecode(it))}")
        }
        conn.useCaches = false

        val headers = conn.headerFields

        val content = conn.inputStream.use {
            it.bufferedReader().readText()
        }

        return Response(headers, content,urlStr)
    }
    data class Response(val headers: Map<String, List<String>>?, val content: String?,val url:String?=null)


    fun getDarkModeStatus(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK
        return mode == UI_MODE_NIGHT_YES
    }

    fun getIpv6Address(address: String): String {
        return if (isIpv6Address(address)) {
            String.format("[%s]", address)
        } else {
            address
        }
    }

    fun getLocale(context: Context): Locale =
        when (settingsStorage?.decodeString(AppConfig.PREF_LANGUAGE) ?: "auto") {
            "auto" -> getSysLocale()
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
    fun toGig(inbytes:Long):String{
        var gb =inbytes.toDouble() / 1073741824.0
        return String.format("%.1f GB", gb)
    }
    fun timeToRelativeDate(time: Long): String {
        if (time<0)
            return ""

        val now = System.currentTimeMillis()/1000
        val diffInMillis = (time-now)/86400
        val flags = DateUtils.FORMAT_SHOW_DATE// or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        if (diffInMillis<0)
            return "Expired"
        return "Remain "+ diffInMillis.toInt() +" days"

    }
    fun timeToRelativeDateOld(time: Long): String {
        if (time<0)
            return ""

        val now = System.currentTimeMillis()/1000
        val diffInMillis = (time-now)/86400
        val flags = DateUtils.FORMAT_SHOW_DATE// or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        if (diffInMillis<0)
            return "Expired "+ (-diffInMillis.toInt()) +" days ago"
        return "Expire in "+ diffInMillis.toInt() +" days"

    }

    fun getQueryParameterValueCaseInsensitive(uri: Uri, paramName: String): String? {
        val queryParameters = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) }
        val lowercaseParamName = paramName.toLowerCase()
        return queryParameters.keys.find { it.toLowerCase() == lowercaseParamName }?.let { queryParameters[it] }
    }
}

