package com.v2ray.ang.ui.urlscheme

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSchemeSourceTest {

    @Test
    fun mapsKnownHosts() {
        assertEquals(UrlSchemeSource.INSTALL_CONFIG, urlSchemeSourceOf("install-config"))
        assertEquals(UrlSchemeSource.INSTALL_SUB, urlSchemeSourceOf("install-sub"))
    }

    @Test
    fun unknownAndOpaqueHostsAreUnsupported() {
        assertEquals(UrlSchemeSource.UNSUPPORTED, urlSchemeSourceOf("install-something"))
        assertEquals(UrlSchemeSource.UNSUPPORTED, urlSchemeSourceOf(null))
        assertEquals(UrlSchemeSource.UNSUPPORTED, urlSchemeSourceOf(""))
    }
}
