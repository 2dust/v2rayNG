package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateCheckerManagerTest {
    @Test
    fun coreSuccessSkipsFallbacks() {
        val response = UpdateCheckerManager.fetchReleaseMetadata(
            coreFetch = { CoreFetchResult.Success("core") },
            directFetch = { error("Direct fetch must not run") },
            proxyFetch = { error("Proxy fetch must not run") },
        )

        assertEquals("core", response)
    }

    @Test
    fun acceptedCoreFailureDoesNotBypassTunnel() {
        assertThrows(IllegalStateException::class.java) {
            UpdateCheckerManager.fetchReleaseMetadata(
                coreFetch = { CoreFetchResult.Failed },
                directFetch = { error("Direct fetch must not run") },
                proxyFetch = { error("Proxy fetch must not run") },
            )
        }
    }

    @Test
    fun emptyCoreResponseDoesNotBypassTunnel() {
        assertThrows(IllegalStateException::class.java) {
            UpdateCheckerManager.fetchReleaseMetadata(
                coreFetch = { CoreFetchResult.Success("") },
                directFetch = { error("Direct fetch must not run") },
                proxyFetch = { error("Proxy fetch must not run") },
            )
        }
    }

    @Test
    fun unavailableCoreUsesDirectFallbackOnly() {
        val response = UpdateCheckerManager.fetchReleaseMetadata(
            coreFetch = { CoreFetchResult.Unavailable },
            directFetch = { "direct" },
            proxyFetch = { error("Disabled local proxy must not be tried") },
        )

        assertEquals("direct", response)
    }

    @Test
    fun otherModesPreserveDirectThenProxyFallback() {
        val response = UpdateCheckerManager.fetchReleaseMetadata(
            coreFetch = { CoreFetchResult.NotApplicable },
            directFetch = { "" },
            proxyFetch = { "proxy" },
        )

        assertEquals("proxy", response)
    }

    @Test
    fun otherModesSkipProxyAfterDirectSuccess() {
        val response = UpdateCheckerManager.fetchReleaseMetadata(
            coreFetch = { CoreFetchResult.NotApplicable },
            directFetch = { "direct" },
            proxyFetch = { error("Proxy fetch must not run after direct success") },
        )

        assertEquals("direct", response)
    }
}
