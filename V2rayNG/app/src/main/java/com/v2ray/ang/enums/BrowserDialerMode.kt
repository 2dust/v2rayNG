package com.v2ray.ang.enums

enum class BrowserDialerMode(val value: String) {
    OKHTTP("OkHttp"),
    WEBVIEW("WebView");

    companion object {
        fun from(value: String?): BrowserDialerMode? = entries.find { it.value == value }

        /**
         * Picks the mode for a profile. A non-blank per-node value always wins, including
         * "Disable", which resolves to null (off). A blank node value falls back to the
         * global value, but only for ws/xhttp, the only transports the browser dialer supports.
         */
        fun resolve(nodeValue: String?, globalValue: String?, network: String?): BrowserDialerMode? {
            if (!nodeValue.isNullOrBlank()) {
                return from(nodeValue)
            }
            if (network != NetworkType.WS.type && network != NetworkType.XHTTP.type) {
                return null
            }
            return from(globalValue)
        }
    }
}
