package com.v2ray.ang.ui

import android.content.Intent
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

internal object UrlSchemeRequest {
    fun parse(action: String?, type: String?, data: String?, sharedText: String?): String? {
        if (action == Intent.ACTION_SEND) {
            return sharedText?.takeIf { type == "text/plain" && it.isNotBlank() }
        }
        if (action != Intent.ACTION_VIEW || data == null) return null

        return try {
            val uri = URI(data)
            if (uri.scheme != "v2rayng" || uri.host !in setOf("install-config", "install-sub")) {
                return null
            }
            val urlParameter = uri.rawQuery?.split('&')?.firstOrNull {
                it.substringBefore('=') == "url"
            } ?: return null
            // Decode the wrapper once; escapes and '+' inside the config belong to its format.
            val content = URLDecoder.decode(urlParameter.substringAfter('=', ""), "UTF-8")
            if (content.isBlank()) return null
            if ('#' !in content && !uri.rawFragment.isNullOrEmpty()) {
                "$content#${uri.rawFragment}"
            } else {
                content
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: URISyntaxException) {
            null
        }
    }
}
