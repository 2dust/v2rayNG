package com.v2ray.ang.util

import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.Utils.encode
import com.v2ray.ang.util.Utils.urlDecode
import java.io.IOException
import java.net.*
import java.util.*

object HttpUtil {

    fun idnToASCII(str: String): String {
        val url = URL(str)
        return URL(url.protocol, IDN.toASCII(url.host, IDN.ALLOW_UNASSIGNED), url.port, url.file).toExternalForm()
    }

    fun getUrlContent(url: String, timeout: Int): String {
        var result: String = ""
        val conn = createProxyConnection(url, 0, timeout, timeout) ?: return result
        try {
            result = conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
        } finally {
            conn.disconnect()
        }
        return result
    }

    @Throws(IOException::class)
    fun getUrlContentWithUserAgent(url: String?, timeout: Int = 30000, httpPort: Int = 0): String {
        var currentUrl = url
        var redirects = 0
        val maxRedirects = 3

        while (redirects++ < maxRedirects) {
            if (currentUrl == null) continue
            val conn = createProxyConnection(currentUrl, httpPort, timeout, timeout) ?: continue
            conn.setRequestProperty("User-agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
            conn.connect()

            val responseCode = conn.responseCode
            when (responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
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
     * @param ip The IP address of the proxy server.
     * @param port The port of the proxy server.
     * @param connectTimeout The connection timeout in milliseconds (default is 30000 ms).
     * @param readTimeout The read timeout in milliseconds (default is 30000 ms).
     * @param needStream
     * @return Returns a configured HttpURLConnection object, or null if it fails.
     */
    fun createProxyConnection(
        urlStr: String,
        port: Int,
        connectTimeout: Int = 30000,
        readTimeout: Int = 30000,
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
            e.printStackTrace()
            // If an exception occurs, close the connection and return null
            conn?.disconnect()
            return null
        }
        return conn
    }
}

