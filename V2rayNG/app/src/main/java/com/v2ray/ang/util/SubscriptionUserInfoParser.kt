package com.v2ray.ang.util

import com.v2ray.ang.dto.SubscriptionUserInfo

object SubscriptionUserInfoParser {
    fun parse(value: String?): SubscriptionUserInfo? {
        if (value.isNullOrBlank()) return null

        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = -1L
        var found = false

        value.split(';').forEach { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach

            val key = item.substring(0, separator).trim().lowercase()
            val number = item.substring(separator + 1).trim().toLongOrNull() ?: return@forEach
            if (number < 0) return@forEach

            when (key) {
                "upload" -> upload = number.also { found = true }
                "download" -> download = number.also { found = true }
                "total" -> total = number.also { found = true }
                "expire" -> expire = number.times(1000).also { found = true }
            }
        }

        return if (found) SubscriptionUserInfo(upload, download, total, expire) else null
    }
}
