package com.v2ray.ang.enums

enum class BrowserDialerMode(val value: String) {
    OKHTTP("OkHttp"),
    WEBVIEW("WebView");

    companion object {
        fun from(value: String?): BrowserDialerMode? = entries.find { it.value == value }

        /**
         * Picks the mode for a profile. A non-blank per-node value always wins, including
         * "Disable", which resolves to null (off). A blank node value falls back to the
         * global value, unless the node's network is known and is not ws/xhttp, the only
         * transports the browser dialer supports. An unknown (null) network, as for custom
         * configs, uses the global value and leaves the decision to the core.
         */
        fun resolve(nodeValue: String?, globalValue: String?, network: String?): BrowserDialerMode? {
            if (!nodeValue.isNullOrBlank()) {
                return from(nodeValue)
            }
            // A null network means unknown (e.g. custom configs): let the core decide per outbound.
            if (network != null && network != NetworkType.WS.type && network != NetworkType.XHTTP.type) {
                return null
            }
            return from(globalValue)
        }
    }
}
