package com.v2ray.ang.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.text.Editable
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.LOOPBACK
import java.io.IOException
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

object Utils {

    /**
     * Convert string to editable for Kotlin.
     *
     * @param text The string to convert.
     * @return An Editable instance containing the text.
     */
    fun getEditable(text: String?): Editable {
        return Editable.Factory.getInstance().newEditable(text.orEmpty())
    }

    /**
     * Find the position of a value in an array.
     *
     * @param array The array to search.
     * @param value The value to find.
     * @return The index of the value in the array, or -1 if not found.
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
     * Parse a string to an integer.
     *
     * @param str The string to parse.
     * @return The parsed integer, or 0 if parsing fails.
     */
    fun parseInt(str: String): Int {
        return parseInt(str, 0)
    }

    /**
     * Parse a string to an integer with a default value.
     *
     * @param str The string to parse.
     * @param default The default value if parsing fails.
     * @return The parsed integer, or the default value if parsing fails.
     */
    fun parseInt(str: String?, default: Int): Int {
        return str?.toIntOrNull() ?: default
    }

    /**
     * Get text from the clipboard.
     *
     * @param context The context to use.
     * @return The text from the clipboard, or an empty string if an error occurs.
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
     * Set text to the clipboard.
     *
     * @param context The context to use.
     * @param content The text to set to the clipboard.
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
     * Decode a base64 encoded string.
     *
     * @param text The base64 encoded string.
     * @return The decoded string, or an empty string if decoding fails.
     */
    fun decode(text: String?): String {
        return tryDecodeBase64(text) ?: text?.trimEnd('=')?.let { tryDecodeBase64(it) }.orEmpty()
    }

    /**
     * Try to decode a base64 encoded string.
     *
     * @param text The base64 encoded string.
     * @return The decoded string, or null if decoding fails.
     */
    fun tryDecodeBase64(text: String?): String? {
        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.i(ANG_PACKAGE, "Parse base64 standard failed $e")
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.i(ANG_PACKAGE, "Parse base64 url safe failed $e")
        }
        return null
    }

    /**
     * Encode a string to base64.
     *
     * @param text The string to encode.
     * @return The base64 encoded string, or an empty string if encoding fails.
     */
    fun encode(text: String): String {
        return try {
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Check if a string is a valid IP address.
     *
     * @param value The string to check.
     * @return True if the string is a valid IP address, false otherwise.
     */
    fun isIpAddress(value: String?): Boolean {
        try {
            if (value.isNullOrEmpty()) {
                return false
            }
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

    /**
     * Check if a string is a pure IP address (IPv4 or IPv6).
     *
     * @param value The string to check.
     * @return True if the string is a pure IP address, false otherwise.
     */
    fun isPureIpAddress(value: String): Boolean {
        return isIpv4Address(value) || isIpv6Address(value)
    }

    /**
     * Check if a string is a valid IPv4 address.
     *
     * @param value The string to check.
     * @return True if the string is a valid IPv4 address, false otherwise.
     */
    private fun isIpv4Address(value: String): Boolean {
        val regV4 =
            Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
        return regV4.matches(value)
    }

    /**
     * Check if a string is a valid IPv6 address.
     *
     * @param value The string to check.
     * @return True if the string is a valid IPv6 address, false otherwise.
     */
    private fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.indexOf("[") == 0 && addr.lastIndexOf("]") > 0) {
            addr = addr.drop(1)
            addr = addr.dropLast(addr.count() - addr.lastIndexOf("]"))
        }
        val regV6 =
            Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")
        return regV6.matches(addr)
    }

    /**
     * Check if a string is a CoreDNS address.
     *
     * @param s The string to check.
     * @return True if the string is a CoreDNS address, false otherwise.
     */
    fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https")
                || s.startsWith("tcp")
                || s.startsWith("quic")
                || s == "localhost"
    }

    /**
     * Check if a string is a valid URL.
     *
     * @param value The string to check.
     * @return True if the string is a valid URL, false otherwise.
     */
    fun isValidUrl(value: String?): Boolean {
        try {
            if (value.isNullOrEmpty()) {
                return false
            }
            if (Patterns.WEB_URL.matcher(value).matches()
                || Patterns.DOMAIN_NAME.matcher(value).matches()
                || URLUtil.isValidUrl(value)
            ) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return false
    }

    /**
     * Open a URI in a browser.
     *
     * @param context The context to use.
     * @param uriString The URI string to open.
     */
    fun openUri(context: Context, uriString: String) {
        val uri = uriString.toUri()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * Generate a UUID.
     *
     * @return A UUID string without dashes.
     */
    fun getUuid(): String {
        return try {
            UUID.randomUUID().toString().replace("-", "")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Decode a URL-encoded string.
     *
     * @param url The URL-encoded string.
     * @return The decoded string, or the original string if decoding fails.
     */
    fun urlDecode(url: String): String {
        return try {
            URLDecoder.decode(url, Charsets.UTF_8.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }

    /**
     * Encode a string to URL-encoded format.
     *
     * @param url The string to encode.
     * @return The URL-encoded string, or the original string if encoding fails.
     */
    fun urlEncode(url: String): String {
        return try {
            URLEncoder.encode(url, Charsets.UTF_8.toString()).replace("+", "%20")
        } catch (e: Exception) {
            e.printStackTrace()
            url
        }
    }

    /**
     * Read text from an asset file.
     *
     * @param context The context to use.
     * @param fileName The name of the asset file.
     * @return The content of the asset file as a string.
     */
    fun readTextFromAssets(context: Context?, fileName: String): String {
        if (context == null) {
            return ""
        }
        val content = context.assets.open(fileName).bufferedReader().use {
            it.readText()
        }
        return content
    }

    /**
     * Get the path to the user asset directory.
     *
     * @param context The context to use.
     * @return The path to the user asset directory.
     */
    fun userAssetPath(context: Context?): String {
        if (context == null)
            return ""
        val extDir = context.getExternalFilesDir(AppConfig.DIR_ASSETS)
            ?: return context.getDir(AppConfig.DIR_ASSETS, 0).absolutePath
        return extDir.absolutePath
    }

    /**
     * Get the path to the backup directory.
     *
     * @param context The context to use.
     * @return The path to the backup directory.
     */
    fun backupPath(context: Context?): String {
        if (context == null)
            return ""
        val extDir = context.getExternalFilesDir(AppConfig.DIR_BACKUPS)
            ?: return context.getDir(AppConfig.DIR_BACKUPS, 0).absolutePath
        return extDir.absolutePath
    }

    /**
     * Get the device ID for XUDP base key.
     *
     * @return The device ID for XUDP base key.
     */
    fun getDeviceIdForXUDPBaseKey(): String {
        val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
    }

    /**
     * Get the dark mode status.
     *
     * @param context The context to use.
     * @return True if dark mode is enabled, false otherwise.
     */
    fun getDarkModeStatus(context: Context): Boolean {
        return context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_NO
    }

    /**
     * Get the IPv6 address in a formatted string.
     *
     * @param address The IPv6 address.
     * @return The formatted IPv6 address, or the original address if not valid.
     */
    fun getIpv6Address(address: String?): String {
        if (address == null) {
            return ""
        }
        return if (isIpv6Address(address) && !address.contains('[') && !address.contains(']')) {
            String.format("[%s]", address)
        } else {
            address
        }
    }

    /**
     * Get the system locale.
     *
     * @return The system locale.
     */
    fun getSysLocale(): Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        LocaleList.getDefault()[0]
    } else {
        Locale.getDefault()
    }

    /**
     * Fix illegal characters in a URL.
     *
     * @param str The URL string.
     * @return The URL string with illegal characters replaced.
     */
    fun fixIllegalUrl(str: String): String {
        return str
            .replace(" ", "%20")
            .replace("|", "%7C")
    }

    /**
     * Find a free port from a list of ports.
     *
     * @param ports The list of ports to check.
     * @return The first free port found.
     * @throws IOException If no free port is found.
     */
    fun findFreePort(ports: List<Int>): Int {
        for (port in ports) {
            try {
                return ServerSocket(port).use { it.localPort }
            } catch (ex: IOException) {
                continue  // try next port
            }
        }

        // if the program gets here, no port in the range was found
        throw IOException("no free port found")
    }

    /**
     * Check if a string is a valid subscription URL.
     *
     * @param value The string to check.
     * @return True if the string is a valid subscription URL, false otherwise.
     */
    fun isValidSubUrl(value: String?): Boolean {
        try {
            if (value.isNullOrEmpty()) return false
            if (URLUtil.isHttpsUrl(value)) return true
            if (URLUtil.isHttpUrl(value) && value.contains(LOOPBACK)) return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Get the receiver flags based on the Android version.
     *
     * @return The receiver flags.
     */
    fun receiverFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.RECEIVER_EXPORTED
    } else {
        ContextCompat.RECEIVER_NOT_EXPORTED
    }

    /**
     * Check if the package is Xray.
     *
     * @return True if the package is Xray, false otherwise.
     */
    fun isXray(): Boolean = (ANG_PACKAGE.startsWith("com.v2ray.ang"))

}

