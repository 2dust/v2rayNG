package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoSortPolicyTest {

    @Test
    fun intervalsDefaultAndFallBackToTenMinutesWhenUnsupported() {
        assertEquals(10, AutoSortPolicy.normalizedInterval(null))
        assertEquals(10, AutoSortPolicy.normalizedInterval(0))
        assertEquals(10, AutoSortPolicy.normalizedInterval(2))
        AutoSortPolicy.SUPPORTED_INTERVAL_MINUTES.forEach { interval ->
            assertEquals(interval, AutoSortPolicy.normalizedInterval(interval))
            assertEquals(interval * 60_000L, AutoSortPolicy.intervalMillis(interval))
        }
    }

    @Test
    fun onlyTheSelectedRunningLeastPingGroupWithAConcreteSubscriptionCanRun() {
        val config = policyGroup()

        assertTrue(AutoSortPolicy.shouldRun("group", "group", config, isRunning = true))
        assertFalse(AutoSortPolicy.shouldRun(null, "group", config, isRunning = true))
        assertFalse(AutoSortPolicy.shouldRun("other", "group", config, isRunning = true))
        assertFalse(AutoSortPolicy.shouldRun("group", "other", config, isRunning = true))
        assertFalse(AutoSortPolicy.shouldRun("group", "group", config, isRunning = false))
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(policyGroupAutoSortEnabled = false),
                isRunning = true,
            )
        )
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(policyGroupAutoSortEnabled = null),
                isRunning = true,
            )
        )
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(
                    policyGroupType = BalancerStrategyType.ROUND_ROBIN.policyGroupTypeValue
                ),
                isRunning = true,
            )
        )
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(policyGroupSubscriptionId = ""),
                isRunning = true,
            )
        )
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(policyGroupTestOutbounds = false),
                isRunning = true,
            )
        )
        assertFalse(
            AutoSortPolicy.shouldRun(
                "group",
                "group",
                config.copy(policyGroupFallbackTag = "direct"),
                isRunning = true,
            )
        )
    }

    @Test
    fun legacyTestOutboundsNullStillUsesTheLeastPingDefault() {
        val config = policyGroup().copy(policyGroupTestOutbounds = null)

        assertTrue(AutoSortPolicy.shouldRun("group", "group", config, isRunning = true))
    }

    @Test
    fun groupAndChainProfilesAreExcludedButSupportedSingleProfilesRemainTestable() {
        assertFalse(AutoSortPolicy.isTestableNode(ProfileItem.create(EConfigType.POLICYGROUP)))
        assertFalse(AutoSortPolicy.isTestableNode(ProfileItem.create(EConfigType.PROXYCHAIN)))
        assertTrue(AutoSortPolicy.isTestableNode(ProfileItem.create(EConfigType.VLESS)))
        assertTrue(AutoSortPolicy.isTestableNode(ProfileItem.create(EConfigType.CUSTOM)))
    }

    @Test
    fun automaticModeRejectsNonProxyFallbacks() {
        assertTrue(AutoSortPolicy.isSafeFallback(null))
        assertTrue(AutoSortPolicy.isSafeFallback("backup-node"))
        assertFalse(AutoSortPolicy.isSafeFallback("direct"))
        assertFalse(AutoSortPolicy.isSafeFallback("block"))
        assertFalse(AutoSortPolicy.isSafeFallback(" direct "))
        assertFalse(AutoSortPolicy.isSafeFallback("DIRECT"))
    }

    @Test
    fun profileFieldsRoundTripAndLegacyJsonDefaultsRemainCompatible() {
        val legacy = JsonUtil.fromJson(
            """{"configType":"POLICYGROUP","remarks":"legacy"}""",
            ProfileItem::class.java,
        )
        assertEquals(EConfigType.POLICYGROUP, legacy?.configType)
        assertNull(legacy?.policyGroupAutoSortEnabled)
        assertNull(legacy?.policyGroupSortIntervalMinutes)

        val configured = policyGroup().copy(
            policyGroupAutoSortEnabled = true,
            policyGroupSortIntervalMinutes = 5,
        )
        val restored = JsonUtil.fromJson(
            JsonUtil.toJson(configured),
            ProfileItem::class.java,
        )
        assertEquals(true, restored?.policyGroupAutoSortEnabled)
        assertEquals(5, restored?.policyGroupSortIntervalMinutes)
    }

    private fun policyGroup() = ProfileItem.create(EConfigType.POLICYGROUP).apply {
        remarks = "Auto"
        policyGroupType = BalancerStrategyType.LEAST_PING.policyGroupType
        policyGroupSubscriptionId = "subscription"
        policyGroupTestOutbounds = true
        policyGroupAutoSortEnabled = true
        policyGroupSortIntervalMinutes = 10
    }
}
