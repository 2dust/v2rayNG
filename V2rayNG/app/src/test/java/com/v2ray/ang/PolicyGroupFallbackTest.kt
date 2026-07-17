package com.v2ray.ang

import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.enums.BalancerStrategyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun resolve(
        strategyType: BalancerStrategyType,
        configuredFallbackTag: String?,
        testOutbounds: Boolean = true,
    ): String? = CoreConfigManager.resolvePolicyGroupFallbackTag(
        strategyType = strategyType,
        testOutbounds = testOutbounds,
        configuredFallbackTag = configuredFallbackTag,
        defaultFallbackTag = firstMember,
        availableOutboundTags = availableTags,
    )
}
