package com.v2ray.ang.handler

import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import okhttp3.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionDownloadTest {
    private val request = UrlContentRequest(
        url = "https://example.com/subscription",
        httpPort = 10809,
        proxyUsername = "user",
        proxyPassword = "password",
    )

    @Test
    fun coreResponsePreservesSubscriptionMetadataWithoutHttpFallback() {
        var usageInfo: SubscriptionUsageInfo? = null
        val content = AngConfigManager.downloadSubscription(
            request,
            onResponseHeaders = { headers -> usageInfo = parseUsageInfo(headers) },
            coreFetch = { capture ->
                capture(CoreDownloadManager.parseResponseHeaders(
                    """{
                        "subscription-userinfo":["upload=1; download=2; total=100; expire=2000000000"],
                        "Profile-Title":["account"],
                        "Support-Url":["https://example.com/support"],
                        "Profile-Web-Page-Url":["https://example.com/account"]
                    }""",
                ))
                CoreFetchResult.Success("core body")
            },
            httpFetch = { _, _ -> error("HTTP fallback must not run") },
        )

        assertEquals("core body", content)
        assertEquals(SubscriptionUsageInfo(
            upload = 1, download = 2, total = 100, expire = 2000000000,
            title = "account", supportUrl = "https://example.com/support",
            webPageUrl = "https://example.com/account",
        ), usageInfo)
    }

    @Test
    fun coreFailureUnavailableAndEmptyResponsesDoNotBypassTunnel() {
        for (result in listOf(CoreFetchResult.Failed, CoreFetchResult.Unavailable, CoreFetchResult.Success(""))) {
            val content = AngConfigManager.downloadSubscription(
                request,
                onResponseHeaders = { error("Failed response must not publish headers") },
                coreFetch = { result },
                httpFetch = { _, _ -> error("HTTP fallback must not run") },
            )
            assertEquals("", content)
        }
    }

    @Test
    fun otherModesPreserveProxyThenDirectFallbackAndCaptureFinalHeaders() {
        val ports = mutableListOf<Int>()
        var usageInfo: SubscriptionUsageInfo? = null
        val content = AngConfigManager.downloadSubscription(
            request,
            onResponseHeaders = { headers -> usageInfo = parseUsageInfo(headers) },
            coreFetch = { CoreFetchResult.NotApplicable },
            httpFetch = { candidate, capture ->
                ports += candidate.httpPort
                if (candidate.httpPort != 0) {
                    capture(Headers.headersOf("Profile-Title", "empty response"))
                    ""
                } else {
                    assertNull(candidate.proxyUsername)
                    assertNull(candidate.proxyPassword)
                    capture(Headers.headersOf("Profile-Title", "final response"))
                    "direct body"
                }
            },
        )
        assertEquals(listOf(10809, 0), ports)
        assertEquals("direct body", content)
        assertEquals("final response", usageInfo?.title)
    }

    @Test
    fun otherModesStopAfterProxySuccess() {
        val ports = mutableListOf<Int>()
        val content = AngConfigManager.downloadSubscription(
            request,
            onResponseHeaders = {},
            coreFetch = { CoreFetchResult.NotApplicable },
            httpFetch = { candidate, _ -> ports += candidate.httpPort; "proxy body" },
        )
        assertEquals("proxy body", content)
        assertEquals(listOf(10809), ports)
    }

    @Test
    fun directModeMakesOneRequestAndEmptyContentStaysEmpty() {
        var calls = 0
        val content = AngConfigManager.downloadSubscription(
            request.copy(httpPort = 0),
            onResponseHeaders = {},
            coreFetch = { CoreFetchResult.NotApplicable },
            httpFetch = { _, _ -> calls++; "" },
        )
        assertEquals("", content)
        assertEquals(1, calls)
    }

    @Test
    fun responseWithoutMetadataClearsPreviousUsageInfo() {
        var usageInfo: SubscriptionUsageInfo? = SubscriptionUsageInfo(total = 100)
        val content = AngConfigManager.downloadSubscription(
            request,
            onResponseHeaders = { headers -> usageInfo = parseUsageInfo(headers) },
            coreFetch = { capture ->
                capture(CoreDownloadManager.parseResponseHeaders("{}"))
                CoreFetchResult.Success("new body")
            },
            httpFetch = { _, _ -> error("HTTP fallback must not run") },
        )
        assertEquals("new body", content)
        assertNull(usageInfo)
    }

    @Test
    fun nativeHeaderDecodingPreservesDuplicatesUnicodeAndAbsentHeaders() {
        val headers = CoreDownloadManager.parseResponseHeaders(
            """{"X-Repeated":["first","second"],"Profile-Title":["Abunəlik"]}""",
        )
        assertEquals(listOf("first", "second"), headers.values("x-repeated"))
        assertEquals("Abunəlik", headers["profile-title"])
        assertEquals(0, CoreDownloadManager.parseResponseHeaders(null).size)
    }

    private fun parseUsageInfo(headers: Headers): SubscriptionUsageInfo? = SubscriptionInfoParser.parse(
        userInfo = headers["Subscription-Userinfo"],
        profileTitle = headers["Profile-Title"],
        supportUrl = headers["Support-Url"],
        webPageUrl = headers["Profile-Web-Page-Url"],
    )
}
