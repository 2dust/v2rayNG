package com.v2ray.ang.util

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.Utils.encode
import com.v2ray.ang.util.Utils.urlDecode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MalformedURLException
import java.net.Proxy
import java.net.URI
import java.net.URL

object HttpUtil {

    /**
     * Converts the domain part of a URL string to its IDN (Punycode, ASCII Compatible Encoding) format.
     *
     * For example, a URL like "https://例子.中国/path" will be converted to "https://xn--fsqu00a.xn--fiqs8s/path".
     *
     * @param str The URL string to convert (can contain non-ASCII characters in the domain).
     * @return The URL string with the domain part converted to ASCII-compatible (Punycode) format.
     */
    fun toIdnUrl(str: String): String {
        val url = URL(str)
        val host = url.host
        val asciiHost = IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED)
        if (host != asciiHost) {
            return str.replace(host, asciiHost)
        } else {
            return str
        }
    }

    /**
     * Converts a Unicode domain name to its IDN (Punycode, ASCII Compatible Encoding) format.
     * If the input is an IP address or already an ASCII domain, returns the original string.
     *
     * @param domain The domain string to convert (can include non-ASCII internationalized characters).
     * @return The domain in ASCII-compatible (Punycode) format, or the original string if input is an IP or already ASCII.
     */
    fun toIdnDomain(domain: String): String {
        // Return as is if it's a pure IP address (IPv4 or IPv6)
        if (Utils.isPureIpAddress(domain)) {
            return domain
        }

        // Return as is if already ASCII (English domain or already punycode)
        if (domain.all { it.code < 128 }) {
            return domain
        }

        // Otherwise, convert to ASCII using IDN
        return IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED)
    }

    /**
     * Resolves a hostname to an IP address, returns original input if it's already an IP
     *
     * @param host The hostname or IP address to resolve
     * @param ipv6Preferred Whether to prefer IPv6 addresses, defaults to false
     * @return The resolved IP address or the original input (if it's already an IP or resolution fails)
     */
    fun resolveHostToIP(host: String, ipv6Preferred: Boolean = false): List<String>? {
        try {
            // If it's already an IP address, return it as a list
            if (Utils.isPureIpAddress(host)) {
                return null
            }

            // Get all IP addresses
            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) {
                return null
            }

            // Sort addresses based on preference
            val sortedAddresses = if (ipv6Preferred) {
                addresses.sortedWith(compareByDescending { it is Inet6Address })
            } else {
                addresses.sortedWith(compareBy { it is Inet6Address })
            }

            val ipList = sortedAddresses.mapNotNull { it.hostAddress }

            Log.i(AppConfig.TAG, "Resolved IPs for $host: ${ipList.joinToString()}")

            return ipList
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to resolve host to IP", e)
            return null
        }
    }


    /**
     * Retrieves the content of a URL as a string.
     *
     * @param url The URL to fetch content from.
     * @param timeout The timeout value in milliseconds.
     * @param httpPort The HTTP port to use.
     * @return The content of the URL as a string.
     */
    fun getUrlContent(url: String, timeout: Int, httpPort: Int = 0): String? {
        val conn = createProxyConnection(url, httpPort, timeout, timeout) ?: return null
        try {
            return conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
        } finally {
            conn.disconnect()
        }
        return null
    }

    /**
     * Retrieves the content of a URL as a string with a custom User-Agent header.
     *
     * @param url The URL to fetch content from.
     * @param timeout The timeout value in milliseconds.
     * @param httpPort The HTTP port to use.
     * @return The content of the URL as a string.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getUrlContentWithUserAgent(url: String?, userAgent: String?,  timeout: Int = 15000, httpPort: Int = 0): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 3

        while (redirects++ < maxRedirects) {
            if (currentUrl == null) continue
            val conn = createProxyConnection(currentUrl, httpPort, timeout, timeout) ?: continue
            val finalUserAgent = if (userAgent.isNullOrBlank()) {
                "v2rayNG/${BuildConfig.VERSION_NAME}"
            } else {
                userAgent
            }
            conn.setRequestProperty("User-agent", finalUserAgent)
            conn.connect()

            val responseCode = conn.responseCode
            when (responseCode) {
                in 300..399 -> {
                    val location = resolveLocation(conn)
                    conn.disconnect()
                    if (location.isNullOrEmpty()) {
                        throw IOException("Redirect location not found")
                    }
                    currentUrl = location
                    continue
                }

                else -> try {
                    return conn.inputStream.use { it.bufferedReader().readText() }
                } finally {
                    conn.disconnect()
                }
            }
        }
        throw IOException("Too many redirects")
    }

    /**
     * Creates an HttpURLConnection object connected through a proxy.
     *
     * @param urlStr The target URL address.
     * @param port The port of the proxy server.
     * @param connectTimeout The connection timeout in milliseconds (default is 15000 ms).
     * @param readTimeout The read timeout in milliseconds (default is 15000 ms).
     * @param needStream Whether the connection needs to support streaming.
     * @return Returns a configured HttpURLConnection object, or null if it fails.
     */
    fun createProxyConnection(
        urlStr: String,
        port: Int,
        connectTimeout: Int = 15000,
        readTimeout: Int = 15000,
        needStream: Boolean = false
    ): HttpURLConnection? {

        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            // Create a connection
            conn = if (port == 0) {
                url.openConnection()
            } else {
                url.openConnection(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(LOOPBACK, port)
                    )
                )
            } as HttpURLConnection

            // Set connection and read timeouts
            conn.connectTimeout = connectTimeout
            conn.readTimeout = readTimeout
            if (!needStream) {
                // Set request headers
                conn.setRequestProperty("Connection", "close")
                // Disable automatic redirects
                conn.instanceFollowRedirects = false
                // Disable caching
                conn.useCaches = false
            }

            //Add Basic Authorization
            url.userInfo?.let {
                conn.setRequestProperty(
                    "Authorization",
                    "Basic ${encode(urlDecode(it))}"
                )
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to create proxy connection", e)
            // If an exception occurs, close the connection and return null
            conn?.disconnect()
            return null
        }
        return conn
    }

    // Returns absolute URL string location header sets
    fun resolveLocation(conn: HttpURLConnection): String? {
        val raw = conn.getHeaderField("Location")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // Try check url is relative or absolute
        return try {
            val locUri = URI(raw)
            val baseUri = conn.url.toURI()
            val resolved = if (locUri.isAbsolute) locUri else baseUri.resolve(locUri)
            resolved.toURL().toString()
        } catch (_: Exception) {
            // Fallback: url resolver, also should handles //host/...
            try {
                URL(raw).toString() // absolute with protocol
            } catch (_: MalformedURLException) {
                try {
                    URL(conn.url, raw).toString()
                } catch (_: MalformedURLException) {
                    null
                }
            }
        }
    }
}

