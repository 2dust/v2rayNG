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
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

object Utils {

    private val IPV4_REGEX =
        Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
    private val IPV6_REGEX = Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")

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
        return array.indexOf(value)
    }

    /**
     * Parse a string to an integer with a default value.
     *
     * @param str The string to parse.
     * @param default The default value if parsing fails.
     * @return The parsed integer, or the default value if parsing fails.
     */
    fun parseInt(str: String?, default: Int = 0): Int {
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
            Log.e(AppConfig.TAG, "Failed to get clipboard content", e)
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
            Log.e(AppConfig.TAG, "Failed to set clipboard content", e)
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
        if (text.isNullOrEmpty()) return null

        try {
            return Base64.decode(text, Base64.NO_WRAP).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to decode standard base64", e)
        }
        try {
            return Base64.decode(text, Base64.NO_WRAP.or(Base64.URL_SAFE)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to decode URL-safe base64", e)
        }
        return null
    }

    /**
     * Encode a string to base64.
     *
     * @param text The string to encode.
     * @param removePadding
     * @return The base64 encoded string, or an empty string if encoding fails.
     */
    fun encode(text: String, removePadding : Boolean = false): String {
        return try {
            var encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            if (removePadding) {
                encoded = encoded.trimEnd('=')
            }
            encoded
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to encode text to base64", e)
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
        if (value.isNullOrEmpty()) return false

        try {
            var addr = value.trim()
            if (addr.isEmpty()) return false

            //CIDR
            if (addr.contains("/")) {
                val arr = addr.split("/")
                if (arr.size == 2 && arr[1].toIntOrNull() != null && arr[1].toInt() > -1) {
                    addr = arr[0]
                }
            }

            // Handle IPv4-mapped IPv6 addresses
            if (addr.startsWith("::ffff:") && '.' in addr) {
                addr = addr.drop(7)
            } else if (addr.startsWith("[::ffff:") && '.' in addr) {
                addr = addr.drop(8).replace("]", "")
            }

            val octets = addr.split('.')
            if (octets.size == 4) {
                if (octets[3].contains(":")) {
                    addr = addr.substring(0, addr.indexOf(":"))
                }
                return isIpv4Address(addr)
            }

            return isIpv6Address(addr)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to validate IP address", e)
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
     * Check if a string is a valid domain name.
     *
     * A valid domain name must not be an IP address and must be a valid URL format.
     *
     * @param input The string to check.
     * @return True if the string is a valid domain name, false otherwise.
     */
    fun isDomainName(input: String?): Boolean {
        if (input.isNullOrEmpty()) return false

        // Must not be an IP address and must be a valid URL format
        return !isPureIpAddress(input) && isValidUrl(input)
    }

    /**
     * Check if a string is a valid IPv4 address.
     *
     * @param value The string to check.
     * @return True if the string is a valid IPv4 address, false otherwise.
     */
    private fun isIpv4Address(value: String): Boolean {
        return IPV4_REGEX.matches(value)
    }

    /**
     * Check if a string is a valid IPv6 address.
     *
     * @param value The string to check.
     * @return True if the string is a valid IPv6 address, false otherwise.
     */
    private fun isIpv6Address(value: String): Boolean {
        var addr = value
        if (addr.startsWith("[") && addr.endsWith("]")) {
            addr = addr.drop(1).dropLast(1)
        }
        return IPV6_REGEX.matches(addr)
    }

    /**
     * Check if a string is a CoreDNS address.
     *
     * @param s The string to check.
     * @return True if the string is a CoreDNS address, false otherwise.
     */
    fun isCoreDNSAddress(s: String): Boolean {
        return s.startsWith("https") ||
                s.startsWith("tcp") ||
                s.startsWith("quic") ||
                s == "localhost"
    }

    /**
     * Check if a string is a valid URL.
     *
     * @param value The string to check.
     * @return True if the string is a valid URL, false otherwise.
     */
    fun isValidUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false

        return try {
            Patterns.WEB_URL.matcher(value).matches() ||
                    Patterns.DOMAIN_NAME.matcher(value).matches() ||
                    URLUtil.isValidUrl(value)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to validate URL", e)
            false
        }
    }

    /**
     * Open a URI in a browser.
     *
     * @param context The context to use.
     * @param uriString The URI string to open.
     */
    fun openUri(context: Context, uriString: String) {
        try {
            val uri = uriString.toUri()
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to open URI", e)
        }
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
            Log.e(AppConfig.TAG, "Failed to generate UUID", e)
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
            Log.e(AppConfig.TAG, "Failed to decode URL", e)
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
            Log.e(AppConfig.TAG, "Failed to encode URL", e)
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
        if (context == null) return ""

        return try {
            context.assets.open(fileName).use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to read asset file: $fileName", e)
            ""
        }
    }

    /**
     * Get the path to the user asset directory.
     *
     * @param context The context to use.
     * @return The path to the user asset directory.
     */
    fun userAssetPath(context: Context?): String {
        if (context == null) return ""

        return try {
            context.getExternalFilesDir(AppConfig.DIR_ASSETS)?.absolutePath
                ?: context.getDir(AppConfig.DIR_ASSETS, 0).absolutePath
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get user asset path", e)
            ""
        }
    }

    /**
     * Get the path to the backup directory.
     *
     * @param context The context to use.
     * @return The path to the backup directory.
     */
    fun backupPath(context: Context?): String {
        if (context == null) return ""

        return try {
            context.getExternalFilesDir(AppConfig.DIR_BACKUPS)?.absolutePath
                ?: context.getDir(AppConfig.DIR_BACKUPS, 0).absolutePath
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get backup path", e)
            ""
        }
    }

    /**
     * Get the device ID for XUDP base key.
     *
     * @return The device ID for XUDP base key.
     */
    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to generate device ID", e)
            ""
        }
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
        if (address.isNullOrEmpty()) return ""

        return if (isIpv6Address(address) && !address.contains('[') && !address.contains(']')) {
            "[$address]"
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
        return str.replace(" ", "%20")
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
        if (value.isNullOrEmpty()) return false

        try {
            if (URLUtil.isHttpsUrl(value)) return true
            if (URLUtil.isHttpUrl(value)) {
                if (value.contains(LOOPBACK)) return true

                //Check private ip address
                val uri = URI(fixIllegalUrl(value))
                if (isIpAddress(uri.host)) {
                    AppConfig.PRIVATE_IP_LIST.forEach {
                        if (isIpInCidr(uri.host, it)) return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to validate subscription URL", e)
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
    fun isXray(): Boolean = BuildConfig.APPLICATION_ID.startsWith("com.v2ray.ang")

    /**
     * Check if it is the Google Play version.
     *
     * @return True if the package is Google Play, false otherwise.
     */
    fun isGoogleFlavor(): Boolean = BuildConfig.FLAVOR == "playstore"

    /**
     * Converts an InetAddress to its long representation
     *
     * @param ip The InetAddress to convert
     * @return The long representation of the IP address
     */
    private fun inetAddressToLong(ip: InetAddress): Long {
        val bytes = ip.address
        var result: Long = 0
        for (i in bytes.indices) {
            result = result shl 8 or (bytes[i].toInt() and 0xff).toLong()
        }
        return result
    }

    /**
     * Check if an IP address is within a CIDR range
     *
     * @param ip The IP address to check
     * @param cidr The CIDR notation range (e.g., "192.168.1.0/24")
     * @return True if the IP is within the CIDR range, false otherwise
     */
    fun isIpInCidr(ip: String, cidr: String): Boolean {
        try {
            if (!isIpAddress(ip)) return false

            // Parse CIDR (e.g., "192.168.1.0/24")
            val (cidrIp, prefixLen) = cidr.split("/")
            val prefixLength = prefixLen.toInt()

            // Convert IP and CIDR's IP portion to Long
            val ipLong = inetAddressToLong(InetAddress.getByName(ip))
            val cidrIpLong = inetAddressToLong(InetAddress.getByName(cidrIp))

            // Calculate subnet mask (e.g., /24 → 0xFFFFFF00)
            val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength))

            // Check if they're in the same subnet
            return (ipLong and mask) == (cidrIpLong and mask)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to check if IP is in CIDR", e)
            return false
        }
    }
}

