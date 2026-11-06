package com.v2ray.ang.enums

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserDialerModeTest {

    @Test
    fun nodeValueWinsOverGlobal() {
        assertEquals(BrowserDialerMode.WEBVIEW, BrowserDialerMode.resolve("WebView", "OkHttp", "ws"))
    }

    @Test
    fun nodeDisableOverridesGlobal() {
        assertNull(BrowserDialerMode.resolve("Disable", "OkHttp", "ws"))
    }

    @Test
    fun blankNodeFallsBackToGlobalOnWsAndXhttp() {
        assertEquals(BrowserDialerMode.OKHTTP, BrowserDialerMode.resolve(null, "OkHttp", "ws"))
        assertEquals(BrowserDialerMode.WEBVIEW, BrowserDialerMode.resolve("", "WebView", "xhttp"))
    }

    @Test
    fun globalIgnoredForUnsupportedNetworks() {
        assertNull(BrowserDialerMode.resolve(null, "OkHttp", "tcp"))
        assertNull(BrowserDialerMode.resolve(null, "OkHttp", null))
    }

    @Test
    fun globalDisableOrUnsetMeansOff() {
        assertNull(BrowserDialerMode.resolve(null, "Disable", "ws"))
        assertNull(BrowserDialerMode.resolve(null, null, "ws"))
    }
}
