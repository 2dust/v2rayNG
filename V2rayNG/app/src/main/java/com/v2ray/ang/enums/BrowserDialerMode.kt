package com.v2ray.ang.enums

enum class BrowserDialerMode(val value: String) {
    OKHTTP("OkHttp"),
    WEBVIEW("WebView");

    companion object {
        fun from(value: String?): BrowserDialerMode? = entries.find { it.value == value }
    }
}
