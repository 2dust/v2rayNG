package com.v2ray.ang.ui.routing

import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.CoreFetchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource

class RoutingRulesetImportTest {
    private val url = "https://example.invalid/rules.json"
    private val request = UrlContentRequest(url = url, timeout = 10_000)
    private val rules = """[{"remarks":"Example"}]"""

    @Test
    fun inlineRulesDoNotFetch() {
        assertEquals(
            rules,
            resolveRulesetImportContent(
                "  $rules\n",
                isNetworkUrl = { false },
                coreFetch = { error("Inline rules must not use the core") },
                directFetch = { error("Inline rules must not use direct HTTP") },
                proxyFetch = { error("Inline rules must not use the local proxy") },
            ),
        )
    }

    @Test
    fun coreResponseDoesNotTriggerHttpFallback() {
        for (body in listOf(rules, "")) {
            assertEquals(
                body,
                resolveRulesetImportContent(
                    " $url ",
                    isNetworkUrl = { it == url },
                    coreFetch = {
                        assertEquals(request, it)
                        CoreFetchResult.Success(body)
                    },
                    directFetch = { error("Core response must not trigger direct HTTP") },
                    proxyFetch = { error("Core response must not trigger the local proxy") },
                ),
            )
        }
    }

    @Test
    fun unavailableOrFailedCoreDoesNotBypassTunnel() {
        for (failure in listOf(CoreFetchResult.Unavailable, CoreFetchResult.Failed)) {
            assertNull(
                resolveRulesetImportContent(
                    url,
                    isNetworkUrl = { true },
                    coreFetch = { failure },
                    directFetch = { error("$failure must not trigger direct HTTP") },
                    proxyFetch = { error("$failure must not trigger the local proxy") },
                ),
            )
        }
    }

    @Test
    fun otherModesKeepDirectResponse() {
        assertEquals(
            rules,
            resolveRulesetImportContent(
                url,
                isNetworkUrl = { true },
                coreFetch = { CoreFetchResult.NotApplicable },
                directFetch = { rules },
                proxyFetch = { error("Successful direct HTTP must not trigger the local proxy") },
            ),
        )
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun otherModesTryLocalProxyWhenDirectResponseIsMissing(directResponse: String?) {
        val attempts = mutableListOf<String>()
        val content = resolveRulesetImportContent(
            url,
            isNetworkUrl = { true },
            coreFetch = { CoreFetchResult.NotApplicable },
            directFetch = {
                assertEquals(request, it)
                attempts.add("direct")
                directResponse
            },
            proxyFetch = {
                assertEquals(request, it)
                attempts.add("proxy")
                rules
            },
        )

        assertEquals(listOf("direct", "proxy"), attempts)
        assertEquals(rules, content)
    }
}
