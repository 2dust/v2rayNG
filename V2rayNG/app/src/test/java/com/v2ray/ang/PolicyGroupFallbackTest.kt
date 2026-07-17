package com.v2ray.ang

import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.ui.buildPolicyGroupFallbackSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGroupFallbackTest {

    private val firstMember = "proxy-proxy-1-first"
    private val selectedFallback = "direct"
    private val availableTags = setOf(firstMember, selectedFallback)

    @Test
    fun randomUsesFirstMemberWhenFallbackIsEmpty() {
        assertEquals(
            firstMember,
            resolve(BalancerStrategyType.RANDOM, configuredFallbackTag = null)
        )
        assertEquals(
            firstMember,
            resolve(BalancerStrategyType.RANDOM, configuredFallbackTag = " ")
        )
    }

    @Test
    fun randomAndRoundRobinTestOutboundsByDefault() {
        assertEquals(
            firstMember,
            resolve(
                BalancerStrategyType.RANDOM,
                configuredFallbackTag = null,
                testOutbounds = null
            )
        )
        assertEquals(
            firstMember,
            resolve(
                BalancerStrategyType.ROUND_ROBIN,
                configuredFallbackTag = null,
                testOutbounds = null
            )
        )
    }

    @Test
    fun roundRobinUsesSelectedFallbackWhenAvailable() {
        assertEquals(
            selectedFallback,
            resolve(
                BalancerStrategyType.ROUND_ROBIN,
                configuredFallbackTag = selectedFallback
            )
        )
    }

    @Test
    fun missingSelectedFallbackUsesFirstMember() {
        assertEquals(
            firstMember,
            resolve(BalancerStrategyType.RANDOM, configuredFallbackTag = "deleted-profile")
        )
    }

    @Test
    fun disabledTestingDoesNotSetFallback() {
        assertNull(
            resolve(
                BalancerStrategyType.RANDOM,
                configuredFallbackTag = selectedFallback,
                testOutbounds = false
            )
        )
    }

    @Test
    fun leastPingAndLeastLoadDoNotSetFallback() {
        assertNull(
            resolve(BalancerStrategyType.LEAST_PING, configuredFallbackTag = selectedFallback)
        )
        assertNull(
            resolve(BalancerStrategyType.LEAST_LOAD, configuredFallbackTag = selectedFallback)
        )
    }

    @Test
    fun fallbackSuggestionsExcludeProxy() {
        val suggestions = buildPolicyGroupFallbackSuggestions(
            listOf("profile-a", AppConfig.TAG_PROXY, "profile-a")
        )

        assertFalse(AppConfig.TAG_PROXY in suggestions)
        assertTrue(AppConfig.TAG_DIRECT in suggestions)
        assertTrue("profile-a" in suggestions)
        assertEquals(1, suggestions.count { it == "profile-a" })
    }

    private fun resolve(
        strategyType: BalancerStrategyType,
        configuredFallbackTag: String?,
        testOutbounds: Boolean? = true,
    ): String? = CoreConfigManager.resolvePolicyGroupFallbackTag(
        strategyType = strategyType,
        testOutbounds = testOutbounds,
        configuredFallbackTag = configuredFallbackTag,
        defaultFallbackTag = firstMember,
        availableOutboundTags = availableTags,
    )
}
