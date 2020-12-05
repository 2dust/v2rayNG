package com.v2ray.ang.util

import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.util.Base64
import com.google.zxing.WriterException
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.EncodeHintType
import java.util.*
import kotlin.collections.HashMap
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.defaultDPreference
import com.v2ray.ang.extension.responseLength
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.v2RayApplication
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.SettingsActivity
import kotlinx.coroutines.isActive
import me.dozen.dpreference.DPreference
import java.io.IOException
import java.net.*
import kotlin.coroutines.coroutineContext

object Utils {

    val tcpTestingSockets = ArrayList<Socket?>()

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
        try {
            return Integer.parseInt(str)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    /**
     * get text from clipboard
     */
    fun getClipboard(context: Context): String {
        try {
            val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return cmb.primaryClip?.getItemAt(0)?.text.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
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
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(charset("UTF-8"))
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * base64 encode
     */
    fun encode(text: String): String {
        try {
            return Base64.encodeToString(text.toByteArray(charset("UTF-8")), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * get remote dns servers from preference
     */
    fun getRemoteDnsServers(defaultDPreference: DPreference): ArrayList<String> {
        val remoteDns = defaultDPreference.getPrefString(SettingsActivity.PREF_REMOTE_DNS, AppConfig.DNS_AGENT)
        val ret = ArrayList<String>()
        if (!TextUtils.isEmpty(remoteDns)) {
            remoteDns
                    .split(",")
                    .forEach {
                        if (Utils.isPureIpAddress(it)) {
                            ret.add(it)
                        }
                    }
        }
        if (ret.size == 0) {
            ret.add(AppConfig.DNS_AGENT)
        }
        return ret
    }

    /**
     * get remote dns servers from preference
     */
    fun getDomesticDnsServers(defaultDPreference: DPreference): ArrayList<String> {
        val domesticDns = defaultDPreference.getPrefString(SettingsActivity.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        val ret = ArrayList<String>()
        if (!TextUtils.isEmpty(domesticDns)) {
            domesticDns
                    .split(",")
                    .forEach {
                        if (Utils.isPureIpAddress(it)) {
                            ret.add(it)
                        }
                    }
        }
        if (ret.size == 0) {
            ret.add(AppConfig.DNS_DIRECT)
        }
        return ret
    }

    /**
     * create qrcode using zxing
     */
    fun createQRCode(text: String, size: Int = 800): Bitmap? {
        try {
            val hints = HashMap<EncodeHintType, String>()
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8")
            val bitMatrix = QRCodeWriter().encode(text,
                    BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0..size - 1) {
                for (x in 0..size - 1) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * size + x] = 0xff000000.toInt()
                    } else {
                        pixels[y * size + x] = 0xffffffff.toInt()
                    }

                }
            }
            val bitmap = Bitmap.createBitmap(size, size,
                    Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }
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
            var octets = addr.split('.').toTypedArray()
            if (octets.size == 4) {
                if(octets[3].indexOf(":") > 0) {
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

    /**
     * is valid url
     */
    fun isValidUrl(value: String?): Boolean {
        try {
            if (Patterns.WEB_URL.matcher(value).matches() || URLUtil.isValidUrl(value)) {
                return true
            }
        } catch (e: WriterException) {
            e.printStackTrace()
            return false
        }
        return false
    }

    fun startVServiceFromToggle(context: Context): Boolean {
        val result = context.defaultDPreference.getPrefString(AppConfig.PREF_CURR_CONFIG, "")
        if (result.isBlank()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        V2RayServiceManager.startV2Ray(context)
        return true
    }

    /**
     * startVService
     */
    fun startVService(context: Context, guid: String): Boolean {
        val index = AngConfigManager.getIndexViaGuid(guid)
        context.v2RayApplication.curIndex=index
        return startVService(context, index)
    }

    /**
     * startVService
     */
    fun startVService(context: Context, index: Int): Boolean {
        if (AngConfigManager.setActiveServer(index) < 0) {
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
        try {
            return UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun urlDecode(url: String): String {
        try {
            return URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            return url
        }
    }

    fun urlEncode(url: String): String {
        try {
            return URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            return url
        }
    }

    fun testConnection(context: Context, port: Int): String {
        var result: String
        var conn: HttpURLConnection? = null

        try {
            val url = URL("https",
                    "www.google.com",
                    "/generate_204")

            conn = url.openConnection(
                Proxy(Proxy.Type.HTTP,
                InetSocketAddress("127.0.0.1", port + 1))) as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("Connection", "close")
            conn.instanceFollowRedirects = false
            conn.useCaches = false

            val start = SystemClock.elapsedRealtime()
            val code = conn.responseCode
            val elapsed = SystemClock.elapsedRealtime() - start

            if (code == 204 || code == 200 && conn.responseLength == 0L) {
                result = context.getString(R.string.connection_test_available, elapsed)
            } else {
                throw IOException(context.getString(R.string.connection_test_error_status_code, code))
            }
        } catch (e: IOException) {
            // network exception
            Log.d(AppConfig.ANG_PACKAGE,"testConnection IOException: "+Log.getStackTraceString(e))
            result = context.getString(R.string.connection_test_error, e.message)
        } catch (e: Exception) {
            // library exception, eg sumsung
            Log.d(AppConfig.ANG_PACKAGE,"testConnection Exception: "+Log.getStackTraceString(e))
            result = context.getString(R.string.connection_test_error, e.message)
        } finally {
            conn?.disconnect()
        }

        return result
    }

    /**
     * package path
     */
    fun packagePath(context: Context): String {
        var path = context.filesDir.toString()
        path = path.replace("files", "")
        //path += "tun2socks"

        return path
    }

    /**
     * readTextFromAssets
     */
    fun readTextFromAssets(app: AngApplication, fileName: String): String {
        val content = app.assets.open(fileName).bufferedReader().use {
            it.readText()
        }
        return content
    }

    /**
     * ping
     */
    fun ping(url: String): String {
        try {
            val command = "/system/bin/ping -c 3 $url"
            val process = Runtime.getRuntime().exec(command)
            val allText = process.inputStream.bufferedReader().use { it.readText() }
            if (!TextUtils.isEmpty(allText)) {
                val tempInfo = allText.substring(allText.indexOf("min/avg/max/mdev") + 19)
                val temps = tempInfo.split("/".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                if (temps.count() > 0 && temps[0].length < 10) {
                    return temps[0].toFloat().toInt().toString() + "ms"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "-1ms"
    }

    /**
     * tcping
     */
    suspend fun tcping(url: String, port: Int): String {
        var time = -1L
        for (k in 0 until 2) {
            val one = socketConnectTime(url, port)
            if (!coroutineContext.isActive) {
                break
            }
            if (one != -1L  )
                if(time == -1L || one < time) {
                time = one
            }
        }
        return time.toString() + "ms"
    }

    fun socketConnectTime(url: String, port: Int): Long {
        try {
            val socket = Socket()
            synchronized(this) {
                tcpTestingSockets.add(socket)
            }
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(url, port))
            val time = System.currentTimeMillis() - start
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            socket.close()
            return time
        } catch (e: UnknownHostException) {
            e.printStackTrace()
        } catch (e: IOException) {
            Log.d(AppConfig.ANG_PACKAGE, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return -1
    }

    fun closeAllTcpSockets() {
        synchronized(this) {
            tcpTestingSockets.forEach {
                it?.close()
            }
            tcpTestingSockets.clear()
        }
    }
}

