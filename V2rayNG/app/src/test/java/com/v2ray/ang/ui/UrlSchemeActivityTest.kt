package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UrlSchemeActivityTest {
    @Test
    fun bothInstallActionsPreserveTheContainedUrlEscapes() {
        val url = "https://example.invalid/a%2Fb?token=x%2By+z%25"
        for (host in listOf("install-config", "install-sub")) {
            assertEquals(url, viewIntent(host, url).importConfigText())
        }
    }

    @Test
    fun outerNameOnlyFillsAnAbsentOrEmptyInnerFragment() {
        val url = "https://example.invalid/a%2Fb?token=x%2By"
        val name = "Name%20with%20%23%25+"
        assertEquals("$url#$name", viewIntent("install-sub", url, name).importConfigText())
        assertEquals("$url#$name", viewIntent("install-sub", "$url#", name).importConfigText())
        assertEquals("$url#Inner%20name", viewIntent("install-sub", "$url#Inner%20name", name).importConfigText())
        assertEquals(url, viewIntent("install-sub", url, "").importConfigText())
    }

    @Test
    fun sharedTextIsPassedThroughUnchanged() {
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(Intent.ACTION_SEND)
        whenever(intent.type).thenReturn("text/plain")
        for (text in listOf("https://example.invalid/a%2Fb?token=a+b%2Bc%25", "{\n\"remarks\":\"100% +\"\n}", "dGVzdA==", "")) {
            whenever(intent.getStringExtra(Intent.EXTRA_TEXT)).thenReturn(text)
            assertEquals(text, intent.importConfigText())
        }
        whenever(intent.getStringExtra(Intent.EXTRA_TEXT)).thenReturn(null)
        assertNull(intent.importConfigText())
        whenever(intent.type).thenReturn("image/png")
        assertNull(intent.importConfigText())
    }

    @Test
    fun absentUrlsAndUnrelatedActionsHaveNothingToImport() {
        for (url in listOf(null, "")) assertNull(viewIntent("install-sub", url).importConfigText())
        assertNull(mock<Intent>().importConfigText())
    }

    @Test
    fun unsupportedInstallHostsAndMissingDataFailValidation() {
        assertThrows(IllegalArgumentException::class.java) { viewIntent("unknown", "https://example.invalid").importConfigText() }
        val intent = mock<Intent>()
        whenever(intent.action).thenReturn(Intent.ACTION_VIEW)
        assertThrows(IllegalArgumentException::class.java) { intent.importConfigText() }
    }

    private fun viewIntent(host: String, decodedUrlParameter: String?, encodedFragment: String? = null): Intent {
        val uri = mock<Uri>()
        whenever(uri.host).thenReturn(host)
        whenever(uri.getQueryParameter("url")).thenReturn(decodedUrlParameter)
        whenever(uri.encodedFragment).thenReturn(encodedFragment)
        return mock<Intent>().also {
            whenever(it.action).thenReturn(Intent.ACTION_VIEW)
            whenever(it.data).thenReturn(uri)
        }
    }
}
