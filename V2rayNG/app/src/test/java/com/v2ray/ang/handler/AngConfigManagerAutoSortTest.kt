package com.v2ray.ang.handler

import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AngConfigManagerAutoSortTest {

    @Test
    fun availableNodesSortByDelayAndFailuresRemainStableAtTheEnd() {
        val guids = listOf("medium", "fast", "failed-a", "slow", "unknown", "failed-b")
        val delays = mapOf(
            "medium" to 120L,
            "fast" to 25L,
            "failed-a" to -1L,
            "slow" to 450L,
            "unknown" to 0L,
            "failed-b" to -1L,
        )

        assertEquals(
            listOf("fast", "medium", "slow", "failed-a", "unknown", "failed-b"),
            AngConfigManager.sortGuidsByTestResults(guids) { delays.getValue(it) },
        )
    }

    @Test
    fun selectedPolicyGroupRemainsFirstAndEqualDelaysKeepOriginalOrder() {
        val guids = listOf("first", "group", "third", "second")
        val delays = mapOf(
            "first" to 40L,
            "group" to 0L,
            "third" to 40L,
            "second" to 40L,
        )

        assertEquals(
            listOf("group", "first", "third", "second"),
            AngConfigManager.sortGuidsByTestResults(
                guids = guids,
                pinnedGuid = "group",
                delayForGuid = { delays.getValue(it) },
            ),
        )
    }

    @Test
    fun allFailedNodesKeepTheirExistingRelativeOrder() {
        val guids = listOf("a", "b", "c")

        assertEquals(
            guids,
            AngConfigManager.sortGuidsByTestResults(guids) { -1L },
        )
    }

    @Test
    fun leastPingAlwaysUsesTheFirstMemberAsItsFallback() {
        val automatic = ProfileItem.create(EConfigType.POLICYGROUP).apply {
            policyGroupAutoSortEnabled = true
            policyGroupFallbackTag = "direct"
        }
        val manual = automatic.copy(
            policyGroupAutoSortEnabled = false,
        )

        assertEquals(
            "member-1",
            CoreConfigManager.resolvePolicyGroupFallbackTag(
                strategyType = BalancerStrategyType.LEAST_PING,
                profile = automatic,
                firstMemberTag = "member-1",
            ),
        )
        assertEquals(
            "member-1",
            CoreConfigManager.resolvePolicyGroupFallbackTag(
                strategyType = BalancerStrategyType.LEAST_PING,
                profile = manual,
                firstMemberTag = "member-1",
            ),
        )
    }
}
