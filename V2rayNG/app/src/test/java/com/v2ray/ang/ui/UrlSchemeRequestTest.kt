package com.v2ray.ang.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class UrlSchemeRequestTest {
    private val profile = "trojan://secret%2Bvalue@127.0.0.1:9#Test+Profile"

    @Test fun sharedTextIsNotFormDecoded() {
        assertEquals(profile, UrlSchemeRequest.parse(Intent.ACTION_SEND, "text/plain", null, profile))
    }

    @Test fun wrapperIsDecodedExactlyOnceForBothHosts() {
        for (host in listOf("install-config", "install-sub")) {
            val link = "v2rayng://$host?url=" + URLEncoder.encode(profile, "UTF-8")
            assertEquals(profile, UrlSchemeRequest.parse(Intent.ACTION_VIEW, null, link, null))
        }
    }

    @Test fun wrapperFragmentIsRetainedOnlyWhenContentHasNone() {
        assertEquals(
            "https://example.com/sub#My%20Group",
            UrlSchemeRequest.parse(Intent.ACTION_VIEW, null,
                "v2rayng://install-sub?url=https%3A%2F%2Fexample.com%2Fsub#My%20Group", null),
        )
        assertEquals(
            profile,
            UrlSchemeRequest.parse(Intent.ACTION_VIEW, null,
                "v2rayng://install-config?url=" + URLEncoder.encode(profile, "UTF-8") + "#Other", null),
        )
    }

    @Test fun rejectsUnsupportedActionsAndTypes() {
        assertNull(UrlSchemeRequest.parse(Intent.ACTION_SEND, "image/png", null, profile))
        assertNull(UrlSchemeRequest.parse(Intent.ACTION_MAIN, "text/plain", null, profile))
        assertNull(UrlSchemeRequest.parse(null, "text/plain", null, profile))
    }

    @Test fun rejectsWrongSchemeHostAndMissingOrMalformedUrl() {
        for (data in listOf(
            null, "", "https://install-config?url=x", "v2rayng://other?url=x",
            "v2rayng://install-config", "v2rayng://install-config?url",
            "v2rayng://install-config?url=", "v2rayng://install-config?url=%20",
            "v2rayng://install-config?url=%zz", "v2rayng://install-config?other=x",
        )) {
            assertNull(data, UrlSchemeRequest.parse(Intent.ACTION_VIEW, null, data, null))
        }
    }

    @Test fun rejectsMissingOrBlankSharedText() {
        for (text in listOf(null, "", " \n ")) {
            assertNull(UrlSchemeRequest.parse(Intent.ACTION_SEND, "text/plain", null, text))
        }
    }
}
