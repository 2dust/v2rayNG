package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.util.Utils
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Parses the account information subscription panels put in the response headers of the
 * subscription URL (the de-facto standard shared by 3x-ui, Marzban, Remnawave, Clash, v2rayN,
 * Hiddify and others):
 *
 * - `Subscription-Userinfo: upload=<bytes>; download=<bytes>; total=<bytes>; expire=<unix seconds>`
 * - `Profile-Title: <text>` or `base64:<base64 text>`
 * - `Support-Url: <link>` (usually a Telegram link)
 * - `Profile-Web-Page-Url: <link>` (the panel's subscription information page)
 *
 * Pure functions only, so the logic is covered by plain JVM tests.
 */
object SubscriptionInfoParser {
    private const val BASE64_PREFIX = "base64:"
    private const val MAX_TITLE_LENGTH = 100
    private const val MAX_URL_LENGTH = 2048

    /** Values at or above this are epoch milliseconds, not seconds (year 5138 in seconds). */
    private const val MILLIS_THRESHOLD = 100_000_000_000L

    private val SAFE_SCHEMES = setOf("http", "https", "tg")
    private val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "telegram.dog", "telegram.org")

    /**
     * Builds usage info from raw header values.
     *
     * @return null when the panel sent none of the supported headers, so the UI shows nothing.
     */
    fun parse(
        userInfo: String?,
        profileTitle: String?,
        supportUrl: String?,
        webPageUrl: String?,
        decodeBase64: (String) -> String = Utils::decode,
    ): SubscriptionUsageInfo? {
        val fields = parseUserInfoFields(userInfo)
        val title = decodeTitle(profileTitle, decodeBase64)
        val support = sanitizeLink(supportUrl)
        val webPage = sanitizeLink(webPageUrl)

        if (fields == null && title == null && support == null && webPage == null) return null

        return SubscriptionUsageInfo(
            upload = fields?.get("upload") ?: 0,
            download = fields?.get("download") ?: 0,
            total = fields?.get("total") ?: 0,
            expire = normalizeExpire(fields?.get("expire") ?: 0),
            title = title,
            supportUrl = support,
            webPageUrl = webPage,
        )
    }

    /**
     * The subscription URL itself answers with the panel's information page when it is opened in
     * a browser (3x-ui, Marzban), so it is the fallback target of the "info page" button.
     */
    fun withWebPageFallback(info: SubscriptionUsageInfo, subscriptionUrl: String): SubscriptionUsageInfo {
        if (info.webPageUrl != null) return info
        return info.copy(webPageUrl = sanitizeLink(subscriptionUrl))
    }

    /** Returns [raw] when it is an http(s) or tg:// link that is safe to hand to ACTION_VIEW. */
    fun sanitizeLink(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > MAX_URL_LENGTH) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme !in SAFE_SCHEMES) return null
        if (scheme != "tg" && uri.host.isNullOrEmpty()) return null
        return value
    }

    /** True for links that open in the Telegram app. */
    fun isTelegramLink(url: String?): Boolean {
        val uri = runCatching { URI(url.orEmpty().trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme == "tg") return true
        return uri.host?.lowercase(Locale.ROOT) in TELEGRAM_HOSTS
    }

    private fun parseUserInfoFields(header: String?): Map<String, Long>? {
        if (header.isNullOrBlank()) return null
        val result = HashMap<String, Long>()
        for (part in header.split(';')) {
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val key = part.substring(0, separator).trim().lowercase(Locale.ROOT)
            val raw = part.substring(separator + 1).trim()
            val number = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
            if (number != null) result[key] = number.coerceAtLeast(0)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun normalizeExpire(value: Long): Long =
        if (value >= MILLIS_THRESHOLD) value / 1000 else value

    private fun decodeTitle(raw: String?, decodeBase64: (String) -> String): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val decoded = when {
            value.startsWith(BASE64_PREFIX, ignoreCase = true) ->
                decodeBase64(value.substring(BASE64_PREFIX.length).trim())

            value.contains('%') ->
                runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

            else -> value
        }
        return decoded.trim().take(MAX_TITLE_LENGTH).ifEmpty { null }
    }
}
