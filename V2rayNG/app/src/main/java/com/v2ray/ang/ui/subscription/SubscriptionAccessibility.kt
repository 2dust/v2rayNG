package com.v2ray.ang.ui.subscription

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun subscriptionAccessibilityName(remarks: String, url: String, unnamed: String): String {
    if (remarks.isNotBlank()) return remarks

    // Identify unnamed subscriptions without speaking credentials, paths, or access tokens.
    val host = url.toHttpUrlOrNull()?.host.orEmpty()
    return "$unnamed $host".trimEnd()
}
